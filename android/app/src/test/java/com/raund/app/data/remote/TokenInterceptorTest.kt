package com.raund.app.data.remote

import com.raund.app.data.local.TokenStore
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TokenInterceptorTest {

    private val tokenStore = mockk<TokenStore>()
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(TokenInterceptor(tokenStore))
            .build()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `adds Authorization header when token is present`() {
        every { tokenStore.getToken() } returns "test_token_123"
        server.enqueue(MockResponse().setBody("ok"))

        client.newCall(Request.Builder().url(server.url("/test")).build()).execute()

        val recorded = server.takeRequest()
        assertEquals("Bearer test_token_123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `does not add Authorization header when token is null`() {
        every { tokenStore.getToken() } returns null
        server.enqueue(MockResponse().setBody("ok"))

        client.newCall(Request.Builder().url(server.url("/test")).build()).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }
}
