package com.example.network

import com.example.data.model.SharedFile
import com.example.data.model.TransferSession
import com.example.data.model.TransferStatus
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Socket
import java.util.concurrent.Executors

class P2PClient(
    private val targetIp: String,
    private val targetPort: Int = 8888,
    private val filesToSend: List<SharedFile>,
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
                status = TransferStatus.TRANSFERRING
            )
            onProgress(session)

            var overallSent = 0L
            val startTime = System.currentTimeMillis()

            for (file in filesToSend) {
                if (isCancelled) {
                    session.status = TransferStatus.CANCELLED
                    onProgress(session)
                    return@execute
                }

                try {
                    val socket = Socket(targetIp, targetPort)
                    val dataOut = DataOutputStream(socket.getOutputStream())

                    dataOut.writeUTF(file.name)
                    dataOut.writeLong(file.size)

                    val srcFile = File(file.path)
                    if (srcFile.exists()) {
                        val fis = FileInputStream(srcFile)
                        val buffer = ByteArray(32768)
                        var read: Int
                        var fileSent = 0L

                        while (fis.read(buffer).also { read = it } != -1) {
                            if (isCancelled) break
                            dataOut.write(buffer, 0, read)
                            fileSent += read
                            overallSent += read

                            val now = System.currentTimeMillis()
                            val elapsedSec = Math.max(1, (now - startTime) / 1000)
                            val speed = overallSent / elapsedSec
                            val eta = if (speed > 0) (totalSize - overallSent) / speed else 0

                            session.bytesTransferred = overallSent
                            session.speedBytesPerSec = speed
                            session.etaSeconds = eta
                            onProgress(session)
                        }
                        fis.close()
                    }
                    dataOut.flush()
                    socket.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            session.status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.COMPLETED
            session.speedBytesPerSec = 0
            session.etaSeconds = 0
            onProgress(session)
        }
    }

    fun cancel() {
        isCancelled = true
    }
}
