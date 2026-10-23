package com.politex.adminplus.publish

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ApiReply(
    val code: Int,
    val body: String,
)

interface GithubApi {
    suspend fun get(
        path: String,
        token: String,
    ): ApiReply?

    suspend fun post(
        path: String,
        token: String,
        json: String,
    ): ApiReply?

    suspend fun patch(
        path: String,
        token: String,
        json: String,
    ): ApiReply?
}

class OkHttpGithubApi(
    private val client: OkHttpClient = defaultClient,
    private val baseUrl: String = API_BASE_URL,
) : GithubApi {
    override suspend fun get(
        path: String,
        token: String,
    ): ApiReply? = execute(path, token) { it.get() }

    override suspend fun post(
        path: String,
        token: String,
        json: String,
    ): ApiReply? = execute(path, token) { builder -> builder.post(json.toRequestBody(JSON_MEDIA)) }

    override suspend fun patch(
        path: String,
        token: String,
        json: String,
    ): ApiReply? = execute(path, token) { builder -> builder.patch(json.toRequestBody(JSON_MEDIA)) }

    private suspend fun execute(
        path: String,
        token: String,
        method: (Request.Builder) -> Request.Builder,
    ): ApiReply? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(baseUrl + path)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", GITHUB_ACCEPT)
                        .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                        .header("User-Agent", USER_AGENT)
                        .let(method)
                        .build()
                client
                    .newCall(request)
                    .execute()
                    .use { response -> ApiReply(response.code, response.body?.string().orEmpty()) }
            }.getOrNull()
        }

    companion object {
        const val API_BASE_URL = "https://api.github.com"
        const val USER_AGENT = "MoyPolitech-AdminPlus"
        private const val GITHUB_ACCEPT = "application/vnd.github+json"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }
    }
}
