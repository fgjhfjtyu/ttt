package com.opensstp.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import vn.unlimit.softether.client.SoftEtherClient
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Real SoftEther protocol session + Android TUN bridge.
 * The external native module is SoftEther-Android-Module (not the legacy SSTP code under reference/).
 * Connection state is reported only after SoftEther authentication, socket protection, DHCP and
 * VpnService.Builder.establish() have all succeeded.
 */
class SoftEtherVpnService : VpnService() {
    @Volatile private var running = false
    @Volatile private var connected = false
    @Volatile private var nativeClient: SoftEtherClient? = null
    @Volatile private var nativeHandle: Long = 0L
    @Volatile private var tunDescriptor: ParcelFileDescriptor? = null
    @Volatile private var cpuWakeLock: PowerManager.WakeLock? = null
    private val sessionExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "opensstp-softether-session").apply { isDaemon = true }
    }

    override fun onCreate() {
        super.onCreate()
        Lang.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Lang.init(this)
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (running) {
                    publishStatus(Lang.t("svc_in_progress"), STATE_CONNECTING)
                    return START_NOT_STICKY
                }
                val rawServer = intent.getStringExtra(EXTRA_SERVER).orEmpty()
                val username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
                val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
                val target = try {
                    parseServer(rawServer)
                } catch (e: IllegalArgumentException) {
                    publishStatus(e.message ?: Lang.t("srv_port_bad"), STATE_DISCONNECTED)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                running = true
                startAsForeground(Lang.t("connecting"))
                publishStatus(Lang.t("svc_connecting_to", "${target.host}:${target.port}"), STATE_CONNECTING)
                sessionExecutor.execute { runSession(target.host, target.port, username, password) }
            }
            ACTION_DISCONNECT -> {
                requestDisconnect()
                if (nativeHandle == 0L) stopSelf(startId)
            }
            ACTION_QUERY -> {
                val state = when { connected -> STATE_CONNECTED; running || nativeHandle != 0L -> STATE_CONNECTING; else -> STATE_DISCONNECTED }
                publishStatus(when { connected -> Lang.t("connected"); running -> Lang.t("connecting"); nativeHandle != 0L -> Lang.t("disconnecting"); else -> Lang.t("disconnected") }, state)
                if (!running && nativeHandle == 0L) stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun runSession(host: String, port: Int, username: String, password: String) {
        var client: SoftEtherClient? = null
        var handle = 0L
        var tun: ParcelFileDescriptor? = null
        var tunInput: FileInputStream? = null
        var tunOutput: FileOutputStream? = null
        var failureMessage: String? = null
        val loopFailure = AtomicReference<String?>(null)
        var inboundThread: Thread? = null
        var outboundThread: Thread? = null
        var statsThread: Thread? = null
        // Byte counters measured at the packet bridge itself (independent of native stats).
        val rxBytes = AtomicLong(0L)
        val txBytes = AtomicLong(0L)
        // Diagnostics: shows in logcat (tag OpenSSTP-VpnService) every 10 s to locate CPU use.
        val rxCalls = AtomicLong(0L)
        val rxEmpty = AtomicLong(0L)
        val rxPackets = AtomicLong(0L)
        val txPackets = AtomicLong(0L)

        try {
            client = SoftEtherClient()
            handle = client.nativeCreate()
            if (handle == 0L) throw IOException(Lang.t("err_engine_create"))
            nativeClient = client
            nativeHandle = handle

            // A single TCP session avoids unprotected extra native sockets being added after TUN starts.
            client.nativeSetMaxConnection(handle, 1)
            client.nativeSetOption(handle, SoftEtherClient.OPTION_TIMEOUT, 20_000L)
            client.nativeSetAuthType(handle, if (password.isBlank()) AUTH_AUTO else AUTH_PASSWORD)

            val connectResult = client.nativeConnectWithHub(
                handle = handle,
                host = host,
                port = port,
                username = username,
                password = password,
                hubName = HUB_NAME,
                useTcp = true,
                clientProductName = "OpenSSTP Android",
                clientVersion = APP_VERSION,
                clientBuild = 1,
                clientOsName = "Android",
                clientOsVersion = Build.VERSION.RELEASE.orEmpty(),
                clientOsProductId = Build.DEVICE.orEmpty(),
                clientHostName = Build.MODEL.orEmpty().take(120),
                clientIpAddress = "",
                clientPort = 0,
                serverHostName = host,
                serverIpAddress = "",
                serverPort = port
            )
            if (!running) throw InterruptedException(Lang.t("svc_cancelled"))
            if (connectResult != 0 || client.nativeGetState(handle) != NATIVE_STATE_CONNECTED) {
                throw IOException(Lang.t("err_connect_failed", connectResult))
            }

            // Protect every socket before adding the default routes to the Android VPN interface.
            protectNativeSockets(client, handle)
            publishStatus(Lang.t("svc_getting_ip"), STATE_CONNECTING)

            val lease = client.doDhcp(handle)
                ?: throw IOException(Lang.t("err_no_dhcp"))
            if (!isUsableIpv4(lease.assignedIp) || lease.prefixLength !in 1..32) {
                throw IOException(Lang.t("err_bad_ip"))
            }
            if (!running) throw InterruptedException(Lang.t("svc_cancelled"))

            val builder = Builder()
                .setSession("OpenSSTP — SoftEther VPN")
                .setMtu(TUN_MTU)
                .addAddress(lease.assignedIp, lease.prefixLength)
                .addRoute("0.0.0.0", 0)
                // Capture IPv6 too, so it cannot silently bypass the VPN on the physical network.
                // Real IPv6 Internet access depends on the remote SoftEther hub/server routing IPv6.
                .addAddress(IPV6_TUN_ADDRESS, 128)
                .addRoute("::", 0)
                .setConfigureIntent(PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

            listOf(lease.dnsServer, lease.dnsServer2)
                .filter { isUsableIpv4(it) }
                .distinct()
                .forEach { builder.addDnsServer(it) }
            if (!isUsableIpv4(lease.dnsServer) && !isUsableIpv4(lease.dnsServer2)) {
                // Both DNS requests still traverse the configured VPN default route.
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("8.8.8.8")
            }

            val establishedTun = builder.establish() ?: throw IOException(Lang.t("err_tun"))
            tun = establishedTun
            tunDescriptor = establishedTun
            val inputStream = FileInputStream(establishedTun.fileDescriptor)
            val outputStream = FileOutputStream(establishedTun.fileDescriptor)
            tunInput = inputStream
            tunOutput = outputStream
            connected = true
            publishStatus(Lang.t("svc_connected_to", "${host}:${port}"), STATE_CONNECTED)
            val sessionClient = client ?: throw IOException(Lang.t("err_engine_gone"))
            val sessionHandle = handle

            // The native protocol uses blocking socket reads; keep the CPU awake while this
            // user-requested VPN session is active, especially with the screen off.
            val powerManager = getSystemService(PowerManager::class.java)
            statsThread = Thread({
                var previousTx = 0L
                var previousRx = 0L
                var previousTime = SystemClock.elapsedRealtime()
                var ticks = 0
                while (running && connected) {
                    try { Thread.sleep(STATS_INTERVAL_MS) } catch (_: InterruptedException) { break }
                    if (!running || !connected) break
                    if (++ticks % 60 == 0) {
                        Log.i(TAG, "diag/60s total: rxCalls=${rxCalls.get()} rxEmpty=${rxEmpty.get()} rxPkts=${rxPackets.get()} txPkts=${txPackets.get()}")
                    }
                    // Nobody sees the speed meter while the screen is off; skip the work.
                    if (!uiVisible || powerManager?.isInteractive == false) {
                        previousTx = txBytes.get(); previousRx = rxBytes.get(); previousTime = SystemClock.elapsedRealtime()
                        continue
                    }
                    try {
                        val now = SystemClock.elapsedRealtime()
                        val elapsedMs = (now - previousTime).coerceAtLeast(1L)
                        val currentTx = txBytes.get()
                        val currentRx = rxBytes.get()
                        val txRate = (currentTx - previousTx).coerceAtLeast(0L) * 1000L / elapsedMs
                        val rxRate = (currentRx - previousRx).coerceAtLeast(0L) * 1000L / elapsedMs
                        previousTx = currentTx
                        previousRx = currentRx
                        previousTime = now
                        publishTrafficRates(rxRate, txRate)
                    } catch (e: Exception) {
                        Log.w(TAG, "Traffic statistics unavailable", e)
                    }
                }
            }, "opensstp-softether-stats").apply { isDaemon = true }

            val input = inputStream
            val output = outputStream
            inboundThread = Thread({
                try {
                    // Batch receive: one JNI call returns up to RX_BATCH_PACKETS IP packets
                    // (Ethernet header already stripped natively); each is written to the TUN
                    // as its own packet. Far fewer JNI crossings and wakeups under load.
                    val buffer = ByteArray(RX_BATCH_BUFFER_SIZE)
                    val lengths = IntArray(RX_BATCH_PACKETS)
                    var idleSleepMs = RECEIVE_IDLE_SLEEP_MIN_MS
                    while (running) {
                        val startedAt = SystemClock.elapsedRealtime()
                        val count = sessionClient.nativeReceiveBatch(sessionHandle, buffer, buffer.size, lengths, lengths.size)
                        rxCalls.incrementAndGet()
                        when {
                            count > 0 -> {
                                var offset = 0
                                for (len in lengths) {
                                    if (len <= 0) break
                                    output.write(buffer, offset, len)
                                    offset += len
                                    rxPackets.incrementAndGet()
                                }
                                rxBytes.addAndGet(count.toLong())
                                idleSleepMs = RECEIVE_IDLE_SLEEP_MIN_MS
                            }
                            count == 0 -> {
                                rxEmpty.incrementAndGet()
                                // The native side already waits (poll) up to ~100 ms when idle, so no
                                // extra sleep is needed then. Back off only when it returned instantly
                                // with nothing usable, which would otherwise spin the CPU.
                                val waited = SystemClock.elapsedRealtime() - startedAt
                                if (waited >= NATIVE_BLOCKED_MS) {
                                    idleSleepMs = RECEIVE_IDLE_SLEEP_MIN_MS
                                } else {
                                    Thread.sleep(idleSleepMs)
                                    idleSleepMs = (idleSleepMs * 2).coerceAtMost(RECEIVE_IDLE_SLEEP_MAX_MS)
                                }
                            }
                            else -> throw IOException(Lang.t("err_link_lost"))
                        }
                    }
                } catch (e: Exception) {
                    if (running) failPacketLoop(Lang.t("err_rx", e.message ?: ""), loopFailure)
                }
            }, "opensstp-softether-rx").apply { isDaemon = true }

            outboundThread = Thread({
                try {
                    val buffer = ByteArray(PACKET_BUFFER_SIZE)
                    while (running) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) { Thread.sleep(1L); continue }
                        val sent = sessionClient.nativeSendSlice(sessionHandle, buffer, 0, count)
                        if (sent != count) throw IOException(Lang.t("err_tx_send", sent, count))
                        txBytes.addAndGet(count.toLong())
                        txPackets.incrementAndGet()
                    }
                    if (running) throw IOException(Lang.t("err_tun_closed"))
                } catch (e: Exception) {
                    if (running) failPacketLoop(Lang.t("err_tx", e.message ?: ""), loopFailure)
                }
            }, "opensstp-softether-tx").apply { isDaemon = true }

            inboundThread.start()
            outboundThread.start()
            statsThread?.start()
            inboundThread.join()
            outboundThread.join()
            loopFailure.get()?.let { throw IOException(it) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            if (running) failureMessage = Lang.t("err_stopped")
        } catch (e: Exception) {
            if (running || loopFailure.get() != null) {
                failureMessage = loopFailure.get() ?: (e.message ?: Lang.t("err_vpn_failed"))
            }
            Log.e(TAG, "SoftEther session ended", e)
        } finally {
            running = false
            connected = false
            statsThread?.interrupt()
            try { statsThread?.join(250L) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            try { tunInput?.close() } catch (_: Exception) { }
            try { tunOutput?.close() } catch (_: Exception) { }
            try { tun?.close() } catch (_: Exception) { }
            if (tunDescriptor === tun) tunDescriptor = null

            if (handle != 0L && client != null) {
                try { client.nativeDisconnect(handle) } catch (e: Exception) { Log.w(TAG, "nativeDisconnect", e) }
                try { client.nativeDestroy(handle) } catch (e: Exception) { Log.w(TAG, "nativeDestroy", e) }
            }
            nativeHandle = 0L
            nativeClient = null
            releaseCpuWakeLock()

            val finalMessage = failureMessage ?: Lang.t("disconnected")
            publishStatus(finalMessage, if (failureMessage == null) STATE_DISCONNECTED else STATE_ERROR)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun protectNativeSockets(client: SoftEtherClient, handle: Long) {
        val descriptors = linkedSetOf<Int>()
        client.nativeGetSocketFd(handle).takeIf { it >= 0 }?.let(descriptors::add)
        client.nativeGetAllSocketFds(handle)?.filter { it >= 0 }?.forEach(descriptors::add)
        client.nativeGetRudpSocketFd(handle).takeIf { it >= 0 }?.let(descriptors::add)
        client.nativeGetNatTUdpSocketFd(handle).takeIf { it >= 0 }?.let(descriptors::add)
        if (descriptors.isEmpty()) throw IOException(Lang.t("err_no_socket"))
        descriptors.forEach { fd ->
            if (!protect(fd)) throw IOException(Lang.t("err_protect", fd))
        }
    }

    private fun failPacketLoop(message: String, failure: AtomicReference<String?>) {
        failure.compareAndSet(null, message)
        running = false
        try { tunDescriptor?.close() } catch (_: Exception) { }
        val client = nativeClient
        val handle = nativeHandle
        if (client != null && handle != 0L) {
            try { client.nativeForceCloseSocket(handle) } catch (_: Exception) { }
        }
    }

    private fun requestDisconnect() {
        if (!running && nativeHandle == 0L) {
            publishStatus(Lang.t("disconnected"), STATE_DISCONNECTED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        running = false
        publishStatus(Lang.t("disconnecting"), STATE_DISCONNECTED)
        try { tunDescriptor?.close() } catch (_: Exception) { }
        val client = nativeClient
        val handle = nativeHandle
        if (client != null && handle != 0L) {
            // Interrupt a blocked native connect/read. The session worker owns final destruction.
            try { client.nativeForceCloseSocket(handle) } catch (e: Exception) { Log.w(TAG, "force close", e) }
        }
    }

    private fun acquireCpuWakeLock() {
        if (cpuWakeLock?.isHeld == true) return
        val manager = getSystemService(PowerManager::class.java)
        cpuWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:VpnSession").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseCpuWakeLock() {
        val lock = cpuWakeLock
        cpuWakeLock = null
        try { if (lock?.isHeld == true) lock.release() } catch (e: Exception) {
            Log.w(TAG, "Unable to release CPU wake lock", e)
        }
    }

    private fun publishTrafficRates(rxBytesPerSecond: Long, txBytesPerSecond: Long) {
        val broadcast = Intent(ACTION_STATUS)
            .setPackage(packageName)
            .putExtra(EXTRA_STATE, STATE_CONNECTED)
            .putExtra(EXTRA_RX_RATE, formatRate(rxBytesPerSecond))
            .putExtra(EXTRA_TX_RATE, formatRate(txBytesPerSecond))
            .putExtra(EXTRA_RX_BPS, rxBytesPerSecond)
            .putExtra(EXTRA_TX_BPS, txBytesPerSecond)
        sendBroadcast(broadcast)
    }

    private fun formatRate(bytesPerSecond: Long): String {
        val safeRate = bytesPerSecond.coerceAtLeast(0L)
        return when {
            safeRate < 1024L -> "$safeRate B/s"
            safeRate < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB/s", safeRate / 1024.0)
            else -> String.format(java.util.Locale.US, "%.2f MB/s", safeRate / 1048576.0)
        }
    }

    private fun startAsForeground(text: String) {
        acquireCpuWakeLock()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "OpenSSTP VPN", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("OpenSSTP VPN")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(running)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun publishStatus(message: String, state: String) {
        Log.i(TAG, "$state: $message")
        // Wall-clock start of the current session, used by the UI for the connection timer.
        if (state == STATE_CONNECTED) {
            if (connectedSinceMs == 0L) connectedSinceMs = System.currentTimeMillis()
        } else if (state == STATE_DISCONNECTED || state == STATE_ERROR) {
            connectedSinceMs = 0L
        }
        val broadcast = Intent(ACTION_STATUS)
            .setPackage(packageName)
            .putExtra(EXTRA_STATUS, message)
            .putExtra(EXTRA_STATE, state)
        sendBroadcast(broadcast)
        if (running) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(message))
        }
    }

    override fun onRevoke() {
        requestDisconnect()
        if (nativeHandle == 0L) stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        requestDisconnect()
        releaseCpuWakeLock()
        sessionExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private data class ServerAddress(val host: String, val port: Int)

    private fun parseServer(raw: String): ServerAddress {
        val value = raw.trim()
        require(value.isNotEmpty()) { Lang.t("srv_empty") }
        require(!value.contains("://")) { Lang.t("srv_scheme") }

        val host: String
        val port: Int
        if (value.startsWith("[")) {
            val end = value.indexOf(']')
            require(end > 1) { Lang.t("srv_ipv6") }
            host = value.substring(1, end)
            val suffix = value.substring(end + 1)
            port = if (suffix.isEmpty()) DEFAULT_PORT else {
                require(suffix.startsWith(":")) { Lang.t("srv_port_bad") }
                suffix.drop(1).toIntOrNull() ?: throw IllegalArgumentException(Lang.t("srv_port_num"))
            }
        } else {
            val lastColon = value.lastIndexOf(':')
            if (lastColon > 0 && value.indexOf(':') == lastColon) {
                host = value.substring(0, lastColon)
                port = value.substring(lastColon + 1).toIntOrNull()
                    ?: throw IllegalArgumentException(Lang.t("srv_port_num"))
            } else {
                host = value
                port = DEFAULT_PORT
            }
        }
        require(host.isNotBlank()) { Lang.t("srv_host_empty") }
        require(port in 1..65535) { Lang.t("srv_port_range") }
        return ServerAddress(host, port)
    }

    private fun isUsableIpv4(address: String): Boolean {
        val octets = address.split('.')
        return octets.size == 4 && octets.all { part ->
            part.isNotEmpty() && part.all { it in '0'..'9' } && part.toIntOrNull()?.let { it in 0..255 } == true
        } && address != "0.0.0.0"
    }

    companion object {
        private const val TAG = "OpenSSTP-VpnService"
        private const val CHANNEL_ID = "opensstp_vpn_channel"
        private const val NOTIFICATION_ID = 4017
        private const val DEFAULT_PORT = 443
        private const val HUB_NAME = "VPNGATE"
        private const val APP_VERSION = "0.2.0"
        private const val AUTH_AUTO = 0
        private const val AUTH_PASSWORD = 1
        private const val NATIVE_STATE_CONNECTED = 6
        private const val TUN_MTU = 1400
        private const val IPV6_TUN_ADDRESS = "fd42:6f70:656e:5353::2"
        private const val PACKET_BUFFER_SIZE = 32768
        private const val RX_BATCH_BUFFER_SIZE = 65536
        private const val RX_BATCH_PACKETS = 32
        private const val RECEIVE_IDLE_SLEEP_MIN_MS = 1L
        private const val RECEIVE_IDLE_SLEEP_MAX_MS = 5L
        private const val STATS_INTERVAL_MS = 1000L
        private const val NATIVE_BLOCKED_MS = 20L

        const val ACTION_CONNECT = "com.opensstp.android.CONNECT"
        const val ACTION_DISCONNECT = "com.opensstp.android.DISCONNECT"
        const val ACTION_QUERY = "com.opensstp.android.QUERY"
        const val ACTION_STATUS = "com.opensstp.android.STATUS"
        const val EXTRA_SERVER = "server"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_STATUS = "status"
        const val EXTRA_STATE = "state"
        const val EXTRA_RX_RATE = "rx_rate"
        const val EXTRA_TX_RATE = "tx_rate"
        const val EXTRA_RX_BPS = "rx_bps"
        const val EXTRA_TX_BPS = "tx_bps"

        /** Epoch millis when the current tunnel became connected, or 0 when not connected. */
        @Volatile
        var connectedSinceMs: Long = 0L

        /** True only while MainActivity is on screen; the speed meter is skipped otherwise. */
        @Volatile
        var uiVisible: Boolean = false
        const val STATE_CONNECTING = "connecting"
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"
        const val STATE_ERROR = "error"
    }
}
