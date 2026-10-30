package com.ravi.omnichat

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** P1-1: constructing the ViewModel must not load chats synchronously; they arrive async with chatsLoading clearing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
class ChatViewModelLoadingTest {
    @Test fun chatsLoadAsynchronously() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ChatViewModel(app)
        // viewModelScope runs on the (paused, under Robolectric) main looper, so the
        // constructor cannot have finished the load synchronously.
        assertTrue("load must not complete synchronously in the constructor", vm.chatsLoading)
        // Pump the main looper while the IO load finishes on its real thread.
        val deadlineMs = System.currentTimeMillis() + 10_000
        while (vm.chatsLoading) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadlineMs) fail("chatsLoading never cleared")
            Thread.sleep(50)
        }
        assertTrue(!vm.chatsLoading)
    }
}
