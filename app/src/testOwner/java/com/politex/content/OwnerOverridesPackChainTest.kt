package com.politex.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.data.repository.Teacher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OwnerOverridesPackChainTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val base =
        Teacher(
            name = "Иванова Анна Петровна",
            position = "Преподаватель",
            subjects = "Математика",
            phone = "111-111",
            experience = "10 лет",
            category = "первая",
            photo = "001_Ivanova.jpg",
        )

    private val key = OwnerOverridesStore.keyOf(base.name)

    private var packVersion = 100

    private fun activatePack(
        json: String?,
        packPhoto: String? = null,
    ): File {
        packVersion += 1
        val dir = ContentPackStore.packDir(context, packVersion)
        dir.mkdirs()
        if (json != null) File(dir, OwnerOverridesStore.PACK_FILE).writeText(json, Charsets.UTF_8)
        if (packPhoto != null) {
            val photos = File(dir, "teachers_photos").apply { mkdirs() }
            File(photos, packPhoto).writeBytes(byteArrayOf(9, 8, 7))
        }
        ContentPackStore.activate(context, packVersion, "", 0)
        OwnerOverridesStore.invalidateCache()
        return dir
    }

    @Before
    fun setUp() {
        ContentPackStore.reset(context)
        OwnerOverridesStore.clearAll(context)
    }

    @After
    fun tearDown() {
        ContentPackStore.reset(context)
        OwnerOverridesStore.clearAll(context)
    }

    @Test
    fun `пакетный overrides применяется частично и не трогает остальные поля`() {
        activatePack(
            """{"format":1,"teachers":{"$key":{"fio":"Иванова А. П.","phone":"222-222"}}}""",
        )
        val merged = OwnerOverridesStore.applyTeacherOverrides(context, listOf(base)).single()
        assertEquals("Иванова А. П.", merged.name)
        assertEquals("222-222", merged.phone)
        assertEquals("Преподаватель", merged.position)
        assertEquals("Математика", merged.subjects)
        assertEquals("10 лет", merged.experience)
        assertEquals("первая", merged.category)
        assertEquals(base.name, merged.matchName)
    }

    @Test
    fun `локальная правка владельца перекрывает пакетную`() {
        activatePack("""{"format":1,"teachers":{"$key":{"fio":"Пакетная Анна","phone":"222-222"}}}""")
        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = key,
            fio = "Локальная Анна",
            position = "Локальная должность",
            phone = "333-333",
        )
        val merged = OwnerOverridesStore.applyTeacherOverrides(context, listOf(base)).single()
        assertEquals("Локальная Анна", merged.name)
        assertEquals("333-333", merged.phone)
        assertEquals("Локальная должность", merged.position)
        assertEquals(base.name, merged.matchName)
    }

    @Test
    fun `без пакетного файла работает старое поведение`() {
        activatePack(json = null)
        val untouched = OwnerOverridesStore.applyTeacherOverrides(context, listOf(base)).single()
        assertEquals(base, untouched)
        assertNull(OwnerOverridesStore.photoFileFor(context, base.name))
    }

    @Test
    fun `битый пакетный json игнорируется слоем overrides`() {
        activatePack("это не json")
        val untouched = OwnerOverridesStore.applyTeacherOverrides(context, listOf(base)).single()
        assertEquals(base, untouched)
        assertTrue(OwnerOverridesStore.packEntries(context).isEmpty())
    }

    @Test
    fun `фото из пакета подставляется когда нет локального и локальное побеждает`() {
        val dir = activatePack(
            """{"format":1,"teachers":{"$key":{"photo":"ovr_pack.jpg"}}}""",
            packPhoto = "ovr_pack.jpg",
        )
        val packPhotoFile = OwnerOverridesStore.photoFileFor(context, base.name)
        assertNotNull(packPhotoFile)
        assertEquals(File(dir, "teachers_photos/ovr_pack.jpg").absolutePath, packPhotoFile!!.absolutePath)

        val localPhoto = File(context.cacheDir, "local_photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = key,
            fio = base.name,
            photoSource = localPhoto,
        )
        val resolved = OwnerOverridesStore.photoFileFor(context, base.name)
        assertNotNull(resolved)
        assertTrue(resolved!!.absolutePath.startsWith(OwnerOverridesStore.photosDir(context).absolutePath))
    }

    @Test
    fun `пакетный override без фото не ломает резолв фото из csv`() {
        activatePack("""{"format":1,"teachers":{"$key":{"fio":"Иванова А. П."}}}""")
        assertNull(OwnerOverridesStore.photoFileFor(context, base.name))
        val model = ContentAssets.teacherPhotoModel(context, base.name, base.photo)
        assertEquals("file:///android_asset/teachers_photos/001_Ivanova.jpg", model)
    }

    @Test
    fun `новый преподаватель только из пакета добавляется в список`() {
        activatePack(
            """{"format":1,"teachers":{"сидорова мария":{"fio":"Сидорова Мария","position":"Педагог"}}}""",
        )
        val merged = OwnerOverridesStore.applyTeacherOverrides(context, listOf(base))
        assertEquals(2, merged.size)
        val extra = merged.first { it.name == "Сидорова Мария" }
        assertEquals("Педагог", extra.position)
        assertEquals("Сидорова Мария", extra.matchName)
    }

    @Test
    fun `смена пакета перечитывает overrides`() {
        activatePack("""{"format":1,"teachers":{"$key":{"fio":"Первая версия"}}}""")
        assertEquals(
            "Первая версия",
            OwnerOverridesStore.applyTeacherOverrides(context, listOf(base)).single().name,
        )
        activatePack("""{"format":1,"teachers":{"$key":{"fio":"Вторая версия"}}}""")
        assertEquals(
            "Вторая версия",
            OwnerOverridesStore.applyTeacherOverrides(context, listOf(base)).single().name,
        )
    }
}
