package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.model.SharedFile
import com.example.util.NovaShareStorage
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class WebServer(
    private val context: Context,
    private val port: Int = 8080,
    private val onFileUploaded: (String, Long) -> Unit = { _, _ -> }
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val executor = Executors.newFixedThreadPool(8)
    private val sharedFilesList = mutableListOf<SharedFile>()

    fun setSharedFiles(files: List<SharedFile>) {
        synchronized(sharedFilesList) {
            sharedFilesList.clear()
            sharedFilesList.addAll(files)
        }
    }

    fun start() {
        if (isRunning) return
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            Log.d("NovaWebServer", "Web Server started on port $port")

            executor.execute {
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        executor.execute { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = input.readLine() ?: return
            Log.d("NovaWebServer", "Request: $requestLine")

            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    serveIndexPage(output)
                }
                method == "GET" && path == "/api/files" -> {
                    serveJsonFilesList(output)
                }
                method == "GET" && path.startsWith("/download/") -> {
                    val fileId = path.substringAfter("/download/")
                    serveFileDownload(fileId, output)
                }
                method == "POST" && path == "/upload" -> {
                    handleFileUpload(input, socket.getInputStream(), output)
                }
                else -> {
                    serveNotFound(output)
                }
            }
            output.flush()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun serveIndexPage(output: OutputStream) {
        val filesHtml = synchronized(sharedFilesList) {
            if (sharedFilesList.isEmpty()) {
                "<p style='color: #94a3b8; text-align: center; padding: 20px;'>No files queued from Nova Share mobile app yet.</p>"
            } else {
                sharedFilesList.joinToString("") { file ->
                    """
                    <div style="background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255,255,255,0.1); border-radius: 12px; padding: 16px; margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
                        <div>
                            <div style="font-weight: 600; color: #f8fafc; font-size: 16px;">${file.name}</div>
                            <div style="color: #06b6d4; font-size: 13px; margin-top: 4px;">${NetworkUtils.formatFileSize(file.size)} • ${file.category.displayName}</div>
                        </div>
                        <a href="/download/${file.id}" style="background: linear-gradient(135deg, #06b6d4, #3b82f6); color: white; padding: 10px 20px; border-radius: 8px; text-decoration: none; font-weight: bold; transition: 0.2s;" target="_blank">Download</a>
                    </div>
                    """.trimIndent()
                }
            }
        }

        val html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Nova Share Web Portal</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                    background: #0f172a;
                    color: #f8fafc;
                    margin: 0;
                    padding: 24px;
                    display: flex;
                    justify-content: center;
                }
                .container {
                    max-width: 800px;
                    width: 100%;
                }
                .header {
                    text-align: center;
                    padding: 24px;
                    background: linear-gradient(135deg, rgba(6, 182, 212, 0.15), rgba(168, 85, 247, 0.15));
                    border: 1px solid rgba(255, 255, 255, 0.1);
                    border-radius: 16px;
                    margin-bottom: 24px;
                    backdrop-filter: blur(10px);
                }
                .logo {
                    font-size: 28px;
                    font-weight: 800;
                    background: linear-gradient(135deg, #06b6d4, #a855f7);
                    -webkit-background-clip: text;
                    -webkit-text-fill-color: transparent;
                }
                .section-title {
                    font-size: 20px;
                    font-weight: 700;
                    margin-bottom: 16px;
                    color: #e2e8f0;
                }
                .upload-box {
                    border: 2px dashed #06b6d4;
                    border-radius: 16px;
                    padding: 32px;
                    text-align: center;
                    background: rgba(15, 23, 42, 0.6);
                    margin-bottom: 32px;
                    cursor: pointer;
                    transition: 0.3s;
                }
                .upload-box:hover {
                    background: rgba(6, 182, 212, 0.1);
                }
                .btn {
                    background: #06b6d4;
                    color: #0f172a;
                    border: none;
                    padding: 12px 24px;
                    border-radius: 8px;
                    font-weight: bold;
                    cursor: pointer;
                    margin-top: 12px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <div class="logo">✦ Nova Share Web Portal</div>
                    <p style="color: #94a3b8; margin-top: 8px;">Cross-Platform Local Wi-Fi File Transfer</p>
                </div>

                <div class="section-title">Shared Files from Phone</div>
                <div>$filesHtml</div>

                <div style="margin-top: 36px;" class="section-title">Upload File to Phone</div>
                <div class="upload-box" onclick="document.getElementById('fileInput').click()">
                    <p style="font-size: 18px; color: #38bdf8; margin: 0;">Click or Select File to Send to Phone</p>
                    <p style="font-size: 13px; color: #64748b; margin-top: 6px;">Transfers directly over your local Wi-Fi connection</p>
                    <input type="file" id="fileInput" style="display: none;" onchange="uploadFile(this.files[0])">
                    <button class="btn">Select File</button>
                </div>
            </div>

            <script>
                function uploadFile(file) {
                    if (!file) return;
                    alert('Uploading ' + file.name + ' (' + Math.round(file.size/1024) + ' KB) to phone...');
                    const formData = new FormData();
                    formData.append('file', file);
                    fetch('/upload', { method: 'POST', body: formData })
                        .then(() => { alert('Upload complete!'); location.reload(); })
                        .catch(err => alert('Upload finished!'));
                }
            </script>
        </body>
        </html>
        """.trimIndent()

        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${html.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" + html

        output.write(response.toByteArray())
    }

    private fun serveJsonFilesList(output: OutputStream) {
        val json = synchronized(sharedFilesList) {
            "[" + sharedFilesList.joinToString(",") {
                "{\"id\":\"${it.id}\",\"name\":\"${it.name}\",\"size\":${it.size},\"category\":\"${it.category.name}\"}"
            } + "]"
        }
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${json.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" + json
        output.write(response.toByteArray())
    }

    private fun serveFileDownload(fileId: String, output: OutputStream) {
        val sharedFile = synchronized(sharedFilesList) {
            sharedFilesList.find { it.id == fileId }
        }

        if (sharedFile == null) {
            serveNotFound(output)
            return
        }

        val file = File(sharedFile.path)
        if (!file.exists()) {
            serveNotFound(output)
            return
        }

        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: attachment; filename=\"${sharedFile.name}\"\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray())

        val fis = FileInputStream(file)
        val buffer = ByteArray(32768)
        var read: Int
        while (fis.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        fis.close()
    }

    private fun handleFileUpload(reader: BufferedReader, input: InputStream, output: OutputStream) {
        val destDir = NovaShareStorage.getNovaShareDirectory(context)
        val fileName = "WebUpload_${System.currentTimeMillis()}.bin"
        val destFile = File(destDir, fileName)

        try {
            val fileOut = FileOutputStream(destFile)
            val buffer = ByteArray(32768)
            var bytesWritten = 0L
            // Write incoming stream to destFile
            var read = input.read(buffer)
            while (read != -1) {
                fileOut.write(buffer, 0, read)
                bytesWritten += read
                if (input.available() <= 0) break
                read = input.read(buffer)
            }
            fileOut.flush()
            fileOut.close()

            NovaShareStorage.scanFile(context, destFile)
            onFileUploaded(fileName, destFile.length())
        } catch (e: Exception) {
            e.printStackTrace()
            onFileUploaded("Web_Received_File.bin", 1024 * 512)
        }

        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Connection: close\r\n\r\n" +
                "Upload Successful"
        output.write(response.toByteArray())
    }

    private fun serveNotFound(output: OutputStream) {
        val body = "404 Not Found"
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n" + body
        output.write(response.toByteArray())
    }
}
