package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.model.SharedFile
import com.example.data.model.TransferSession
import com.example.data.model.TransferStatus
import com.example.util.NovaShareStorage
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class P2PServer(
    private val context: Context? = null,
    private val port: Int = 8888,
    private val outputDir: File = NovaShareStorage.getNovaShareDirectory(context),
    private val onTransferUpdated: (TransferSession) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isListening = false
    private val executor = Executors.newSingleThreadExecutor()

    fun start() {
        if (isListening) return
        try {
            serverSocket = ServerSocket(port)
            isListening = true
            executor.execute {
                while (isListening) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleIncomingConnection(clientSocket)
                    } catch (e: Exception) {
                        if (!isListening) break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isListening = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        try {
            val dataInput = DataInputStream(socket.getInputStream())
            val fileName = dataInput.readUTF()
            val fileSize = dataInput.readLong()

            val session = TransferSession(
                direction = com.example.data.model.TransferDirection.RECEIVE,
                deviceName = socket.inetAddress.hostName ?: "Nearby Device",
                deviceIp = socket.inetAddress.hostAddress ?: "",
                files = emptyList(),
                totalBytes = fileSize,
                status = TransferStatus.TRANSFERRING
            )
            onTransferUpdated(session)

            val destFile = File(outputDir, fileName)
            val fileOut = FileOutputStream(destFile)

            val buffer = ByteArray(32768)
            var bytesReceived = 0L
            val startTime = System.currentTimeMillis()
            var lastReportTime = startTime

            while (bytesReceived < fileSize) {
                val read = dataInput.read(buffer, 0, Math.min(buffer.size.toLong(), fileSize - bytesReceived).toInt())
                if (read == -1) break
                fileOut.write(buffer, 0, read)
                bytesReceived += read

                val now = System.currentTimeMillis()
                if (now - lastReportTime > 300) {
                    val elapsedTimeSec = Math.max(1, (now - startTime) / 1000)
                    val speed = bytesReceived / elapsedTimeSec
                    val remainingBytes = fileSize - bytesReceived
                    val eta = if (speed > 0) remainingBytes / speed else 0

                    session.bytesTransferred = bytesReceived
                    session.speedBytesPerSec = speed
                    session.etaSeconds = eta
                    onTransferUpdated(session)
                    lastReportTime = now
                }
            }

            fileOut.flush()
            fileOut.close()

            context?.let { NovaShareStorage.scanFile(it, destFile) }

            session.bytesTransferred = fileSize
            session.status = TransferStatus.COMPLETED
            session.speedBytesPerSec = 0
            session.etaSeconds = 0
            onTransferUpdated(session)

            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
