package com.politex.adminplus.publish

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeGithubApi : GithubApi {
    val calls = mutableListOf<Triple<String, String, String?>>()
    val routes = HashMap<String, ArrayDeque<ApiReply?>>()
    var defaultReply: ApiReply? = ApiReply(404, "{}")

    fun route(
        method: String,
        path: String,
        vararg replies: ApiReply?,
    ) {
        routes["$method $path"] = ArrayDeque(replies.toList())
    }

    private fun reply(
        method: String,
        path: String,
        body: String?,
    ): ApiReply? {
        calls += Triple(method, path, body)
        val queue = routes["$method $path"]
        if (queue != null && queue.isNotEmpty()) return queue.removeFirst()
        return defaultReply
    }

    override suspend fun get(
        path: String,
        token: String,
    ): ApiReply? = reply("GET", path, null)

    override suspend fun post(
        path: String,
        token: String,
        json: String,
    ): ApiReply? = reply("POST", path, json)

    override suspend fun patch(
        path: String,
        token: String,
        json: String,
    ): ApiReply? = reply("PATCH", path, json)

    fun callsTo(
        method: String,
        path: String,
    ): List<String?> = calls.filter { it.first == method && it.second == path }.map { it.third }
}

@RunWith(RobolectricTestRunner::class)
class GithubDataApiPublisherTest {
    private val repoPath = "/repos/F-708/moy-polytech"

    private fun publisher(api: FakeGithubApi) = GithubDataApiPublisher(api, "F-708", "moy-polytech")

    private fun stubHead(api: FakeGithubApi) {
        api.route("GET", "$repoPath/git/ref/heads/main", ApiReply(200, """{"object":{"sha":"C1"}}"""))
        api.route("GET", "$repoPath/git/commits/C1", ApiReply(200, """{"tree":{"sha":"T1"}}"""))
        api.route("GET", "$repoPath/git/trees/T1?recursive=1", ApiReply(200, """{"tree":[]}"""))
        api.route("POST", "$repoPath/git/trees", ApiReply(201, """{"sha":"T2"}"""))
        api.route("POST", "$repoPath/git/commits", ApiReply(201, """{"sha":"C2"}"""))
        api.route("PATCH", "$repoPath/git/refs/heads/main", ApiReply(200, """{"ref":"refs/heads/main"}"""))
    }

    private val payload =
        PublishPayload(
            overridesJson = """{"format":1,"teachers":{"иванова анна":{"fio":"Иванова А. П."}}}""",
            photos = listOf(PublishPhoto("content/teachers_photos/ovr_aa.jpg", byteArrayOf(1, 2, 3, 4))),
        )

    @Test
    fun `успешная публикация делает один атомарный коммит`() =
        runBlocking {
            val api = FakeGithubApi()
            stubHead(api)
            api.route("POST", "$repoPath/git/blobs", ApiReply(201, """{"sha":"B1"}"""))

            val result = publisher(api).publish("tok", payload)

            assertTrue(result is PublishResult.Success)
            assertEquals("C2", (result as PublishResult.Success).commitSha)

            val commits = api.callsTo("POST", "$repoPath/git/commits")
            assertEquals(1, commits.size)
            val commitBody = JSONObject(commits.single()!!)
            assertEquals(GithubDataApiPublisher.COMMIT_MESSAGE, commitBody.getString("message"))
            assertEquals("T2", commitBody.getString("tree"))
            assertEquals("C1", commitBody.getJSONArray("parents").getString(0))

            val treeBody = JSONObject(api.callsTo("POST", "$repoPath/git/trees").single()!!)
            assertEquals("T1", treeBody.getString("base_tree"))
            val entries = treeBody.getJSONArray("tree")
            assertEquals(2, entries.length())
            val jsonEntry = entries.getJSONObject(0)
            assertEquals("content/owner_overrides.json", jsonEntry.getString("path"))
            assertEquals(payload.overridesJson, jsonEntry.getString("content"))
            val photoEntry = entries.getJSONObject(1)
            assertEquals("content/teachers_photos/ovr_aa.jpg", photoEntry.getString("path"))
            assertEquals("B1", photoEntry.getString("sha"))

            val refBody = JSONObject(api.callsTo("PATCH", "$repoPath/git/refs/heads/main").single()!!)
            assertEquals("C2", refBody.getString("sha"))
            assertEquals(false, refBody.getBoolean("force"))
        }

    @Test
    fun `неизменное фото не загружается повторно`() =
        runBlocking {
            val api = FakeGithubApi()
            stubHead(api)
            val photoBytes = byteArrayOf(7, 7, 7)
            val existingSha = GithubDataApiPublisher.gitBlobSha(photoBytes)
            api.routes["GET $repoPath/git/trees/T1?recursive=1"] =
                ArrayDeque(
                    listOf(
                        ApiReply(
                            200,
                            """{"tree":[{"path":"content/teachers_photos/ovr_aa.jpg","type":"blob","sha":"$existingSha"}]}""",
                        ),
                    ),
                )

            val result =
                publisher(api).publish(
                    "tok",
                    PublishPayload(payload.overridesJson, listOf(PublishPhoto("content/teachers_photos/ovr_aa.jpg", photoBytes))),
                )

            assertTrue(result is PublishResult.Success)
            assertEquals(0, api.callsTo("POST", "$repoPath/git/blobs").size)
        }

    @Test
    fun `ответ 401 превращается в ошибку авторизации`() =
        runBlocking {
            val api = FakeGithubApi()
            api.route("GET", "$repoPath/git/ref/heads/main", ApiReply(401, """{"message":"Bad credentials"}"""))

            val result = publisher(api).publish("bad-token", payload)

            assertEquals(PublishResult.Failed(PublishFailure.AUTH, "ref 401"), result)
            assertEquals(1, api.calls.size)
        }

    @Test
    fun `401 на обновлении ref тоже ошибка авторизации`() =
        runBlocking {
            val api = FakeGithubApi()
            stubHead(api)
            api.route("POST", "$repoPath/git/blobs", ApiReply(201, """{"sha":"B1"}"""))
            api.route("PATCH", "$repoPath/git/refs/heads/main", ApiReply(403, "{}"))

            val result = publisher(api).publish("tok", payload)

            assertTrue(result is PublishResult.Failed)
            assertEquals(PublishFailure.AUTH, (result as PublishResult.Failed).failure)
        }

    @Test
    fun `обрыв сети даёт ошибку сети без падения`() =
        runBlocking {
            val api = FakeGithubApi()
            api.route("GET", "$repoPath/git/ref/heads/main", null)

            val result = publisher(api).publish("tok", payload)

            assertEquals(PublishResult.Failed(PublishFailure.NETWORK), result)
        }

    @Test
    fun `конфликт при обновлении ref повторяет попытку один раз`() =
        runBlocking {
            val api = FakeGithubApi()
            api.route(
                "GET",
                "$repoPath/git/ref/heads/main",
                ApiReply(200, """{"object":{"sha":"C1"}}"""),
                ApiReply(200, """{"object":{"sha":"C1B"}}"""),
            )
            api.route(
                "GET",
                "$repoPath/git/commits/C1",
                ApiReply(200, """{"tree":{"sha":"T1"}}"""),
            )
            api.route(
                "GET",
                "$repoPath/git/commits/C1B",
                ApiReply(200, """{"tree":{"sha":"T1B"}}"""),
            )
            api.route(
                "GET",
                "$repoPath/git/trees/T1?recursive=1",
                ApiReply(200, """{"tree":[]}"""),
            )
            api.route(
                "GET",
                "$repoPath/git/trees/T1B?recursive=1",
                ApiReply(200, """{"tree":[]}"""),
            )
            api.route("POST", "$repoPath/git/blobs", ApiReply(201, """{"sha":"B1"}"""), ApiReply(201, """{"sha":"B1"}"""))
            api.route("POST", "$repoPath/git/trees", ApiReply(201, """{"sha":"T2"}"""), ApiReply(201, """{"sha":"T2B"}"""))
            api.route("POST", "$repoPath/git/commits", ApiReply(201, """{"sha":"C2"}"""), ApiReply(201, """{"sha":"C2B"}"""))
            api.route(
                "PATCH",
                "$repoPath/git/refs/heads/main",
                ApiReply(409, """{"message":"conflict"}"""),
                ApiReply(200, """{"ref":"refs/heads/main"}"""),
            )

            val result = publisher(api).publish("tok", payload)

            assertTrue(result is PublishResult.Success)
            assertEquals("C2B", (result as PublishResult.Success).commitSha)
            assertEquals(2, api.callsTo("POST", "$repoPath/git/commits").size)
        }

    @Test
    fun `пустой токен не делает ни одного запроса`() =
        runBlocking {
            val api = FakeGithubApi()
            val result = publisher(api).publish("", payload)
            assertEquals(PublishResult.Failed(PublishFailure.NO_TOKEN), result)
            assertTrue(api.calls.isEmpty())
        }

    @Test
    fun `проверка токена - успех только при праве push`() =
        runBlocking {
            val api = FakeGithubApi()
            api.route("GET", repoPath, ApiReply(200, """{"permissions":{"admin":false,"push":true,"pull":true}}"""))
            assertEquals(TokenCheck.Valid, publisher(api).verifyToken("tok"))

            val noPush = FakeGithubApi()
            noPush.route("GET", repoPath, ApiReply(200, """{"permissions":{"admin":false,"push":false,"pull":true}}"""))
            assertEquals(TokenCheck.Invalid(403), publisher(noPush).verifyToken("tok"))

            val bad = FakeGithubApi()
            bad.route("GET", repoPath, ApiReply(401, "{}"))
            assertEquals(TokenCheck.Invalid(401), publisher(bad).verifyToken("tok"))

            val offline = FakeGithubApi()
            offline.route("GET", repoPath, null)
            assertEquals(TokenCheck.NetworkError, publisher(offline).verifyToken("tok"))
        }

    @Test
    fun `git blob sha совпадает с эталоном git hash-object`() {
        assertEquals("f2ba8f84ab5c1bce84a7b441cb1959cfc7093b7f", GithubDataApiPublisher.gitBlobSha("abc".toByteArray()))
    }

    @Test
    fun `в дерево коммита попадают оба файла`() =
        runBlocking {
            val api = FakeGithubApi()
            stubHead(api)
            api.route("POST", "$repoPath/git/blobs", ApiReply(201, """{"sha":"B1"}"""))
            publisher(api).publish("tok", payload)
            val treeBody = JSONObject(api.callsTo("POST", "$repoPath/git/trees").single()!!)
            assertTrue(treeBody.get("tree") is JSONArray)
        }
}
