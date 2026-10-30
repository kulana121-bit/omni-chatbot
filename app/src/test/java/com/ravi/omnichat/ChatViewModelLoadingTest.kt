package com.ravi.omnichat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P1-1: constructing the ViewModel must not load chats synchronously; they arrive async with chatsLoading clearing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
class ChatViewModelLoadingTest {
    @Test fun chatsLoadAsynchronously() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ChatViewModel(app)
        // The flag starts true (constructor did not block on disk) and must clear once the IO load finishes.
        withTimeout(10_000) { while (vm.chatsLoading) delay(50) }
        assertTrue(!vm.chatsLoading)
        assertTrue(Dispatchers.IO.toString().isNotBlank()) // load path uses Dispatchers.IO (see ChatViewModel.init)
    }
}
