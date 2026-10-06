package app.cove.companion.data

import app.cove.companion.data.auth.AuthRepository
import app.cove.companion.data.auth.AuthState
import app.cove.companion.data.auth.Session
import app.cove.companion.data.auth.SessionStore
import app.cove.companion.data.sync.SyncStatus
import app.cove.companion.feature.me.syncUi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {
    private class Mem(var s: Session? = null) : SessionStore {
        override fun load() = s
        override fun save(session: Session?) { s = session }
    }

    private val tokenJson = """{"access_token":"A2","refresh_token":"R2","expires_in":3600,"user":{"id":"u1","email":"a@b.c"}}"""
    private val now = 1_000_000L
    private val urls = ArrayList<String>()

    private fun repo(store: Mem, status: HttpStatusCode = HttpStatusCode.OK, url: String = "https://x.supabase.co") = AuthRepository(
        url, if (url.isBlank()) "" else "anon", store,
        HttpClient(MockEngine { req ->
            urls += req.url.toString()
            respond(if (status == HttpStatusCode.OK) tokenJson else "{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        now = { now },
    )

    @Test fun blankConfigDisablesAuth() {
        assertEquals(AuthState.Disabled, repo(Mem(), url = "").state.value)
    }

    @Test fun googleIdTokenExchangeStoresSession() = runBlocking {
        val store = Mem(); val r = repo(store)
        assertTrue(r.signInWithGoogle("idtok", "n"))
        assertTrue(urls.single().contains("grant_type=id_token"))
        assertEquals(AuthState.SignedIn("u1", "a@b.c"), r.state.value)
        assertEquals(now + 3_600_000, store.s!!.expiresAtMs)
    }

    @Test fun freshTokenIsReturnedWithoutRefresh() = runBlocking {
        val r = repo(Mem(Session("A1", "R1", now + 600_000, "u1")))
        assertEquals("A1", r.accessToken())
        assertTrue(urls.isEmpty())
    }

    @Test fun expiringTokenIsRefreshed() = runBlocking {
        val store = Mem(Session("A1", "R1", now + 10_000, "u1"))
        assertEquals("A2", repo(store).accessToken())
        assertTrue(urls.single().contains("grant_type=refresh_token"))
        assertEquals("R2", store.s!!.refreshToken)
    }

    @Test fun rejectedRefreshSignsOut() = runBlocking {
        val store = Mem(Session("A1", "R1", now - 1, "u1"))
        val r = repo(store, HttpStatusCode.BadRequest)
        assertNull(r.accessToken())
        assertNull(store.s)
        assertEquals(AuthState.SignedOut, r.state.value)
    }

    @Test fun serverErrorKeepsSessionButNotAnExpiredToken() = runBlocking {
        val store = Mem(Session("A1", "R1", now - 1, "u1"))
        assertNull(repo(store, HttpStatusCode.InternalServerError).accessToken())
        assertEquals("R1", store.s!!.refreshToken)
    }

    @Test fun meRowLabels() {
        val inn = AuthState.SignedIn("u", null)
        assertEquals("Not signed in", syncUi(AuthState.SignedOut, SyncStatus.Idle, 0, 0).label)
        assertEquals("Syncing", syncUi(inn, SyncStatus.Syncing, 0, 0).label)
        assertEquals("Up to date · 2 min ago", syncUi(inn, SyncStatus.UpToDate(0), 0, 120_000).label)
        assertEquals("Paused", syncUi(inn, SyncStatus.Failed("x"), 0, 0).label)
        assertEquals("1 to review", syncUi(inn, SyncStatus.Syncing, 1, 0).label)
    }
}
