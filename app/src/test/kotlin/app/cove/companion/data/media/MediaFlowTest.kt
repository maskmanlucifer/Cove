package app.cove.companion.data.media

import app.cove.companion.data.drive.DriveException
import app.cove.companion.data.drive.FakeDriveClient
import app.cove.companion.data.local.entity.JournalMediaEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaFlowTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Rows(val items: MutableList<JournalMediaEntity>) : MediaRows {
        override suspend fun pending() = items.filter { it.uploadState != "done" }
        override suspend fun save(media: JournalMediaEntity) { items[items.indexOfFirst { it.id == media.id }] = media }
    }

    private class Thumbs(var fail: Boolean = false) : ThumbStore {
        val uploaded = ArrayList<String>()
        override suspend fun upload(mediaId: String, file: File) { if (fail) error("down"); uploaded += mediaId }
        override suspend fun download(mediaId: String, dest: File): Boolean = false
    }

    private fun row(id: String, kind: String = "photo", ext: String = "webp", write: Boolean = true): JournalMediaEntity {
        val f = File(tmp.root, "$id.$ext").also { if (write) it.writeText("data-$id") }
        val t = File(tmp.root, "$id.thumb").also { if (write) it.writeText("t") }
        return JournalMediaEntity(id, "e", kind, f.path, t.path)
    }

    private fun drive() = FakeDriveClient(File(tmp.root, "drive"))

    @Test fun pendingPhotoBecomesDoneWithDriveId() = runBlocking {
        val rows = Rows(mutableListOf(row("p1"), row("v1", "voice", "ogg")))
        val thumbs = Thumbs()
        val run = MediaUploader(rows, drive(), thumbs).run()
        assertEquals(2, run.done)
        assertTrue(rows.items.all { it.uploadState == "done" })
        assertEquals("Cove/Photos/p1.webp", rows.items[0].driveFileId)
        assertEquals("Cove/Voice/v1.ogg", rows.items[1].driveFileId)
        assertEquals(listOf("p1", "v1"), thumbs.uploaded)
        assertEquals("data-p1", File(tmp.root, "drive/Cove/Photos/p1.webp").readText())
    }

    @Test fun transientFailureLeavesRowPendingAndAsksForRetry() = runBlocking {
        val fake = drive().apply { failWith = DriveException.Transient("offline") }
        val rows = Rows(mutableListOf(row("p1")))
        val run = MediaUploader(rows, fake, null).run()
        assertTrue(run.retryLater)
        assertEquals("pending", rows.items[0].uploadState)
        fake.failWith = null
        assertEquals(1, MediaUploader(rows, fake, null).run().done)
        assertEquals("done", rows.items[0].uploadState)
    }

    @Test fun permanentFailureMarksFailedAndLaterRunRecovers() = runBlocking {
        val fake = drive().apply { failWith = DriveException.Permanent(400, "bad") }
        val rows = Rows(mutableListOf(row("p1")))
        val run = MediaUploader(rows, fake, null).run()
        assertEquals(1, run.failed)
        assertEquals("failed", rows.items[0].uploadState)
        fake.failWith = null
        MediaUploader(rows, fake, null).run()
        assertEquals("done", rows.items[0].uploadState)
    }

    @Test fun consentNeededStopsAndKeepsPending() = runBlocking {
        val fake = drive().apply { failWith = DriveException.NeedsConsent() }
        val rows = Rows(mutableListOf(row("p1"), row("p2")))
        val run = MediaUploader(rows, fake, null).run()
        assertTrue(run.needsConsent)
        assertTrue(rows.items.all { it.uploadState == "pending" })
    }

    @Test fun rowsFromAnotherDeviceAreSkippedAndThumbErrorsIgnored() = runBlocking {
        val rows = Rows(mutableListOf(row("far", write = false), row("p1")))
        val run = MediaUploader(rows, drive(), Thumbs(fail = true)).run()
        assertEquals(1, run.done)
        assertEquals("pending", rows.items[0].uploadState)
        assertEquals("done", rows.items[1].uploadState)
    }

    @Test fun fetcherDownloadsOnlyWhenLocalFileIsMissing() = runBlocking {
        val fake = drive()
        val uploaded = fake.upload(fake.folders().photos, "x.webp", "image/webp", "remote".toByteArray())
        val cache = File(tmp.root, "cache.webp")
        val fetcher = MediaFetcher({ fake }, null, { cache }, { File(tmp.root, "t.webp") })
        val missing = JournalMediaEntity("x", "e", "photo", File(tmp.root, "nope.webp").path, driveFileId = uploaded)
        assertEquals("remote", fetcher.file(missing)!!.readText())
        val local = row("p1")
        assertEquals(local.localPath, fetcher.file(local)!!.path)
        assertNull(fetcher.file(missing.copy(driveFileId = null, id = "y").also { cache.delete() }))
        assertNull(MediaFetcher({ null }, null, { cache }, { cache }).file(missing))
        assertNotNull(fetcher.thumb(local))
    }

    @Test fun qualityMapping() {
        assertEquals(80, PhotoQuality.webp("balanced"))
        assertEquals(90, PhotoQuality.webp("high"))
        assertEquals(80, PhotoQuality.webp("???"))
        assertEquals(UNMETERED, MediaUploadScheduler.networkFor(true).name)
        assertEquals("CONNECTED", MediaUploadScheduler.networkFor(false).name)
    }

    private companion object {
        const val UNMETERED = "UNMETERED"
    }
}
