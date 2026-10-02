package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.data.model.SharedFile
import com.example.util.NovaShareStorage
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

class WebServer(
    private val context: Context,
    private val port: Int = 8080,
    private val onFileUploaded: (String, Long, File) -> Unit = { _, _, _ -> }
) {
    companion object {
        private const val TAG = "NovaWebServer"
        private const val BUFFER_SIZE = 65536
    }

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
            Log.d(TAG, "Web Server started on port $port")

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
            Log.e(TAG, "Failed to start WebServer on port $port", e)
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
            Log.d(TAG, "Web Server stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping WebServer", e)
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            // Read HTTP Request Headers byte-by-byte up to \r\n\r\n so we do not over-buffer body
            val headerBytes = ByteArrayOutputStream()
            var state = 0
            while (true) {
                val b = input.read()
                if (b == -1) break
                headerBytes.write(b)
                when (state) {
                    0 -> if (b == '\r'.code) state = 1 else state = 0
                    1 -> if (b == '\n'.code) state = 2 else state = 0
                    2 -> if (b == '\r'.code) state = 3 else state = 0
                    3 -> if (b == '\n'.code) state = 4 else state = 0
                }
                if (state == 4) break
                if (headerBytes.size() > 65536) break // Safety limit
            }

            val headerText = headerBytes.toString("UTF-8")
            if (headerText.isBlank()) {
                socket.close()
                return
            }

            val lines = headerText.split("\r\n")
            val requestLine = lines[0]
            val requestParts = requestLine.split(" ")
            if (requestParts.size < 2) {
                socket.close()
                return
            }

            val method = requestParts[0].uppercase()
            val fullPath = requestParts[1]
            val path = fullPath.substringBefore("?")
            val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.contains(":")) {
                    val k = line.substringBefore(":").trim().lowercase()
                    val v = line.substringAfter(":").trim()
                    headers[k] = v
                }
            }

            val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
            Log.d(TAG, "HTTP $method $path (length=$contentLength)")

            when {
                method == "OPTIONS" -> {
                    serveCorsOptions(output)
                }
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    serveIndexPage(output)
                }
                method == "GET" && path == "/api/files" -> {
                    serveJsonFilesList(output)
                }
                method == "GET" && path.startsWith("/download/") -> {
                    val fileId = path.removePrefix("/download/").substringBefore("/")
                    serveFileDownload(fileId, output)
                }
                method == "POST" && (path == "/upload" || path.startsWith("/upload")) -> {
                    if (headers["expect"]?.contains("100-continue", ignoreCase = true) == true) {
                        output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                    handleDirectFileUpload(queryString, headers, contentLength, input, output)
                }
                else -> {
                    serveNotFound(output)
                }
            }
            output.flush()
            socket.close()
        } catch (e: Exception) {
            Log.w(TAG, "Client handling exception: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveIndexPage(output: OutputStream) {
        val filesHtml = synchronized(sharedFilesList) {
            if (sharedFilesList.isEmpty()) {
                "<div style='color: #94a3b8; text-align: center; padding: 32px; background: rgba(30,41,59,0.5); border-radius: 12px; border: 1px dashed rgba(255,255,255,0.1);'>No files selected on phone yet. Select files in Nova Share and they will appear here instantly.</div>"
            } else {
                sharedFilesList.joinToString("") { file ->
                    val safeName = file.name.replace("\"", "&quot;")
                    val ext = file.name.substringAfterLast('.', "").uppercase()
                    val encodedUrlName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
                    """
                    <div style="background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255,255,255,0.1); border-radius: 12px; padding: 14px 18px; margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center; gap: 14px; backdrop-filter: blur(8px);">
                        <div style="display: flex; align-items: center; gap: 12px; overflow: hidden;">
                            <div style="background: linear-gradient(135deg, #06b6d4, #8b5cf6); color: white; width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 11px; flex-shrink: 0;">$ext</div>
                            <div style="overflow: hidden;">
                                <div style="font-weight: 600; color: #f8fafc; font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">$safeName</div>
                                <div style="color: #38bdf8; font-size: 12px; margin-top: 3px;">${NetworkUtils.formatFileSize(file.size)} • ${file.category.displayName}</div>
                            </div>
                        </div>
                        <a href="/download/${file.id}/$encodedUrlName" download="$safeName" style="background: linear-gradient(135deg, #06b6d4, #3b82f6); color: white; padding: 9px 20px; border-radius: 9px; text-decoration: none; font-weight: 700; font-size: 13px; flex-shrink: 0; box-shadow: 0 4px 12px rgba(6,182,212,0.3);" target="_blank">Download</a>
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
                * { box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    background: #0f172a;
                    color: #f8fafc;
                    margin: 0;
                    padding: 24px 16px;
                    display: flex;
                    justify-content: center;
                }
                .container { max-width: 680px; width: 100%; }
                .header {
                    text-align: center;
                    padding: 24px;
                    background: linear-gradient(135deg, rgba(6, 182, 212, 0.15), rgba(168, 85, 247, 0.15));
                    border: 1px solid rgba(255, 255, 255, 0.1);
                    border-radius: 18px;
                    margin-bottom: 24px;
                }
                .upload-box {
                    border: 2px dashed rgba(6, 182, 212, 0.4);
                    background: rgba(30, 41, 59, 0.5);
                    border-radius: 16px;
                    padding: 28px 16px;
                    text-align: center;
                    margin-bottom: 28px;
                    cursor: pointer;
                    transition: all 0.2s ease;
                }
                .upload-box:hover {
                    border-color: #06b6d4;
                    background: rgba(30, 41, 59, 0.8);
                }
                .btn {
                    background: linear-gradient(135deg, #06b6d4, #8b5cf6);
                    color: white;
                    border: none;
                    padding: 10px 22px;
                    border-radius: 10px;
                    font-weight: 700;
                    cursor: pointer;
                    font-size: 13px;
                    margin-top: 10px;
                }
                #progress-bar {
                    display: none;
                    width: 100%;
                    height: 8px;
                    background: rgba(255,255,255,0.1);
                    border-radius: 4px;
                    overflow: hidden;
                    margin-top: 14px;
                }
                #progress-fill {
                    height: 100%;
                    width: 0%;
                    background: linear-gradient(90deg, #06b6d4, #8b5cf6);
                    transition: width 0.1s;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1 style="margin: 0; font-size: 22px; color: #f8fafc;">Nova Share Web Portal</h1>
                    <p style="margin: 8px 0 0; color: #94a3b8; font-size: 13px;">Direct browser transfer between Phone & PC over local Wi-Fi</p>
                </div>

                <div class="upload-box" onclick="document.getElementById('file-input').click()">
                    <input type="file" id="file-input" style="display: none;" onchange="handleFileSelected(event)" multiple>
                    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#06b6d4" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-bottom: 8px;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                    <div style="font-weight: 700; font-size: 15px;">Send Files to Phone</div>
                    <div style="color: #94a3b8; font-size: 12px; margin-top: 4px;">Click to browse or drag & drop files here</div>
                    <button type="button" class="btn">Select Files</button>
                    <div id="progress-bar"><div id="progress-fill"></div></div>
                    <div id="upload-status" style="margin-top: 10px; font-size: 12px; color: #38bdf8; font-weight: 600;"></div>
                </div>

                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;">
                    <h2 style="font-size: 16px; margin: 0; color: #f8fafc;">Files from Phone</h2>
                    <button onclick="location.reload()" style="background: none; border: 1px solid rgba(255,255,255,0.2); color: #94a3b8; padding: 4px 10px; border-radius: 6px; font-size: 11px; cursor: pointer;">Refresh</button>
                </div>

                $filesHtml
            </div>

            <script>
                function handleFileSelected(e) {
                    const files = e.target.files;
                    if (files && files.length > 0) {
                        uploadQueue(Array.from(files), 0);
                    }
                }

                function uploadQueue(files, index) {
                    if (index >= files.length) {
                        document.getElementById('upload-status').innerText = '✓ All files uploaded successfully!';
                        setTimeout(() => location.reload(), 1500);
                        return;
                    }

                    const file = files[index];
                    const status = document.getElementById('upload-status');
                    const pBar = document.getElementById('progress-bar');
                    const pFill = document.getElementById('progress-fill');
                    pBar.style.display = 'block';
                    status.innerText = 'Uploading ' + (index + 1) + '/' + files.length + ': ' + file.name + '...';

                    const xhr = new XMLHttpRequest();
                    xhr.open('POST', '/upload?filename=' + encodeURIComponent(file.name), true);
                    xhr.setRequestHeader('Content-Type', 'application/octet-stream');
                    xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));

                    xhr.upload.onprogress = function(e) {
                        if (e.lengthComputable) {
                            const percent = Math.round((e.loaded / e.total) * 100);
                            pFill.style.width = percent + '%';
                            status.innerText = 'Uploading ' + file.name + ' (' + percent + '%)';
                        }
                    };

                    xhr.onload = function() {
                        if (xhr.status === 200) {
                            uploadQueue(files, index + 1);
                        } else {
                            status.innerText = '✗ Upload error for ' + file.name;
                        }
                    };

                    xhr.onerror = function() {
                        status.innerText = '✗ Network error uploading ' + file.name;
                    };

                    xhr.send(file);
                }
            </script>
        </body>
        </html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bytes)
    }

    private fun serveJsonFilesList(output: OutputStream) {
        val json = synchronized(sharedFilesList) {
            "[" + sharedFilesList.joinToString(",") {
                "{\"id\":\"${it.id}\",\"name\":\"${it.name.replace("\"", "\\\"")}\",\"size\":${it.size},\"category\":\"${it.category.name}\"}"
            } + "]"
        }
        val bytes = json.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bytes)
    }

    /**
     * Streams the real original file bytes directly to the browser with correct MIME type
     * and Content-Disposition, preventing any file corruption.
     */
    private fun serveFileDownload(fileId: String, output: OutputStream) {
        val sharedFile = synchronized(sharedFilesList) {
            sharedFilesList.find { it.id == fileId }
        }

        if (sharedFile == null) {
            serveNotFound(output)
            return
        }

        val inStream: InputStream? = try {
            if (sharedFile.uri != null) {
                context.contentResolver.openInputStream(sharedFile.uri)
            } else if (sharedFile.path.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(sharedFile.path))
            } else {
                val f = File(sharedFile.path)
                if (f.exists()) FileInputStream(f) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening stream for download ${sharedFile.name}", e)
            null
        }

        if (inStream == null) {
            Log.w(TAG, "Download stream not found for ${sharedFile.name}")
            serveNotFound(output)
            return
        }

        val ext = sharedFile.name.substringAfterLast('.', "").lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }

        val actualSize = try {
            if (sharedFile.uri != null) {
                context.contentResolver.openFileDescriptor(sharedFile.uri, "r")?.use { it.statSize } ?: sharedFile.size
            } else if (sharedFile.path.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(sharedFile.path), "r")?.use { it.statSize } ?: sharedFile.size
            } else {
                val f = File(sharedFile.path)
                if (f.exists()) f.length() else sharedFile.size
            }
        } catch (_: Exception) {
            sharedFile.size
        }

        val safeAscii = sharedFile.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val encodedName = URLEncoder.encode(sharedFile.name, "UTF-8").replace("+", "%20")

        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mimeType\r\n" +
                "Content-Disposition: attachment; filename=\"$safeAscii\"; filename*=UTF-8''$encodedName\r\n" +
                (if (actualSize > 0) "Content-Length: $actualSize\r\n" else "") +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.UTF_8))

        try {
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.flush()
            Log.d(TAG, "Download completed for ${sharedFile.name} (actual=$actualSize)")
        } catch (e: Exception) {
            Log.w(TAG, "Client disconnected while downloading ${sharedFile.name}: ${e.message}")
        } finally {
            try { inStream.close() } catch (_: Exception) {}
        }
    }

    /**
     * Reads pure raw binary file bytes directly from HTTP POST body into NovaShare Downloads directory,
     * preserving full data integrity and triggering Android MediaScanner.
     */
    private fun handleDirectFileUpload(
        queryString: String,
        headers: Map<String, String>,
        contentLength: Long,
        input: InputStream,
        output: OutputStream
    ) {
        var rawName = ""

        // 1. Check query parameter ?filename=...
        if (queryString.contains("filename=")) {
            rawName = queryString.substringAfter("filename=").substringBefore("&")
        }

        // 2. Check X-Filename header
        if (rawName.isBlank()) {
            rawName = headers["x-filename"] ?: ""
        }

        var fileName = try {
            URLDecoder.decode(rawName, "UTF-8")
        } catch (_: Exception) {
            rawName
        }

        if (fileName.isBlank()) {
            fileName = "Upload_${System.currentTimeMillis()}.bin"
        }

        // Sanitize file name but keep original name
        fileName = File(fileName).name.replace(Regex("[/\\\\:*?\"<>|]"), "_")

        val destDir = NovaShareStorage.getNovaShareDirectory(context)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val destFile = getUniqueFile(destDir, fileName)

        Log.d(TAG, "Receiving web upload: $fileName into ${destFile.absolutePath} ($contentLength bytes)")

        var bytesWritten = 0L
        try {
            val fos = FileOutputStream(destFile)
            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = if (contentLength > 0) contentLength else Long.MAX_VALUE
            var read: Int

            while (remaining > 0) {
                val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
                read = input.read(buffer, 0, toRead)
                if (read == -1) break
                fos.write(buffer, 0, read)
                bytesWritten += read
                if (contentLength > 0) remaining -= read
            }
            fos.flush()
            fos.close()

            NovaShareStorage.scanFile(context, destFile)
            Log.d(TAG, "Web upload saved: ${destFile.name} ($bytesWritten bytes)")
            onFileUploaded(destFile.name, bytesWritten, destFile)

            val resBody = "{\"status\":\"ok\",\"file\":\"${destFile.name}\",\"bytes\":$bytesWritten}"
            val response = "HTTP/1.1 200 OK\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${resBody.toByteArray(Charsets.UTF_8).size}\r\n" +
                    "Connection: close\r\n\r\n" + resBody
            output.write(response.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving uploaded file", e)
            val errBody = "{\"status\":\"error\",\"message\":\"${e.message}\"}"
            val response = "HTTP/1.1 500 Internal Server Error\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${errBody.toByteArray(Charsets.UTF_8).size}\r\n" +
                    "Connection: close\r\n\r\n" + errBody
            output.write(response.toByteArray(Charsets.UTF_8))
        }
    }

    private fun serveCorsOptions(output: OutputStream) {
        val response = "HTTP/1.1 200 OK\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
    }

    private fun getUniqueFile(dir: File, fileName: String): File {
        var target = File(dir, fileName)
        if (!target.exists()) return target

        val nameWithoutExt = fileName.substringBeforeLast('.')
        val ext = if (fileName.contains('.')) ".${fileName.substringAfterLast('.')}" else ""
        var counter = 1

        while (target.exists()) {
            target = File(dir, "${nameWithoutExt}_$counter$ext")
            counter++
        }
        return target
    }

    private fun serveNotFound(output: OutputStream) {
        val body = "404 Not Found - Nova Share Web Portal"
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n" + body
        output.write(response.toByteArray(Charsets.UTF_8))
    }
}
