package com.yash.standbytv

import com.yash.standbytv.gphotos.SetupServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** The phone/PC setup page: serves the form, rejects wrong paths, saves a valid client. */
class SetupServerTest {
    private fun req(url: String, body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        if (body != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return code to text
    }

    @Test fun formAndSave() {
        var saved: Pair<String, String>? = null
        val srv = SetupServer({ "127.0.0.1" }) { id, s -> saved = id to s }
        val url = srv.start()!!
        try {
            val local = url
            assertTrue(req(local).second.contains("Client ID"))
            assertEquals(404, req(local.substringBeforeLast("/") + "/wrong").first)
            assertTrue(req(local, "client_id=bad&client_secret=x").second.contains("doesn't look like"))
            val ok = req(local, "client_id=123-abc.apps.googleusercontent.com&client_secret=GOCSPX-abcdefghij")
            assertTrue(ok.second.contains("Saved"))
            assertEquals("123-abc.apps.googleusercontent.com" to "GOCSPX-abcdefghij", saved)
        } finally { srv.stop() }
    }
}
