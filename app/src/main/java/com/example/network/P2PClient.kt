package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.model.SharedFile
import com.example.data.model.TransferSession
import com.example.data.model.TransferStatus
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class P2PClient(
    private val senderDeviceName: String = NetworkUtils.getDeviceModelName(),
    private val targetIp: String,
    private val targetPort: Int = 8888,
    private val filesToSend: List<SharedFile>,
    private val context: Context? = null,
    private val onProgress: (TransferSession) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var isCancelled = false

    fun startTransfer() {
        executor.execute {
            val totalSize = filesToSend.sumOf { it.size }
            val session = TransferSession(
                direction = com.example.data.model.TransferDirection.SEND,
                deviceName = targetIp,
                deviceIp = targetIp,
                files = filesToSend,
                totalBytes = totalSize,
                status = TransferStatus.CONNECTING
            )
            onProgress(session)

            try {
                val socket = Socket().apply {
                    tcpNoDelay = true
                    try { sendBufferSize = 1048576 } catch (_: Exception) {}
                    try { receiveBufferSize = 1048576 } catch (_: Exception) {}
                    try { trafficClass = 0x10 } catch (_: Exception) {}
                    connect(InetSocketAddress(targetIp, targetPort), 5000)
                }

                val dataOut = DataOutputStream(java.io.BufferedOutputStream(socket.getOutputStream(), 262144))
                val dataIn = DataInputStream(java.io.BufferedInputStream(socket.getInputStream(), 262144))

                // Send transfer request packet
                dataOut.writeUTF("NOVASHARE_TRANSFER_REQUEST")
                dataOut.writeUTF(senderDeviceName)
                dataOut.writeInt(filesToSend.size)

                for (file in filesToSend) {
                    dataOut.writeUTF(file.name)
                    dataOut.writeLong(file.size)
                    dataOut.writeUTF(file.category.name)
                }
                dataOut.flush()

                session.status = TransferStatus.WAITING_FOR_ACCEPTANCE
                onProgress(session)

                // Read receiver's decision
                val response = dataIn.readUTF()
                Log.d("NovaP2PClient", "Receiver response: $response")

                if (response != "ACCEPTED") {
                    session.status = TransferStatus.DECLINED
                    onProgress(session)
                    socket.close()
                    return@execute
                }

                session.status = TransferStatus.TRANSFERRING
                onProgress(session)

                var overallSent = 0L
                val startTime = System.currentTimeMillis()
                var lastProgressTime = startTime
                val buffer = ByteArray(262144) // 256 KB buffer

                for (file in filesToSend) {
                    if (isCancelled) {
                        session.status = TransferStatus.CANCELLED
                        onProgress(session)
                        socket.close()
                        return@execute
                    }

                    val rawInStream: InputStream? = if (file.uri != null) {
                        try { context?.contentResolver?.openInputStream(file.uri) } catch (_: Exception) { null }
                    } else if (file.path.startsWith("content://")) {
                        try { context?.contentResolver?.openInputStream(android.net.Uri.parse(file.path)) } catch (_: Exception) { null }
                    } else {
                        val srcFile = File(file.path)
                        if (srcFile.exists()) FileInputStream(srcFile) else null
                    }

                    if (rawInStream != null) {
                        val inStream = java.io.BufferedInputStream(rawInStream, 262144)
                        var read: Int

                        while (inStream.read(buffer).also { read = it } != -1) {
                            if (isCancelled) break
                            dataOut.write(buffer, 0, read)
                            overallSent += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime > 150) {
                                val elapsedSec = Math.max(1, (now - startTime) / 1000)
                                val speed = overallSent / elapsedSec
                                val remainingBytes = totalSize - overallSent
                                val eta = if (speed > 0) remainingBytes / speed else 0

                                session.bytesTransferred = overallSent
                                session.speedBytesPerSec = speed
                                session.etaSeconds = eta
                                onProgress(session)
                                lastProgressTime = now
                            }
                        }
                        inStream.close()
                    } else {
                        throw java.io.IOException("Cannot read stream for file ${file.name}")
                    }
                    dataOut.flush()
                }

                session.bytesTransferred = totalSize
                session.status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.COMPLETED
                session.speedBytesPerSec = 0
                session.etaSeconds = 0
                onProgress(session)

                socket.close()

            } catch (e: Exception) {
                e.printStackTrace()
                session.status = TransferStatus.FAILED
                onProgress(session)
            }
        }
    }

    fun cancel() {
        isCancelled = true
    }
}
