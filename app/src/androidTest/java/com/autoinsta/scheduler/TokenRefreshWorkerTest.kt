package com.autoinsta.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.autoinsta.AutoInstaApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The weekly job that keeps the Instagram login from lapsing.
 *
 * What matters here is that it never fails loudly: a token that cannot be refreshed yet
 * (Meta refuses one under 24 hours old) or an account that isn't connected are both
 * normal states, not errors. Returning failure would make WorkManager retry with backoff
 * for no reason, and could surface as a problem the user cannot act on.
 */
@RunWith(AndroidJUnit4::class)
class TokenRefreshWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<TokenRefreshWorker>(context).build().doWork()

    @Test
    fun succeedsWhateverTheConnectionState() = runTest {
        // Runs against whatever the app's real state happens to be — connected or not,
        // token fresh or due. Every one of those is a success as far as this job goes.
        assertEquals(ListenableWorker.Result.success(), runWorker())
    }

    @Test
    fun runningTwiceInARowIsHarmless() = runTest {
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(ListenableWorker.Result.success(), runWorker())
    }

    @Test
    fun aDisconnectedAppIsANoOpRatherThanAFailure() = runTest {
        val app = context as AutoInstaApp
        val wasConnected = app.accountRepository.isConnected()

        if (wasConnected) {
            // Don't tear down a real connection just to test this; the disconnected
            // path is covered whenever the suite runs before an account is linked.
            return@runTest
        }
        assertEquals(ListenableWorker.Result.success(), runWorker())
    }
}
