package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.model.FileCategory
import com.example.data.model.SharedFile
import com.example.data.model.TransferDirection
import com.example.data.model.TransferSession
import com.example.data.model.TransferStatus
import com.example.util.NovaShareStorage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class P2PServer(
    private val context: Context? = null,
    private val port: Int = 8888,
    private val outputDir: File = NovaShareStorage.getNovaShareDirectory(context),
    private val onTransferRequested: (TransferSession, (Boolean) -> Unit) -> Unit = { _, callback -> callback(true) },
    private val onTransferUpdated: (TransferSession) -> Unit = {}
) {
    private var serverSocket: ServerSocket? = null
    private var isListening = false
    private val executor = Executors.newFixedThreadPool(4)

    fun start() {
        if (isListening) return
        try {
            serverSocket = ServerSocket(port)
            isListening = true
            Log.d("NovaP2PServer", "P2P Server started listening on port $port")
            executor.execute {
                while (isListening) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        executor.execute { handleIncomingConnection(clientSocket) }
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
            val dataOutput = DataOutputStream(socket.getOutputStream())

            val header = dataInput.readUTF()
            Log.d("NovaP2PServer", "Received header: $header from ${socket.inetAddress}")

            if (header == "NOVASHARE_TRANSFER_REQUEST") {
                val senderDeviceName = dataInput.readUTF()
                val filesCount = dataInput.readInt()
                val filesList = mutableListOf<SharedFile>()
                var totalSize = 0L

                for (i in 0 until filesCount) {
                    val name = dataInput.readUTF()
                    val size = dataInput.readLong()
                    val catName = dataInput.readUTF()
                    val cat = try { FileCategory.valueOf(catName) } catch (_: Exception) { FileCategory.ALL }
                    filesList.add(SharedFile(id = "rx_$i", name = name, size = size, path = "", category = cat, mimeType = "*/*"))
                    totalSize += size
                }

                val session = TransferSession(
                    direction = TransferDirection.RECEIVE,
                    deviceName = senderDeviceName,
                    deviceIp = socket.inetAddress.hostAddress ?: "",
                    files = filesList,
                    totalBytes = totalSize,
                    status = TransferStatus.WAITING_FOR_ACCEPTANCE
                )

                val decisionLatch = CountDownLatch(1)
                var userAccepted = false

                onTransferRequested(session) { accepted ->
                    userAccepted = accepted
                    decisionLatch.countDown()
                }

                decisionLatch.await()

                if (!userAccepted) {
                    dataOutput.writeUTF("DECLINED")
                    dataOutput.flush()
                    session.status = TransferStatus.DECLINED
                    onTransferUpdated(session)
                    socket.close()
                    return
                }

                dataOutput.writeUTF("ACCEPTED")
                dataOutput.flush()

                session.status = TransferStatus.TRANSFERRING
                onTransferUpdated(session)

                var totalBytesReceived = 0L
                val startTime = System.currentTimeMillis()
                var lastReportTime = startTime

                for (fileMeta in filesList) {
                    val destFile = File(outputDir, fileMeta.name)
                    val fileOut = FileOutputStream(destFile)
                    val buffer = ByteArray(32768)
                    var fileReceived = 0L

                    while (fileReceived < fileMeta.size) {
                        val toRead = Math.min(buffer.size.toLong(), fileMeta.size - fileReceived).toInt()
                        val read = dataInput.read(buffer, 0, toRead)
                        if (read == -1) break
                        fileOut.write(buffer, 0, read)
                        fileReceived += read
                        totalBytesReceived += read

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 250) {
                            val elapsedSec = Math.max(1, (now - startTime) / 1000)
                            val speed = totalBytesReceived / elapsedSec
                            val remainingBytes = totalSize - totalBytesReceived
                            val eta = if (speed > 0) remainingBytes / speed else 0

                            session.bytesTransferred = totalBytesReceived
                            session.speedBytesPerSec = speed
                            session.etaSeconds = eta
                            onTransferUpdated(session)
                            lastReportTime = now
                        }
                    }

                    fileOut.flush()
                    fileOut.close()
                    context?.let { NovaShareStorage.scanFile(it, destFile) }
                }

                session.bytesTransferred = totalSize
                session.status = TransferStatus.COMPLETED
                session.speedBytesPerSec = 0
                session.etaSeconds = 0
                onTransferUpdated(session)

                dataOutput.writeUTF("SUCCESS")
                dataOutput.flush()
                socket.close()

            } else {
                // Fallback for simple legacy file transfer
                val fileName = header
                val fileSize = dataInput.readLong()

                val session = TransferSession(
                    direction = TransferDirection.RECEIVE,
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

                while (bytesReceived < fileSize) {
                    val read = dataInput.read(buffer, 0, Math.min(buffer.size.toLong(), fileSize - bytesReceived).toInt())
                    if (read == -1) break
                    fileOut.write(buffer, 0, read)
                    bytesReceived += read
                }
                fileOut.flush()
                fileOut.close()
                context?.let { NovaShareStorage.scanFile(it, destFile) }

                session.bytesTransferred = fileSize
                session.status = TransferStatus.COMPLETED
                onTransferUpdated(session)
                socket.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
