package app.cove.companion.data.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DriveClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = ArrayList<HttpRequestData>()
    private val sleeps = ArrayList<Long>()

    private fun client(auth: DriveAuth = FakeDriveAuth(), handler: (HttpRequestData) -> Pair<HttpStatusCode, String>) =
        KtorDriveClient(
            HttpClient(MockEngine { req ->
                requests += req
                val (status, body) = handler(req)
                respond(body, status, json)
            }),
            auth, sleep = { sleeps += it }, boundary = { "B" },
        )

    @Test fun backoffDoublesAndCaps() {
        assertEquals(listOf(500L, 1000L, 2000L), (1..3).map { Backoff.delayMs(it) })
        assertEquals(30_000L, Backoff.delayMs(20))
        assertTrue(Backoff.retryable(429) && Backoff.retryable(503))
        assertFalse(Backoff.retryable(404))
    }

    @Test fun multipartBodyHasMetadataThenMedia() {
        val body = String(DriveRequests.multipartBody("B", """{"name":"a"}""", "image/webp", "XYZ".toByteArray()))
        assertEquals(
            "--B\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{\"name\":\"a\"}\r\n--B\r\nContent-Type: image/webp\r\n\r\nXYZ\r\n--B--",
            body,
        )
        assertEquals("multipart/related; boundary=B", DriveRequests.multipartContentType("B"))
    }

    @Test fun queriesEscapeQuotes() {
        assertEquals(
            "name = 'Bob\\'s' and mimeType = 'application/vnd.google-apps.folder' and trashed = false and 'p' in parents",
            DriveRequests.folderQuery("Bob's", "p"),
        )
        assertTrue(DriveRequests.useResumable(6 * 1024 * 1024))
        assertFalse(DriveRequests.useResumable(1024))
    }

    @Test fun findsExistingFoldersWithoutCreating() = runBlocking {
        val c = client { """{"files":[{"id":"F1","name":"x"}]}""".let { HttpStatusCode.OK to it } }
        val f = c.folders()
        assertEquals(DriveFolders("F1", "F1", "F1", "F1"), f)
        assertTrue(requests.all { it.method == HttpMethod.Get })
        assertEquals(4, requests.size)
        c.folders()
        assertEquals("cached after first call", 4, requests.size)
    }

    @Test fun createsMissingFolderUnderParent() = runBlocking {
        val c = client { req ->
            if (req.method == HttpMethod.Get) HttpStatusCode.OK to """{"files":[]}""" else HttpStatusCode.OK to """{"id":"NEW"}"""
        }
        assertEquals("NEW", c.findOrCreateFolder("Photos", "root"))
        val post = requests.last()
        assertEquals(HttpMethod.Post, post.method)
        val body = (post.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertEquals("""{"name":"Photos","mimeType":"application/vnd.google-apps.folder","parents":["root"]}""", body)
    }

    @Test fun smallUploadIsMultipartWithBearer() = runBlocking {
        val c = client { HttpStatusCode.OK to """{"id":"FILE"}""" }
        assertEquals("FILE", c.upload("folder", "a.webp", "image/webp", "abc".toByteArray()))
        val r = requests.single()
        assertEquals("multipart", r.url.parameters["uploadType"])
        assertEquals("Bearer fake-token", r.headers[HttpHeaders.Authorization])
        assertTrue(r.body.contentType.toString().startsWith("multipart/related; boundary=B"))
        assertTrue(r.body.toByteArray().decodeToString().contains("\"parents\":[\"folder\"]"))
    }

    @Test fun largeUploadIsResumable() = runBlocking<Unit> {
        val engineClient = KtorDriveClient(
            HttpClient(MockEngine { req ->
                requests += req
                if (req.method == HttpMethod.Put) respond("""{"id":"BIG"}""", HttpStatusCode.OK, json)
                else respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.Location, "https://upload.example/session"))
            }),
            FakeDriveAuth(),
        )
        assertEquals("BIG", engineClient.upload("f", "v.ogg", "audio/ogg", ByteArray(6 * 1024 * 1024)))
        assertEquals("resumable", requests[0].url.parameters["uploadType"])
        assertEquals("audio/ogg", requests[0].headers["X-Upload-Content-Type"])
        assertEquals("https://upload.example/session", requests[1].url.toString())
        assertEquals(HttpMethod.Put, requests[1].method)
    }

    @Test fun retriesRateLimitThenSucceeds() = runBlocking {
        var n = 0
        val c = client { if (++n < 3) HttpStatusCode.TooManyRequests to "{}" else HttpStatusCode.OK to """{"id":"OK"}""" }
        assertEquals("OK", c.upload("f", "a", "text/plain", ByteArray(1)))
        assertEquals(listOf(500L, 1000L), sleeps)
    }

    @Test fun persistentServerErrorBecomesTransient() = runBlocking {
        val c = client { HttpStatusCode.ServiceUnavailable to "{}" }
        try {
            c.upload("f", "a", "text/plain", ByteArray(1))
            fail()
        } catch (e: DriveException.Transient) {
            assertEquals(Backoff.MAX_ATTEMPTS, requests.size)
        }
    }

    @Test fun unauthorizedRefreshesOnceThenNeedsConsent() = runBlocking {
        val auth = FakeDriveAuth()
        val c = client(auth) { HttpStatusCode.Unauthorized to "{}" }
        try {
            c.delete("x")
            fail()
        } catch (e: DriveException.NeedsConsent) {
            assertEquals(1, auth.invalidated)
            assertEquals(2, requests.size)
        }
    }

    @Test fun forbiddenWithoutRateLimitNeedsConsentAndMissingTokenToo() = runBlocking {
        val c = client { HttpStatusCode.Forbidden to """{"error":{"errors":[{"reason":"insufficientPermissions"}]}}""" }
        try { c.list("f"); fail() } catch (e: DriveException.NeedsConsent) { }
        val none = client(FakeDriveAuth(DriveToken.NeedsConsent)) { HttpStatusCode.OK to "{}" }
        try { none.list("f"); fail() } catch (e: DriveException.NeedsConsent) { }
    }

    @Test fun notFoundIsPermanentButDeleteIgnoresIt() = runBlocking {
        val c = client { HttpStatusCode.NotFound to "{}" }
        c.delete("gone")
        try { c.download("gone", java.io.File.createTempFile("dlfile", "x")); fail() } catch (e: DriveException.Permanent) {
            assertEquals(404, e.status)
        }
    }
}
