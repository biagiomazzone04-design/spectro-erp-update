package com.spectro.app.auth

import org.junit.Assert.assertTrue
import org.junit.Test

class AuthMessagesTest {
    @Test
    fun emailNotConfirmedExplainsHowToRecover() {
        val message = localizedAuthError("Email not confirmed", 400)
        assertTrue(message.contains("non ancora confermata"))
        assertTrue(message.contains("nuovo"))
    }

    @Test
    fun rateLimitDoesNotInviteRepeatedRequests() {
        val message = localizedAuthError("Email rate limit exceeded", 429)
        assertTrue(message.contains("Attendi"))
    }

    @Test
    fun unknownServerMessageIsPreserved() {
        val message = localizedAuthError("Custom backend error", 400)
        assertTrue(message == "Custom backend error")
    }
}
