package com.politex.adminplus.publish

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

sealed class TokenCheck {
    object Valid : TokenCheck()

    data class Invalid(
        val code: Int,
    ) : TokenCheck()

    object NetworkError : TokenCheck()
}

enum class PublishFailure { NO_TOKEN, AUTH, NETWORK, CONFLICT, SERVER, PAYLOAD }

sealed class PublishResult {
    data class Success(
        val commitSha: String,
    ) : PublishResult()

    data class Failed(
        val failure: PublishFailure,
        val detail: String = "",
    ) : PublishResult()
}

data class PublishPhoto(
    val repoPath: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is PublishPhoto && repoPath == other.repoPath && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * repoPath.hashCode() + bytes.contentHashCode()
}

data class PublishPayload(
    val overridesJson: String,
    val photos: List<PublishPhoto>,
)

interface GithubPublisher {
    suspend fun verifyToken(token: String): TokenCheck

    suspend fun publish(
        token: String,
        payload: PublishPayload,
    ): PublishResult
}

class GithubDataApiPublisher(
    private val api: GithubApi,
    private val owner: String,
    private val repo: String,
    private val branch: String = DEFAULT_BRANCH,
) : GithubPublisher {
    private val repoPath = "/repos/$owner/$repo"

    override suspend fun verifyToken(token: String): TokenCheck {
        if (token.isBlank()) return TokenCheck.Invalid(0)
        val reply = api.get(repoPath, token) ?: return TokenCheck.NetworkError
        if (reply.code == HTTP_OK) {
            return if (hasPushPermission(reply.body)) TokenCheck.Valid else TokenCheck.Invalid(HTTP_FORBIDDEN)
        }
        return TokenCheck.Invalid(reply.code)
    }

    override suspend fun publish(
        token: String,
        payload: PublishPayload,
    ): PublishResult {
        if (token.isBlank()) return PublishResult.Failed(PublishFailure.NO_TOKEN)
        if (payload.overridesJson.isBlank()) return PublishResult.Failed(PublishFailure.PAYLOAD, "empty payload")
        var last: PublishResult = PublishResult.Failed(PublishFailure.SERVER)
        repeat(MAX_ATTEMPTS) { attempt ->
            last = publishOnce(token, payload)
            val conflict = last is PublishResult.Failed && last.failure == PublishFailure.CONFLICT
            if (!conflict || attempt == MAX_ATTEMPTS - 1) return last
        }
        return last
    }

    private suspend fun publishOnce(
        token: String,
        payload: PublishPayload,
    ): PublishResult {
        val refReply = api.get("$repoPath/git/ref/heads/$branch", token) ?: return network()
        if (isAuthFailure(refReply.code)) return PublishResult.Failed(PublishFailure.AUTH, "ref ${refReply.code}")
        if (refReply.code != HTTP_OK) return server(refReply)
        val baseCommit = jsonSha(refReply.body, "object") ?: return PublishResult.Failed(PublishFailure.SERVER, "no base commit")

        val commitReply = api.get("$repoPath/git/commits/$baseCommit", token) ?: return network()
        if (isAuthFailure(commitReply.code)) return PublishResult.Failed(PublishFailure.AUTH, "commit ${commitReply.code}")
        if (commitReply.code != HTTP_OK) return server(commitReply)
        val baseTree = jsonSha(commitReply.body, "tree") ?: return PublishResult.Failed(PublishFailure.SERVER, "no base tree")

        val remoteShas = fetchTreeShas(token, baseTree)

        val treeEntries = JSONArray()
        treeEntries.put(
            JSONObject()
                .put("path", OVERRIDES_PATH)
                .put("mode", MODE_FILE)
                .put("type", "blob")
                .put("content", payload.overridesJson),
        )
        for (photo in payload.photos) {
            if (photo.bytes.isEmpty()) return PublishResult.Failed(PublishFailure.PAYLOAD, photo.repoPath)
            val blobSha = gitBlobSha(photo.bytes)
            var sha = blobSha
            if (remoteShas[photo.repoPath] != blobSha) {
                val blobBody =
                    JSONObject()
                        .put("content", Base64.encodeToString(photo.bytes, Base64.NO_WRAP))
                        .put("encoding", "base64")
                val blobReply = api.post("$repoPath/git/blobs", token, blobBody.toString()) ?: return network()
                if (isAuthFailure(blobReply.code)) return PublishResult.Failed(PublishFailure.AUTH, "blob ${blobReply.code}")
                if (blobReply.code !in HTTP_OK..HTTP_CREATED) return server(blobReply)
                sha = runCatching { JSONObject(blobReply.body).optString("sha") }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: return PublishResult.Failed(PublishFailure.SERVER, "no blob sha")
            }
            treeEntries.put(
                JSONObject()
                    .put("path", photo.repoPath)
                    .put("mode", MODE_FILE)
                    .put("type", "blob")
                    .put("sha", sha),
            )
        }

        val treeBody =
            JSONObject()
                .put("base_tree", baseTree)
                .put("tree", treeEntries)
        val treeReply = api.post("$repoPath/git/trees", token, treeBody.toString()) ?: return network()
        if (isAuthFailure(treeReply.code)) return PublishResult.Failed(PublishFailure.AUTH, "tree ${treeReply.code}")
        if (treeReply.code !in HTTP_OK..HTTP_CREATED) return server(treeReply)
        val newTree = runCatching { JSONObject(treeReply.body).optString("sha") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return PublishResult.Failed(PublishFailure.SERVER, "no tree sha")

        val commitBody =
            JSONObject()
                .put("message", COMMIT_MESSAGE)
                .put("tree", newTree)
                .put("parents", JSONArray().put(baseCommit))
        val newCommitReply = api.post("$repoPath/git/commits", token, commitBody.toString()) ?: return network()
        if (isAuthFailure(newCommitReply.code)) return PublishResult.Failed(PublishFailure.AUTH, "commit ${newCommitReply.code}")
        if (newCommitReply.code == HTTP_CONFLICT || newCommitReply.code == HTTP_UNPROCESSABLE) {
            return PublishResult.Failed(PublishFailure.CONFLICT, "commit ${newCommitReply.code}")
        }
        if (newCommitReply.code !in HTTP_OK..HTTP_CREATED) return server(newCommitReply)
        val newCommit = runCatching { JSONObject(newCommitReply.body).optString("sha") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return PublishResult.Failed(PublishFailure.SERVER, "no new commit sha")

        val refBody =
            JSONObject()
                .put("sha", newCommit)
                .put("force", false)
        val updateReply = api.patch("$repoPath/git/refs/heads/$branch", token, refBody.toString()) ?: return network()
        if (isAuthFailure(updateReply.code)) return PublishResult.Failed(PublishFailure.AUTH, "ref update ${updateReply.code}")
        if (updateReply.code == HTTP_CONFLICT || updateReply.code == HTTP_UNPROCESSABLE) {
            return PublishResult.Failed(PublishFailure.CONFLICT, "ref update ${updateReply.code}")
        }
        if (updateReply.code !in HTTP_OK..HTTP_CREATED) return server(updateReply)
        return PublishResult.Success(newCommit)
    }

    private suspend fun fetchTreeShas(
        token: String,
        baseTree: String,
    ): Map<String, String> {
        val reply = api.get("$repoPath/git/trees/$baseTree?recursive=1", token) ?: return emptyMap()
        if (reply.code != HTTP_OK) return emptyMap()
        return runCatching {
            val out = HashMap<String, String>()
            val entries = JSONObject(reply.body).optJSONArray("tree") ?: return emptyMap()
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                if (entry.optString("type") != "blob") continue
                val path = entry.optString("path")
                val sha = entry.optString("sha")
                if (path.isNotBlank() && sha.isNotBlank()) out[path] = sha
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun network() = PublishResult.Failed(PublishFailure.NETWORK)

    private fun server(reply: ApiReply) = PublishResult.Failed(PublishFailure.SERVER, "http ${reply.code}")

    private fun isAuthFailure(code: Int) = code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN

    private fun jsonSha(
        body: String,
        field: String,
    ): String? =
        runCatching {
            JSONObject(body)
                .optJSONObject(field)
                ?.optString("sha")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    companion object {
        const val COMMIT_MESSAGE = "Обновление данных преподавателей"
        const val OVERRIDES_PATH = "content/owner_overrides.json"
        const val DEFAULT_BRANCH = "main"
        private const val MODE_FILE = "100644"
        private const val MAX_ATTEMPTS = 2
        private const val HTTP_OK = 200
        private const val HTTP_CREATED = 201
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_CONFLICT = 409
        private const val HTTP_UNPROCESSABLE = 422

        fun gitBlobSha(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.US_ASCII))
            digest.update(bytes)
            return digest
                .digest()
                .joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
        }

        fun hasPushPermission(body: String): Boolean =
            runCatching {
                val permissions = JSONObject(body).optJSONObject("permissions") ?: return true
                permissions.optBoolean("push", false)
            }.getOrDefault(false)
    }
}
