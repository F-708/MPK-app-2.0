package com.politex.adminplus.publish

import android.content.Context
import com.politex.content.ContentAssets
import com.politex.content.OwnerOverridesStore
import com.politex.content.PackTeacherOverride
import com.politex.util.Csv
import java.io.File

internal data class BaseTeacher(
    val key: String,
    val name: String,
    val position: String,
    val subjects: String,
    val phone: String,
    val experience: String,
    val category: String,
)

object OwnerPublishPayloadBuilder {
    const val OVERRIDES_REPO_PATH = GithubDataApiPublisher.OVERRIDES_PATH
    private const val PHOTOS_REPO_PREFIX = "content/teachers_photos/"

    fun photoRepoFileName(key: String): String = "ovr_" + key.hashCode().toUInt().toString(16) + ".jpg"

    fun photoRepoPath(key: String): String = PHOTOS_REPO_PREFIX + photoRepoFileName(key)

    fun build(context: Context): PublishPayload? =
        runCatching {
            val local = OwnerOverridesStore.listTeachers(context)
            val base = parseBaseTeachers(context).associateBy { it.key }
            val entries = LinkedHashMap<String, PackTeacherOverride>()
            val photos = mutableListOf<PublishPhoto>()
            for (record in local) {
                val b = base[record.key]
                val photoName =
                    record.photoName?.let { name ->
                        // Sentinel «фото убрано» едет в пакет как есть: приёмник
                        // покажет заглушку вместо исходного фото.
                        if (name == OwnerOverridesStore.PHOTO_REMOVED) return@let name
                        val file = File(OwnerOverridesStore.photosDir(context), name)
                        if (file.isFile && file.length() > 0L) {
                            photos += PublishPhoto(photoRepoPath(record.key), file.readBytes())
                            photoRepoFileName(record.key)
                        } else {
                            null
                        }
                    }
                val pack =
                    PackTeacherOverride(
                        key = record.key,
                        fio = record.fio.takeIf { b == null || it != b.name },
                        position = record.position.takeIf { b == null || it != b.position },
                        phone = record.phone.takeIf { b == null || it != b.phone },
                        subjects = record.subjects.takeIf { b == null || it != b.subjects },
                        experience = record.experience.takeIf { b == null || it != b.experience },
                        category = record.category.takeIf { b == null || it != b.category },
                        photo = photoName,
                    )
                if (!pack.isEmpty) entries[record.key] = pack
            }
            PublishPayload(
                overridesJson = OwnerOverridesStore.serializePackJson(entries, System.currentTimeMillis()),
                photos = photos,
            )
        }.getOrNull()

    internal fun parseBaseTeachers(context: Context): List<BaseTeacher> =
        ContentAssets
            .open(context, "teachers.csv")
            .bufferedReader(Charsets.UTF_8)
            .use { Csv.stripBom(it.readText()) }
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val p = Csv.splitRow(line)
                val name = cell(p, 0)
                if (name.isBlank()) return@mapNotNull null
                BaseTeacher(
                    key = OwnerOverridesStore.keyOf(name),
                    name = name,
                    position = cell(p, 1),
                    subjects = cell(p, 2),
                    phone = cell(p, 4),
                    experience = cell(p, 8),
                    category = cell(p, 9),
                )
            }

    private fun cell(
        p: List<String>,
        i: Int,
    ): String {
        val v = p.getOrElse(i) { "" }.trim()
        return if (v.isEmpty() || v == "—" || v == "–" || v == "-") "" else v
    }
}
