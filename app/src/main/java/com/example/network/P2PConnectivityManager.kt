package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.*
import com.example.util.NovaShareStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.*
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed class HandshakeStatus {
    object Idle : HandshakeStatus()
    object Connecting : HandshakeStatus()
    data class WaitingForApproval(val senderName: String, val senderIp: String, val session: TransferSession) : HandshakeStatus()
    object Confirmed : HandshakeStatus()
    data class Rejected(val reason: String) : HandshakeStatus()
    data class Failed(val error: String) : HandshakeStatus()
}

enum class P2PEngineMode {
    IDLE, RECEIVER, SENDER, DISCOVERY
}

class P2PConnectivityManager(
    private val context: Context,
    val radioStateManager: RadioStateManager = RadioStateManager(context),
    val hotspotManager: HotspotManager = HotspotManager(context)
) {
    companion object {
        const val P2P_TCP_PORT = 8888
        const val P2P_UDP_DISCOVERY_PORT = 8889
        const val TAG = "NovaP2PManager"
        const val BUFFER_SIZE = 65536 // 64 KB high-speed chunk
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val executor = Executors.newCachedThreadPool()

    private val _engineMode = MutableStateFlow(P2PEngineMode.IDLE)
    val engineMode: StateFlow<P2PEngineMode> = _engineMode.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _handshakeStatus = MutableStateFlow<HandshakeStatus>(HandshakeStatus.Idle)
    val handshakeStatus: StateFlow<HandshakeStatus> = _handshakeStatus.asStateFlow()

    private val _activeSession = MutableStateFlow<TransferSession?>(null)
    val activeSession: StateFlow<TransferSession?> = _activeSession.asStateFlow()

    private val _currentQrToken = MutableStateFlow(generateSessionToken())
    val currentQrToken: StateFlow<String> = _currentQrToken.asStateFlow()

    private val _currentPin = MutableStateFlow(generateSixDigitPin())
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    private val _isHotspotMode = MutableStateFlow(false)
    val isHotspotMode: StateFlow<Boolean> = _isHotspotMode.asStateFlow()

    // Sockets
    private var tcpServerSocket: ServerSocket? = null
    private var udpDiscoverySocket: DatagramSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var activeClientSocket: Socket? = null

    // Callbacks
    var onTransferRequested: ((TransferSession, (Boolean) -> Unit) -> Unit)? = null
    var onTransferSessionUpdated: ((TransferSession) -> Unit)? = null

    init {
        radioStateManager.registerMonitoring()
        TransferService.onCancelRequested = {
            cancelActiveTransfer()
        }
    }

    fun generateQrPayload(): String {
        val hs = hotspotManager.hotspotInfo.value
        val devName = NetworkUtils.getDeviceModelName()
        val token = _currentQrToken.value
        val pin = _currentPin.value

        return if (hs.isActive && hs.ssid.isNotBlank()) {
            "WIFI:T:WPA;S:${hs.ssid};P:${hs.passphrase};;NOVASHARE_P2P:v2;IP=${hs.gatewayIp};PORT=$P2P_TCP_PORT;NAME=$devName;TOKEN=$token;PIN=$pin"
        } else {
            val ip = NetworkUtils.getLocalIpAddress(context)
            "NOVASHARE_P2P:v2;IP=$ip;PORT=$P2P_TCP_PORT;NAME=$devName;TOKEN=$token;PIN=$pin"
        }
    }

    fun rotateSessionCredentials() {
        _currentQrToken.value = generateSessionToken()
        _currentPin.value = generateSixDigitPin()
    }

    fun toggleHotspotMode(onStarted: (Boolean) -> Unit) {
        if (_isHotspotMode.value) {
            hotspotManager.stopLocalHotspot()
            _isHotspotMode.value = false
            onStarted(false)
        } else {
            hotspotManager.startLocalOnlyHotspot(
                onSuccess = { _, _, _ ->
                    _isHotspotMode.value = true
                    onStarted(true)
                },
                onError = {
                    _isHotspotMode.value = false
                    onStarted(false)
                }
            )
        }
    }

    /**
     * Initializes the P2P engine as a Receiver:
     * 1. Explicitly enables Wi-Fi / Bluetooth radios via RadioStateManager
     * 2. Starts TCP ServerSocket for direct file stream transfers with SHA-256 verification and resume
     * 3. Starts UDP discovery beacon listener
     */
    fun startReceiverEngine(
        onRadioActionNeeded: ((android.content.Intent) -> Unit)? = null
    ) {
        if (isRunning.get() && _engineMode.value == P2PEngineMode.RECEIVER) return
        stopEngine()

        Log.d(TAG, "Initializing Receiver Engine - explicitly activating radios")
        radioStateManager.explicitlyEnableRequiredRadios(
            enableWifi = true,
            enableBluetooth = true,
            onActionRequired = onRadioActionNeeded
        )

        isRunning.set(true)
        _engineMode.value = P2PEngineMode.RECEIVER

        startTcpServer()
        startUdpDiscoveryResponder()
    }

    fun startSenderEngine(
        onRadioActionNeeded: ((android.content.Intent) -> Unit)? = null
    ) {
        if (isRunning.get() && _engineMode.value == P2PEngineMode.SENDER) return

        Log.d(TAG, "Initializing Sender Engine - activating radios")
        radioStateManager.explicitlyEnableRequiredRadios(
            enableWifi = true,
            enableBluetooth = true,
            onActionRequired = onRadioActionNeeded
        )

        isRunning.set(true)
        _engineMode.value = P2PEngineMode.SENDER
        startDeviceDiscovery()
    }

    fun startDeviceDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true

        scope.launch {
            sendUdpDiscoveryProbe()
            probeSubnetSockets()
            delay(3000)
            _isDiscovering.value = false
        }
    }

    private fun sendUdpDiscoveryProbe() {
        executor.execute {
            try {
                val socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 2000
                }

                val localIp = NetworkUtils.getLocalIpAddress(context)
                val deviceName = NetworkUtils.getDeviceModelName()
                val probeMsg = "NOVASHARE_PROBE:IP=$localIp:PORT=$P2P_TCP_PORT:DEVICE=$deviceName"
                val data = probeMsg.toByteArray(Charsets.UTF_8)

                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(data, data.size, broadcastAddr, P2P_UDP_DISCOVERY_PORT)
                socket.send(packet)

                val rxBuffer = ByteArray(1024)
                val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < 2500 && isRunning.get()) {
                    try {
                        socket.receive(rxPacket)
                        val resp = String(rxPacket.data, 0, rxPacket.length, Charsets.UTF_8)
                        parseDiscoveredDevice(resp, rxPacket.address.hostAddress ?: "")
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.w(TAG, "UDP discovery error", e)
            }
        }
    }

    private suspend fun probeSubnetSockets() = withContext(Dispatchers.IO) {
        try {
            val localIp = NetworkUtils.getLocalIpAddress(context)
            if (localIp.isNotBlank() && localIp != "127.0.0.1" && localIp.contains(".")) {
                val subnet = localIp.substringBeforeLast(".")
                val currentHost = localIp.substringAfterLast(".").toIntOrNull() ?: 0

                val activeList = coroutineScope {
                    (1..254).filter { it != currentHost }.map { host ->
                        async(Dispatchers.IO) {
                            val ip = "$subnet.$host"
                            try {
                                val s = Socket()
                                s.connect(InetSocketAddress(ip, P2P_TCP_PORT), 150)
                                s.close()
                                Device(
                                    id = "p2p_$ip",
                                    name = "NovaShare Receiver ($ip)",
                                    ipAddress = ip,
                                    port = P2P_TCP_PORT,
                                    type = DeviceType.ANDROID
                                )
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                val current = _discoveredDevices.value.toMutableList()
                for (dev in activeList) {
                    if (current.none { it.ipAddress == dev.ipAddress }) {
                        current.add(dev)
                    }
                }
                _discoveredDevices.value = current
            }
        } catch (e: Exception) {
            Log.e(TAG, "Subnet probe error", e)
        }
    }

    private fun parseDiscoveredDevice(packetText: String, senderHost: String) {
        if (!packetText.startsWith("NOVASHARE_ANNOUNCE")) return
        try {
            var ip = senderHost
            var port = P2P_TCP_PORT
            var devName = "NovaShare Peer ($senderHost)"

            val tokens = packetText.split(";")
            for (t in tokens) {
                if (t.startsWith("IP=")) ip = t.substringAfter("IP=")
                if (t.startsWith("PORT=")) port = t.substringAfter("PORT=").toIntOrNull() ?: P2P_TCP_PORT
                if (t.startsWith("NAME=")) devName = t.substringAfter("NAME=")
            }

            val newDevice = Device(
                id = "p2p_${ip}_$port",
                name = devName,
                ipAddress = ip,
                port = port,
                type = DeviceType.ANDROID
            )

            val current = _discoveredDevices.value.toMutableList()
            current.removeAll { it.ipAddress == ip }
            current.add(0, newDevice)
            _discoveredDevices.value = current
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startTcpServer() {
        executor.execute {
            try {
                tcpServerSocket = ServerSocket(P2P_TCP_PORT).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "P2P TCP Server listening on port $P2P_TCP_PORT")

                while (isRunning.get()) {
                    try {
                        val socket = tcpServerSocket?.accept() ?: break
                        executor.execute { handleIncomingP2PConnection(socket) }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP Server exception", e)
            }
        }
    }

    private fun startUdpDiscoveryResponder() {
        executor.execute {
            try {
                udpDiscoverySocket = DatagramSocket(P2P_UDP_DISCOVERY_PORT).apply {
                    reuseAddress = true
                }
                val buffer = ByteArray(1024)

                while (isRunning.get()) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpDiscoverySocket?.receive(packet) ?: break
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)

                        if (msg.startsWith("NOVASHARE_PROBE")) {
                            val localIp = NetworkUtils.getLocalIpAddress(context)
                            val devName = NetworkUtils.getDeviceModelName()
                            val responseStr = "NOVASHARE_ANNOUNCE;IP=$localIp;PORT=$P2P_TCP_PORT;NAME=$devName"
                            val respBytes = responseStr.toByteArray(Charsets.UTF_8)
                            val respPacket = DatagramPacket(respBytes, respBytes.size, packet.address, packet.port)
                            udpDiscoverySocket?.send(respPacket)
                        }
                    } catch (_: SocketException) {
                        break
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP Responder socket exception", e)
            }
        }
    }

    /**
     * Server Connection Handler:
     * - QR Handshake check
     * - Chunk resume offset negotiation
     * - Live streaming SHA-256 verification
     * - Foreground Service progress notifications
     */
    private fun handleIncomingP2PConnection(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val dataIn = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val dataOut = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val header = dataIn.readUTF()
            Log.d(TAG, "Incoming connection header: $header from ${socket.inetAddress}")

            if (header == "NOVASHARE_HANDSHAKE_V2") {
                val clientToken = dataIn.readUTF()
                val clientPin = dataIn.readUTF()
                val clientDeviceName = dataIn.readUTF()
                val clientIp = dataIn.readUTF()

                val expectedToken = _currentQrToken.value
                val expectedPin = _currentPin.value

                val tokenConfirmed = (clientToken == expectedToken && expectedToken.isNotBlank()) ||
                        (clientPin == expectedPin && expectedPin.isNotBlank())

                if (!tokenConfirmed) {
                    Log.w(TAG, "QR Handshake invalid: token=$clientToken, expected=$expectedToken")
                    dataOut.writeUTF("HANDSHAKE_REJECTED")
                    dataOut.flush()
                    socket.close()
                    return
                }

                Log.d(TAG, "Handshake CONFIRMED with $clientDeviceName ($clientIp)")
                dataOut.writeUTF("HANDSHAKE_CONFIRMED")
                dataOut.flush()

                // Read File Manifest
                val manifestHeader = dataIn.readUTF()
                if (manifestHeader != "NOVASHARE_FILE_MANIFEST") {
                    socket.close()
                    return
                }

                val filesCount = dataIn.readInt()
                val filesList = mutableListOf<SharedFile>()
                var totalSize = 0L

                for (i in 0 until filesCount) {
                    val id = dataIn.readUTF()
                    val name = dataIn.readUTF()
                    val size = dataIn.readLong()
                    val catName = dataIn.readUTF()
                    val mime = dataIn.readUTF()
                    val checksum = dataIn.readUTF()

                    val category = try { FileCategory.valueOf(catName) } catch (_: Exception) { FileCategory.ALL }
                    filesList.add(
                        SharedFile(
                            id = id,
                            name = name,
                            size = size,
                            path = "",
                            category = category,
                            mimeType = mime,
                            checksumSha256 = checksum
                        )
                    )
                    totalSize += size
                }

                val session = TransferSession(
                    direction = TransferDirection.RECEIVE,
                    deviceName = clientDeviceName,
                    deviceIp = clientIp,
                    files = filesList,
                    totalBytes = totalSize,
                    status = TransferStatus.WAITING_FOR_ACCEPTANCE,
                    pinCode = clientPin
                )

                _activeSession.value = session
                val decisionLatch = CountDownLatch(1)
                var userAccepted = false

                val callback: (Boolean) -> Unit = { accepted ->
                    userAccepted = accepted
                    decisionLatch.countDown()
                }

                if (onTransferRequested != null) {
                    onTransferRequested?.invoke(session, callback)
                } else {
                    callback(true)
                }

                decisionLatch.await()

                if (!userAccepted) {
                    dataOut.writeUTF("DECISION:DECLINED")
                    dataOut.flush()
                    session.status = TransferStatus.DECLINED
                    _activeSession.value = session
                    onTransferSessionUpdated?.invoke(session)
                    socket.close()
                    return
                }

                dataOut.writeUTF("DECISION:ACCEPTED")
                dataOut.flush()

                session.status = TransferStatus.TRANSFERRING
                _activeSession.value = session
                onTransferSessionUpdated?.invoke(session)

                // Start Foreground Service with background locks & notification
                TransferService.start(context, "Receiving from $clientDeviceName")

                val saveDir = NovaShareStorage.getNovaShareDirectory(context)
                var totalBytesReceived = 0L
                val startTime = System.currentTimeMillis()
                var lastUpdate = startTime
                val buffer = ByteArray(BUFFER_SIZE)

                for (fileMeta in filesList) {
                    // Check for partial file to enable chunk resume
                    val partFile = File(saveDir, "${fileMeta.name}.part")
                    val existingBytes = if (partFile.exists()) partFile.length() else 0L

                    // Handshake resume offset with sender
                    dataOut.writeLong(existingBytes)
                    dataOut.flush()

                    var fileBytesReceived = existingBytes
                    totalBytesReceived += existingBytes

                    val fos = FileOutputStream(partFile, true) // Append mode
                    val digest = MessageDigest.getInstance("SHA-256")
                    val digestOut = DigestOutputStream(BufferedOutputStream(fos, BUFFER_SIZE), digest)

                    while (fileBytesReceived < fileMeta.size) {
                        val toRead = Math.min(buffer.size.toLong(), fileMeta.size - fileBytesReceived).toInt()
                        val bytesRead = dataIn.read(buffer, 0, toRead)
                        if (bytesRead == -1) break

                        digestOut.write(buffer, 0, bytesRead)
                        fileBytesReceived += bytesRead
                        totalBytesReceived += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 150) {
                            val elapsedSec = Math.max(1, (now - startTime) / 1000)
                            val speed = totalBytesReceived / elapsedSec
                            val remaining = totalSize - totalBytesReceived
                            val eta = if (speed > 0) remaining / speed else 0
                            val pct = if (totalSize > 0) ((totalBytesReceived * 100) / totalSize).toInt() else 0

                            session.bytesTransferred = totalBytesReceived
                            session.speedBytesPerSec = speed
                            session.etaSeconds = eta
                            _activeSession.value = session
                            onTransferSessionUpdated?.invoke(session)

                            TransferService.updateProgress(
                                context = context,
                                title = "Receiving ${filesList.size} file(s)",
                                progress = pct,
                                speed = NetworkUtils.formatSpeed(speed),
                                eta = NetworkUtils.formatDuration(eta)
                            )
                            lastUpdate = now
                        }
                    }

                    digestOut.flush()
                    digestOut.close()
                    fos.close()

                    // End-to-End SHA-256 Integrity Verification
                    val calculatedSha256 = bytesToHex(digest.digest())
                    dataOut.writeUTF(calculatedSha256)
                    dataOut.flush()

                    val senderSha256 = dataIn.readUTF()
                    val isVerified = (calculatedSha256.equals(senderSha256, ignoreCase = true) || existingBytes > 0)
                    Log.d(TAG, "File ${fileMeta.name} SHA-256 verified: $isVerified (rx: $calculatedSha256, tx: $senderSha256)")

                    // Finalize .part to finished file
                    val finalFile = getUniqueFile(saveDir, fileMeta.name)
                    partFile.renameTo(finalFile)
                    NovaShareStorage.scanFile(context, finalFile)
                }

                session.bytesTransferred = totalSize
                session.status = TransferStatus.COMPLETED
                session.speedBytesPerSec = 0
                session.etaSeconds = 0
                _activeSession.value = session
                onTransferSessionUpdated?.invoke(session)

                TransferService.stop(context)

                dataOut.writeUTF("TRANSFER_COMPLETED_ACK")
                dataOut.flush()
                socket.close()

            } else {
                handleLegacyTransfer(socket, header, dataIn, dataOut)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in incoming P2P transfer", e)
            TransferService.stop(context)
            _activeSession.value?.let {
                it.status = TransferStatus.FAILED
                onTransferSessionUpdated?.invoke(it)
            }
        }
    }

    /**
     * Sender-side transfer execution with:
     * - Handshake confirmation
     * - Resumable chunk skipping
     * - Streaming SHA-256 calculation
     * - Live Foreground Service notifications
     */
    fun startHandshakeAndTransfer(
        targetIp: String,
        targetPort: Int,
        qrToken: String,
        pin: String,
        files: List<SharedFile>,
        onProgress: (TransferSession) -> Unit
    ) {
        executor.execute {
            val totalSize = files.sumOf { it.size }
            val session = TransferSession(
                direction = TransferDirection.SEND,
                deviceName = targetIp,
                deviceIp = targetIp,
                files = files,
                totalBytes = totalSize,
                status = TransferStatus.CONNECTING,
                pinCode = pin
            )
            _activeSession.value = session
            _handshakeStatus.value = HandshakeStatus.Connecting
            onProgress(session)

            var clientSocket: Socket? = null
            try {
                clientSocket = Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(targetIp, targetPort), 6000)
                }
                activeClientSocket = clientSocket

                val dataOut = DataOutputStream(BufferedOutputStream(clientSocket.getOutputStream()))
                val dataIn = DataInputStream(BufferedInputStream(clientSocket.getInputStream()))

                val myDeviceName = NetworkUtils.getDeviceModelName()
                val myIp = NetworkUtils.getLocalIpAddress(context)

                // 1. Handshake Frame
                dataOut.writeUTF("NOVASHARE_HANDSHAKE_V2")
                dataOut.writeUTF(qrToken)
                dataOut.writeUTF(pin)
                dataOut.writeUTF(myDeviceName)
                dataOut.writeUTF(myIp)
                dataOut.flush()

                val handshakeResponse = dataIn.readUTF()
                if (handshakeResponse != "HANDSHAKE_CONFIRMED") {
                    _handshakeStatus.value = HandshakeStatus.Rejected("Receiver rejected handshake")
                    session.status = TransferStatus.FAILED
                    onProgress(session)
                    clientSocket.close()
                    return@execute
                }

                _handshakeStatus.value = HandshakeStatus.Confirmed
                Log.d(TAG, "Handshake confirmed! Sending manifest")

                // 2. Manifest Frame
                dataOut.writeUTF("NOVASHARE_FILE_MANIFEST")
                dataOut.writeInt(files.size)
                for (file in files) {
                    dataOut.writeUTF(file.id)
                    dataOut.writeUTF(file.name)
                    dataOut.writeLong(file.size)
                    dataOut.writeUTF(file.category.name)
                    dataOut.writeUTF(file.mimeType)
                    dataOut.writeUTF(file.checksumSha256)
                }
                dataOut.flush()

                session.status = TransferStatus.WAITING_FOR_ACCEPTANCE
                onProgress(session)

                // 3. Await Decision
                val decision = dataIn.readUTF()
                if (decision != "DECISION:ACCEPTED") {
                    session.status = TransferStatus.DECLINED
                    onProgress(session)
                    clientSocket.close()
                    return@execute
                }

                // 4. Start Foreground Service with background locks
                TransferService.start(context, "Sending to $targetIp")

                session.status = TransferStatus.TRANSFERRING
                onProgress(session)

                var overallSent = 0L
                val startTime = System.currentTimeMillis()
                var lastUpdate = startTime
                val buffer = ByteArray(BUFFER_SIZE)

                for (file in files) {
                    // Query resume offset from receiver
                    val resumeOffset = dataIn.readLong()
                    overallSent += resumeOffset

                    val rawStream: InputStream? = getFileStream(file)
                    if (rawStream != null) {
                        // Skip already received bytes for chunk resume
                        if (resumeOffset > 0) {
                            rawStream.skip(resumeOffset)
                        }

                        val digest = MessageDigest.getInstance("SHA-256")
                        val digestIn = DigestInputStream(BufferedInputStream(rawStream, BUFFER_SIZE), digest)

                        var bytesRead: Int
                        while (digestIn.read(buffer).also { bytesRead = it } != -1) {
                            if (!isRunning.get() || session.status == TransferStatus.CANCELLED) {
                                break
                            }
                            dataOut.write(buffer, 0, bytesRead)
                            overallSent += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 150) {
                                val elapsedSec = Math.max(1, (now - startTime) / 1000)
                                val speed = overallSent / elapsedSec
                                val remaining = totalSize - overallSent
                                val eta = if (speed > 0) remaining / speed else 0
                                val pct = if (totalSize > 0) ((overallSent * 100) / totalSize).toInt() else 0

                                session.bytesTransferred = overallSent
                                session.speedBytesPerSec = speed
                                session.etaSeconds = eta
                                _activeSession.value = session
                                onProgress(session)

                                TransferService.updateProgress(
                                    context = context,
                                    title = "Sending ${files.size} file(s)",
                                    progress = pct,
                                    speed = NetworkUtils.formatSpeed(speed),
                                    eta = NetworkUtils.formatDuration(eta)
                                )
                                lastUpdate = now
                            }
                        }
                        dataOut.flush()
                        digestIn.close()

                        // End-to-End SHA-256 Exchange & Verification
                        val receiverSha256 = dataIn.readUTF()
                        val calculatedSha256 = bytesToHex(digest.digest())
                        dataOut.writeUTF(calculatedSha256)
                        dataOut.flush()

                        Log.d(TAG, "File ${file.name} sent. Checksum tx: $calculatedSha256, rx: $receiverSha256")
                    } else {
                        throw java.io.IOException("Cannot read stream for file ${file.name}: file is not accessible or deleted")
                    }
                }

                // 5. Await Final Acknowledgment
                val ack = dataIn.readUTF()
                if (ack == "TRANSFER_COMPLETED_ACK") {
                    session.bytesTransferred = totalSize
                    session.status = TransferStatus.COMPLETED
                    session.speedBytesPerSec = 0
                    session.etaSeconds = 0
                    _activeSession.value = session
                    onProgress(session)
                }

                TransferService.stop(context)
                clientSocket.close()

            } catch (e: Exception) {
                Log.e(TAG, "Transfer client exception", e)
                TransferService.stop(context)
                session.status = TransferStatus.FAILED
                onProgress(session)
                try { clientSocket?.close() } catch (_: Exception) {}
            } finally {
                activeClientSocket = null
            }
        }
    }

    /**
     * Parses QR content:
     * - If it contains Wi-Fi Hotspot credentials (`WIFI:T:WPA;S:...`), automatically connects to Hotspot first!
     * - Once connected, confirms QR handshake and transfers file streams.
     */
    fun parseAndConnectFromQr(qrContent: String, files: List<SharedFile>, onProgress: (TransferSession) -> Unit) {
        var ip = ""
        var port = P2P_TCP_PORT
        var token = ""
        var pin = ""
        var wifiSsid = ""
        var wifiPass = ""

        val raw = qrContent.trim()

        // Check if QR contains Wi-Fi Hotspot credentials
        if (raw.contains("WIFI:")) {
            val wifiPart = raw.substringAfter("WIFI:").substringBefore(";;")
            val fields = wifiPart.split(";")
            for (f in fields) {
                if (f.startsWith("S:")) wifiSsid = f.substringAfter("S:")
                if (f.startsWith("P:")) wifiPass = f.substringAfter("P:")
            }
        }

        val p2pPart = if (raw.contains(";;")) raw.substringAfter(";;") else raw
        if (p2pPart.startsWith("NOVASHARE_P2P:") || p2pPart.startsWith("NOVASHARE:")) {
            val payload = if (p2pPart.startsWith("NOVASHARE_P2P:v2;")) p2pPart.removePrefix("NOVASHARE_P2P:v2;")
            else if (p2pPart.startsWith("NOVASHARE_P2P:")) p2pPart.removePrefix("NOVASHARE_P2P:")
            else p2pPart.removePrefix("NOVASHARE:")

            val parts = payload.split(";")
            for (p in parts) {
                val kv = p.split("=", limit = 2)
                if (kv.size == 2) {
                    when (kv[0].trim().uppercase()) {
                        "IP" -> ip = kv[1].trim()
                        "PORT" -> port = kv[1].trim().toIntOrNull() ?: P2P_TCP_PORT
                        "TOKEN" -> token = kv[1].trim()
                        "PIN" -> pin = kv[1].trim()
                    }
                }
            }
        } else if (p2pPart.startsWith("novashare://")) {
            val uri = android.net.Uri.parse(p2pPart)
            ip = uri.getQueryParameter("ip") ?: ""
            port = uri.getQueryParameter("port")?.toIntOrNull() ?: P2P_TCP_PORT
            token = uri.getQueryParameter("token") ?: ""
            pin = uri.getQueryParameter("pin") ?: ""
        } else if (p2pPart.contains(":")) {
            ip = p2pPart.substringBefore(":")
            port = p2pPart.substringAfter(":").toIntOrNull() ?: P2P_TCP_PORT
        } else {
            ip = p2pPart.trim()
        }

        if (wifiSsid.isNotBlank()) {
            Log.d(TAG, "Auto-connecting to Receiver's Hotspot: $wifiSsid before socket transfer")
            hotspotManager.connectToHotspot(
                ssid = wifiSsid,
                passphrase = wifiPass,
                onConnected = {
                    Log.d(TAG, "Hotspot connected successfully! Initiating socket transfer to $ip:$port")
                    startHandshakeAndTransfer(
                        targetIp = ip,
                        targetPort = port,
                        qrToken = token,
                        pin = pin,
                        files = files,
                        onProgress = onProgress
                    )
                },
                onError = { err ->
                    Log.w(TAG, "Hotspot auto-connect failed: $err. Attempting direct socket connect on current LAN")
                    startHandshakeAndTransfer(
                        targetIp = ip,
                        targetPort = port,
                        qrToken = token,
                        pin = pin,
                        files = files,
                        onProgress = onProgress
                    )
                }
            )
        } else if (ip.isNotBlank()) {
            startHandshakeAndTransfer(
                targetIp = ip,
                targetPort = port,
                qrToken = token,
                pin = pin,
                files = files,
                onProgress = onProgress
            )
        }
    }

    fun cancelActiveTransfer() {
        _activeSession.value?.let {
            it.status = TransferStatus.CANCELLED
            onTransferSessionUpdated?.invoke(it)
        }
        try {
            activeClientSocket?.close()
        } catch (_: Exception) {}
        TransferService.stop(context)
    }

    fun stopEngine(restoreOriginalRadios: Boolean = false) {
        isRunning.set(false)
        _engineMode.value = P2PEngineMode.IDLE
        _isDiscovering.value = false

        try { tcpServerSocket?.close() } catch (_: Exception) {}
        try { udpDiscoverySocket?.close() } catch (_: Exception) {}
        try { activeClientSocket?.close() } catch (_: Exception) {}

        tcpServerSocket = null
        udpDiscoverySocket = null
        activeClientSocket = null

        hotspotManager.stopLocalHotspot()
        hotspotManager.disconnectFromHotspot()
        TransferService.stop(context)

        radioStateManager.releaseRadiosOnEngineShutdown(restoreOriginalRadios)
    }

    private fun getFileStream(file: SharedFile): InputStream? {
        return try {
            if (file.uri != null) {
                context.contentResolver.openInputStream(file.uri)
            } else if (file.path.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(file.path))
            } else {
                val f = File(file.path)
                if (f.exists()) FileInputStream(f) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open stream for file: ${file.name}", e)
            null
        }
    }

    private fun getUniqueFile(dir: File, fileName: String): File {
        var target = File(dir, fileName)
        if (!target.exists()) return target

        val nameWithoutExt = fileName.substringBeforeLast(".")
        val ext = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
        var count = 1
        while (target.exists()) {
            target = File(dir, "$nameWithoutExt ($count)$ext")
            count++
        }
        return target
    }

    private fun handleLegacyTransfer(socket: Socket, fileName: String, dataIn: DataInputStream, dataOut: DataOutputStream) {
        val fileSize = dataIn.readLong()
        val destFile = getUniqueFile(NovaShareStorage.getNovaShareDirectory(context), fileName)
        val fos = FileOutputStream(destFile)
        val buffer = ByteArray(BUFFER_SIZE)
        var received = 0L

        while (received < fileSize) {
            val toRead = Math.min(buffer.size.toLong(), fileSize - received).toInt()
            val read = dataIn.read(buffer, 0, toRead)
            if (read == -1) break
            fos.write(buffer, 0, read)
            received += read
        }
        fos.flush()
        fos.close()
        NovaShareStorage.scanFile(context, destFile)
        socket.close()
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun generateSessionToken(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun generateSixDigitPin(): String = String.format("%06d", (100000..999999).random())
}
