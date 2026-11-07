package com.opensstp.android

import android.content.Context

/** One public server from the VPN Gate project (https://www.vpngate.net). */
data class VpnGateServer(
    val host: String,
    val ip: String,
    val score: Long,
    val pingMs: Int,
    val speedBps: Long,
    val countryName: String,
    val countryCode: String,
    val sessions: Int,
    val uptimeMs: Long
) {
    /** What the connection form should contain. VPN Gate hostnames live under opengw.net. */
    val address: String get() = if (host.isNotBlank()) "$host.opengw.net" else ip
    val speedMbps: Double get() = speedBps / 1_000_000.0
}

object VpnGate {
    const val DEFAULT_URL = "https://www.vpngate.net/api/iphone/"
    private const val PREFS = "vpngate"
    private const val KEY_URL = "source_url"
    private const val KEY_CACHE = "cache"
    private const val KEY_CACHE_TIME = "cache_time"

    /**
     * Parses the VPN Gate CSV. Columns:
     * HostName, IP, Score, Ping, Speed, CountryLong, CountryShort, NumVpnSessions, Uptime, ...
     * Lines starting with '*' or '#' are markers/header. The big OpenVPN config column is ignored.
     */
    fun parse(csv: String): List<VpnGateServer> {
        val out = ArrayList<VpnGateServer>()
        for (raw in csv.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("#")) continue
            val f = line.split(',', limit = 10)
            if (f.size < 9) continue
            val ip = f[1].trim()
            val cc = f[6].trim().uppercase()
            if (ip.isEmpty() || cc.length != 2) continue
            out.add(
                VpnGateServer(
                    host = f[0].trim(),
                    ip = ip,
                    score = f[2].trim().toLongOrNull() ?: 0L,
                    pingMs = f[3].trim().toIntOrNull() ?: -1,
                    speedBps = f[4].trim().toLongOrNull() ?: 0L,
                    countryName = f[5].trim(),
                    countryCode = cc,
                    sessions = f[7].trim().toIntOrNull() ?: 0,
                    uptimeMs = f[8].trim().toLongOrNull() ?: 0L
                )
            )
        }
        return out
    }

    fun sourceUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, null)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_URL

    fun setSourceUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url.trim()).apply()
    }

    /** Downloads and parses the list from the configured source. Blocking. Throws on failure. */
    fun fetch(context: Context): List<VpnGateServer> {
        val url = sourceUrl(context)
        if (!url.startsWith("https://")) throw java.io.IOException("آدرس منبع باید با https:// شروع شود")
        val servers = parse(Net.get(url, timeoutMs = 15000))
        if (servers.isEmpty()) throw java.io.IOException("فهرست خالی بود")
        saveCache(context, servers)
        return servers
    }

    /** Servers pasted by the user (the raw CSV text copied from the VPN Gate site). */
    fun importCsv(context: Context, csv: String): List<VpnGateServer> {
        val servers = parse(csv)
        if (servers.isNotEmpty()) saveCache(context, servers)
        return servers
    }

    /** Last successful list (public data, kept without the heavy OpenVPN column). */
    fun cached(context: Context): Pair<List<VpnGateServer>, Long>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = p.getString(KEY_CACHE, null) ?: return null
        val list = parse(text)
        return if (list.isEmpty()) null else list to p.getLong(KEY_CACHE_TIME, 0L)
    }

    private fun saveCache(context: Context, servers: List<VpnGateServer>) {
        val text = servers.joinToString("\n") {
            listOf(it.host, it.ip, it.score, it.pingMs, it.speedBps, it.countryName, it.countryCode, it.sessions, it.uptimeMs)
                .joinToString(",")
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CACHE, text)
            .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
            .apply()
    }
}
