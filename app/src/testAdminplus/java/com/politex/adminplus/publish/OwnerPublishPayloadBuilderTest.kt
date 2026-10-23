package com.politex.adminplus.publish

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.content.OwnerOverridesStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OwnerPublishPayloadBuilderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        OwnerOverridesStore.clearAll(context)
    }

    @After
    fun tearDown() {
        OwnerOverridesStore.clearAll(context)
    }

    @Test
    fun `базовый csv читается из активов`() {
        val base = OwnerPublishPayloadBuilder.parseBaseTeachers(context)
        assertTrue(base.size >= 50)
        assertTrue(base.all { it.name.isNotBlank() })
        assertTrue(base.all { it.key == OwnerOverridesStore.keyOf(it.name) })
    }

    @Test
    fun `без локальных правок публикуется пустой список учителей`() {
        val payload = OwnerPublishPayloadBuilder.build(context)
        assertNotNull(payload)
        val root = JSONObject(payload!!.overridesJson)
        assertEquals(1, root.getInt("format"))
        assertEquals(0, root.getJSONObject("teachers").length())
        assertTrue(payload.photos.isEmpty())
    }

    @Test
    fun `в json попадают только изменённые поля`() {
        val base = OwnerPublishPayloadBuilder.parseBaseTeachers(context).first()
        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = base.key,
            fio = base.name,
            position = base.position,
            phone = "999-999-99",
            subjects = base.subjects,
            experience = base.experience,
            category = base.category,
        )
        val payload = OwnerPublishPayloadBuilder.build(context)
        assertNotNull(payload)
        val teachers = JSONObject(payload!!.overridesJson).getJSONObject("teachers")
        val entry = teachers.getJSONObject(base.key)
        assertEquals("999-999-99", entry.getString("phone"))
        assertFalse(entry.has("fio"))
        assertFalse(entry.has("position"))
        assertFalse(entry.has("subjects"))
        assertFalse(entry.has("photo"))
        assertTrue(payload.photos.isEmpty())
    }

    @Test
    fun `переименование попадает в json а ключ остаётся исходным`() {
        val base = OwnerPublishPayloadBuilder.parseBaseTeachers(context).first()
        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = base.key,
            fio = base.name + " Новая",
            position = base.position,
            phone = base.phone,
            subjects = base.subjects,
            experience = base.experience,
            category = base.category,
        )
        val payload = OwnerPublishPayloadBuilder.build(context)!!
        val teachers = JSONObject(payload.overridesJson).getJSONObject("teachers")
        assertTrue(teachers.has(base.key))
        assertEquals(base.name + " Новая", teachers.getJSONObject(base.key).getString("fio"))
    }

    @Test
    fun `фото правки прикрепляется байтами и стабильным именем`() {
        val base = OwnerPublishPayloadBuilder.parseBaseTeachers(context).first()
        val photo = File(context.cacheDir, "publish_test_photo.jpg").apply { writeBytes(byteArrayOf(5, 6, 7, 8, 9)) }
        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = base.key,
            fio = base.name,
            photoSource = photo,
        )
        val payload = OwnerPublishPayloadBuilder.build(context)!!
        assertEquals(1, payload.photos.size)
        val sent = payload.photos.single()
        val expectedName = OwnerPublishPayloadBuilder.photoRepoFileName(base.key)
        assertEquals("content/teachers_photos/$expectedName", sent.repoPath)
        assertTrue(sent.bytes.isNotEmpty())
        val entry = JSONObject(payload.overridesJson).getJSONObject("teachers").getJSONObject(base.key)
        assertEquals(expectedName, entry.getString("photo"))
        assertTrue(expectedName.startsWith("ovr_") && expectedName.endsWith(".jpg"))
    }

    @Test
    fun `новый преподаватель без базы публикуется целиком`() {
        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = "сидорова мария ивановна",
            fio = "Сидорова Мария Ивановна",
            position = "Педагог",
        )
        val payload = OwnerPublishPayloadBuilder.build(context)!!
        val entry = JSONObject(payload.overridesJson).getJSONObject("teachers").getJSONObject("сидорова мария ивановна")
        assertEquals("Сидорова Мария Ивановна", entry.getString("fio"))
        assertEquals("Педагог", entry.getString("position"))
    }
}
