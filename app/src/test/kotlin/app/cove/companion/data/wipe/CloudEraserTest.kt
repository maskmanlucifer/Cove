package app.cove.companion.data.wipe

import app.cove.companion.data.drive.DriveException
import app.cove.companion.data.drive.FakeDriveClient
import app.cove.companion.data.sync.SyncTable
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.nio.file.Files
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudEraserTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = ArrayList<HttpRequestData>()
    private val pauses = ArrayList<Long>()
    private val tables = listOf(SyncTable("todos"), SyncTable("category_memory", key = "token"))

    private fun rows(n: Int) = (1..n).joinToString(",", "[", "]") { """{"id":"$it"}""" }

    private fun eraser(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        userId: String? = "u1",
        drive: app.cove.companion.data.drive.DriveClient? = null,
        token: suspend () -> String? = { "tok" },
    ) = CloudEraser(
        "https://x.supabase.co", "anon",
        HttpClient(MockEngine { req -> requests += req; handler(req) }),
        tables, token, { }, userId, drive, pause = { pauses += it }, pageSize = 2,
    )

    private fun table(req: HttpRequestData) = req.url.encodedPath.substringAfterLast('/')

    @Test fun rowsAreDeletedInPagesUntilNoneRemain() = runBlocking {
        var todoCalls = 0
        val r = eraser({ req ->
            when {
                req.url.encodedPath.contains("/rest/v1/todos") -> respond(if (++todoCalls <= 2) rows(2) else rows(1), HttpStatusCode.OK, json)
                req.url.encodedPath.contains("/rest/v1/") -> respond("[]", HttpStatusCode.OK, json)
                else -> respond("[]", HttpStatusCode.OK, json)
            }
        }).eraseAll()
        assertEquals(StepResult.Done(5), r.supabase)
        assertEquals(3, todoCalls)
        val first = requests.first { it.method == HttpMethod.Delete }
        assertEquals("not.is.null", first.url.parameters["id"])
        assertEquals("2", first.url.parameters["limit"])
        assertEquals("Bearer tok", first.headers[HttpHeaders.Authorization])
        assertEquals("not.is.null", requests.first { table(it) == "category_memory" }.url.parameters["token"])
    }

    @Test fun transientErrorsAreRetriedWithBackoff() = runBlocking {
        var calls = 0
        val r = eraser({ req ->
            if (req.url.encodedPath.contains("/rest/v1/todos") && ++calls < 3) respondError(HttpStatusCode.ServiceUnavailable)
            else respond("[]", HttpStatusCode.OK, json)
        }).eraseAll()
        assertEquals(StepResult.Done(0), r.supabase)
        assertEquals(listOf(1000L, 2000L), pauses)
    }

    @Test fun persistentFailureIsReportedNeverAsSuccess() = runBlocking {
        val r = eraser({ req ->
            if (req.url.encodedPath.contains("/rest/v1/todos")) respondError(HttpStatusCode.BadGateway) else respond(rows(1), HttpStatusCode.OK, json)
        }).eraseAll()
        val s = r.supabase as StepResult.Failed
        assertEquals(1, s.count)
        assertTrue(s.reason.contains("todos"))
        assertEquals(false, r.ok)
    }

    @Test fun tableMissingOnTheServerCountsAsNothingToDelete() = runBlocking {
        val r = eraser({ respondError(HttpStatusCode.NotFound) }).eraseAll()
        assertEquals(StepResult.Done(0), r.supabase)
    }

    @Test fun forbiddenIsAFailureWithPlainReason() = runBlocking {
        val r = eraser({ respondError(HttpStatusCode.Forbidden) }).eraseAll()
        assertTrue(r.supabase is StepResult.Failed)
        assertTrue((r.supabase as StepResult.Failed).reason.contains("refused"))
    }

    @Test fun expiredSessionStopsWithSignInMessage() = runBlocking {
        val r = eraser({ respondError(HttpStatusCode.Unauthorized) }, token = { "t" }).eraseAll()
        assertTrue((r.supabase as StepResult.Failed).reason.contains("sign-in"))
    }

    @Test fun serverThatIgnoresLimitStillTerminates() = runBlocking {
        var calls = 0
        val r = eraser({ req ->
            if (req.url.encodedPath.contains("/rest/v1/todos")) respond(if (++calls == 1) rows(7) else "[]", HttpStatusCode.OK, json) else respond("[]", HttpStatusCode.OK, json)
        }).eraseAll()
        assertEquals(StepResult.Done(7), r.supabase)
    }

    @Test fun thumbnailsAreListedThenDeletedUnderTheUsersPrefix() = runBlocking {
        var listCalls = 0
        val r = eraser({ req ->
            val path = req.url.encodedPath
            when {
                path.contains("/storage/v1/object/list/thumbs") -> respond(if (++listCalls == 1) """[{"name":"a.webp"},{"name":"b.webp"}]""" else "[]", HttpStatusCode.OK, json)
                path.endsWith("/storage/v1/object/thumbs") && req.method == HttpMethod.Delete -> respond("""[{"name":"u1/a.webp"},{"name":"u1/b.webp"}]""", HttpStatusCode.OK, json)
                else -> respond("[]", HttpStatusCode.OK, json)
            }
        }).eraseAll()
        assertEquals(StepResult.Done(2), r.thumbs)
        val del = requests.first { it.method == HttpMethod.Delete && it.url.encodedPath.endsWith("/storage/v1/object/thumbs") }
        assertTrue(String((del.body as io.ktor.http.content.TextContent).text.toByteArray()).contains("u1/a.webp"))
    }

    @Test fun driveFilesAreCountedAndDeleted() = runBlocking {
        Locale.setDefault(Locale.US)
        val root = Files.createTempDirectory("drive").toFile()
        val fake = FakeDriveClient(root)
        val f = fake.folders()
        fake.upload(f.photos, "1.webp", "image/webp", ByteArray(1))
        fake.upload(f.photos, "2.webp", "image/webp", ByteArray(1))
        fake.upload(f.backups, "cove-2026-07.json.gz", "application/gzip", ByteArray(1))
        val r = eraser({ respond("[]", HttpStatusCode.OK, json) }, drive = fake).eraseAll()
        assertEquals(StepResult.Done(3), r.drive)
        assertEquals("Supabase: 0 rows. Drive: 3 files. Thumbnails: 0.", r.summary())
    }

    @Test fun driveNeedingConsentIsReportedAsNotDeleted() = runBlocking {
        val denied = object : app.cove.companion.data.drive.DriveClient {
            override suspend fun folders() = throw DriveException.NeedsConsent()
            override suspend fun upload(folderId: String, name: String, mime: String, content: ByteArray) = ""
            override suspend fun download(fileId: String, dest: java.io.File) = Unit
            override suspend fun delete(fileId: String) = Unit
            override suspend fun list(folderId: String) = emptyList<app.cove.companion.data.drive.DriveFile>()
        }
        val r = eraser({ respond("[]", HttpStatusCode.OK, json) }, drive = denied).eraseAll()
        assertTrue((r.drive as StepResult.Failed).reason.contains("permission"))
        assertEquals(false, r.ok)
        assertNotNull(r.lines().first { it.startsWith("Google Drive") })
    }

    @Test fun notSignedInSkipsEverything() = runBlocking {
        val r = eraser({ error("no requests expected") }, userId = null).eraseAll()
        assertTrue(r.supabase is StepResult.Skipped)
        assertTrue(r.ok)
        assertTrue(requests.isEmpty())
    }

    @Test fun missingDriveIsSkippedNotFailed() = runBlocking {
        val r = eraser({ respond("[]", HttpStatusCode.OK, json) }).eraseAll()
        assertTrue(r.drive is StepResult.Skipped)
        assertTrue(r.ok)
    }
}
