package com.opensstp.android

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** Minimal HTTPS GET helper (blocking; call from a background thread). */
object Net {
    fun get(url: String, timeoutMs: Int = 8000, maxBytes: Int = 8 * 1024 * 1024): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Open-SSTP-Android")
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw java.io.IOException("Response too large")
                }
                out.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Finds the country of a server from its IP address, so the app can show the right flag
 * automatically when the user types a server by hand.
 *
 * The hostname is resolved to an IP and the IP is looked up with free HTTPS GeoIP services
 * (several are tried in order). The result is a two-letter ISO country code such as "DE" or "JP".
 */
object GeoLookup {
    private val CODE = Regex("^[A-Za-z]{2}$")

    private fun clean(code: String?): String? =
        code?.trim()?.takeIf { CODE.matches(it) }?.uppercase()?.takeIf { it != "XX" }

    /** Blocking. Returns an uppercase ISO 3166-1 alpha-2 code, or null if it can't be determined. */
    fun countryOf(context: Context, rawServer: String): String? {
        val (host, _) = PingProbe.parse(rawServer) ?: return null
        val ip = resolveIp(context, host) ?: return null

        val lookups: List<() -> String?> = listOf(
            { clean(JSONObject(Net.get("https://api.country.is/$ip", 6000)).optString("country")) },
            { clean(JSONObject(Net.get("https://ipwho.is/$ip?fields=success,country_code", 6000)).optString("country_code")) },
            { clean(Net.get("https://ipapi.co/$ip/country/", 6000)) }
        )
        for (lookup in lookups) {
            try {
                lookup()?.let { return it }
            } catch (_: Exception) {
                // try the next service
            }
        }
        return null
    }

    private fun resolveIp(context: Context, host: String): String? = try {
        val network = PingProbe.underlyingNetwork(context)
        val addresses: Array<InetAddress> = network?.getAllByName(host) ?: InetAddress.getAllByName(host)
        // Prefer IPv4: it is what the GeoIP services handle best.
        (addresses.firstOrNull { it.address.size == 4 } ?: addresses.firstOrNull())?.hostAddress
    } catch (_: Exception) {
        null
    }
}
