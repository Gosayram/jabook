package com.jabook.core.stream

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Local HTTP server for JaBook app
 * Provides REST API endpoints and audio streaming functionality
 */
class LocalHttp(private val context: Context) : NanoHTTPD(17171) {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private val executor = Executors.newSingleThreadExecutor()
    private val TAG = "LocalHttp"
    
    // Server state
    private var isRunning = false
    private var serverThread: Thread? = null
    
    // Mock data for development
    private var mockUser = mapOf(
        "loggedIn" to false,
        "username" to null,
        "id" to null
    )
    
    private val mockEndpoints = listOf(
        mapOf(
            "url" to "https://rutracker.org",
            "healthy" to true,
            "status" to "healthy",
            "responseTime" to 150L,
            "lastChecked" to System.currentTimeMillis()
        ),
        mapOf(
            "url" to "https://rutracker.net",
            "healthy" to false,
            "status" to "unhealthy",
            "responseTime" to 5000L,
            "lastChecked" to System.currentTimeMillis()
        )
    )
    
    init {
        // Set up server configuration
        try {
            start(17171, false)
            isRunning = true
            Log.i(TAG, "Local HTTP server started on port 17171")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start local HTTP server", e)
            isRunning = false
        }
    }
    
    override fun start(port: Int, daemon: Boolean): NanoHTTPD {
        return try {
            super.start(port, daemon)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start server on port $port", e)
            throw e
        }
    }
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "Request: $method $uri")
        
        return try {
            when {
                uri.startsWith("/api/") -> handleApiRequest(session, uri, method)
                uri.startsWith("/stream/") -> handleStreamRequest(session, uri)
                else -> handleStaticRequest(session, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request: $uri", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error": "Internal server error", "message": "${e.message}"}"""
            )
        }
    }
    
    /**
     * Handles API requests
     */
    private fun handleApiRequest(session: IHTTPSession, uri: String, method: Method): Response {
        return when (uri) {
            "/api/ping" -> {
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    """{"status": "ok", "timestamp": ${System.currentTimeMillis()}}"""
                )
            }
            
            "/api/me" -> {
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    gson.toJson(mockUser)
                )
            }
            
            "/api/endpoints" -> {
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    mockEndpoints.toString()
                )
            }
            
            "/api/endpoints/active" -> {
                val active = mockEndpoints.firstOrNull { it["healthy"] as Boolean }
                if (active != null) {
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        active.toString()
                    )
                } else {
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "application/json",
                        """{"error": "No active endpoints"}"""
                    )
                }
            }
            
            "/api/login" -> {
                if (method == Method.POST) {
                    // Handle login
                    val params = parsePostParameters(session)
                    val username = params["username"] ?: ""
                    val password = params["password"] ?: ""
                    
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        // Mock successful login
                        mockUser = mapOf(
                            "loggedIn" to true,
                            "username" to username,
                            "id" to "user_${System.currentTimeMillis()}"
                        )
                        
                        newFixedLengthResponse(
                            Response.Status.OK,
                            "application/json",
                            gson.toJson(mapOf("success" to true, "message" to "Login successful"))
                        )
                    } else {
                        newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            gson.toJson(mapOf("success" to false, "error" to "Username and password required"))
                        )
                    }
                } else {
                    newFixedLengthResponse(
                        Response.Status.METHOD_NOT_ALLOWED,
                        "application/json",
                        gson.toJson(mapOf("error" to "Method not allowed"))
                    )
                }
            }
            
            "/api/logout" -> {
                if (method == Method.POST) {
                    // Handle logout
                    mockUser = mapOf(
                        "loggedIn" to false,
                        "username" to null,
                        "id" to null
                    )
                    
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        gson.toJson(mapOf("success" to true, "message" to "Logout successful"))
                    )
                } else {
                    newFixedLengthResponse(
                        Response.Status.METHOD_NOT_ALLOWED,
                        "application/json",
                        gson.toJson(mapOf("error" to "Method not allowed"))
                    )
                }
            }
            
            "/api/search" -> {
                if (method == Method.GET) {
                    val query = session.parameters["q"]?.firstOrNull() ?: ""
                    val page = session.parameters["page"]?.firstOrNull()?.toIntOrNull() ?: 1
                    
                    if (mockUser["loggedIn"] != true) {
                        newFixedLengthResponse(
                            Response.Status.UNAUTHORIZED,
                            "application/json",
                            gson.toJson(mapOf("error" to "Authentication required"))
                        )
                    } else if (query.isBlank()) {
                        newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            gson.toJson(mapOf("error" to "Search query required"))
                        )
                    } else {
                        // Mock search results
                        val results = (1..10).map { i ->
                            mapOf(
                                "id" to "${System.currentTimeMillis()}_$i",
                                "title" to "Search Result $i: $query",
                                "author" to "Author $i",
                                "size" to (100 + i * 50) * 1024 * 1024L, // MB to bytes
                                "seeds" to (10 + i),
                                "leeches" to (5 + i % 3),
                                "magnetUrl" to "magnet:?xt=urn:btih:${"1234567890abcdef".repeat(2)}&dn=result$i",
                                "torrentUrl" to null,
                                "description" to "Description for search result $i"
                            )
                        }
                        
                        newFixedLengthResponse(
                            Response.Status.OK,
                            "application/json",
                            gson.toJson(mapOf(
                                "success" to true,
                                "query" to query,
                                "page" to page,
                                "results" to results,
                                "total" to 100
                            ))
                        )
                    }
                } else {
                    newFixedLengthResponse(
                        Response.Status.METHOD_NOT_ALLOWED,
                        "application/json",
                        gson.toJson(mapOf("error" to "Method not allowed"))
                    )
                }
            }
            
            "/api/topic" -> {
                if (method == Method.GET) {
                    val topicId = session.parameters["id"]?.firstOrNull()
                    
                    if (mockUser["loggedIn"] != true) {
                        newFixedLengthResponse(
                            Response.Status.UNAUTHORIZED,
                            "application/json",
                            gson.toJson(mapOf("error" to "Authentication required"))
                        )
                    } else if (topicId == null) {
                        newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            gson.toJson(mapOf("error" to "Topic ID required"))
                        )
                    } else {
                        // Mock topic details
                        val topic = mapOf(
                            "id" to topicId,
                            "title" to "Sample Audiobook Topic",
                            "author" to "Sample Author",
                            "description" to "This is a detailed description of the audiobook topic. It contains multiple files and information about the content.",
                            "files" to listOf(
                                mapOf(
                                    "name" to "01 Introduction.mp3",
                                    "size" to 10 * 1024 * 1024L,
                                    "type" to "audio"
                                ),
                                mapOf(
                                    "name" to "02 Chapter 1.mp3",
                                    "size" to 25 * 1024 * 1024L,
                                    "type" to "audio"
                                ),
                                mapOf(
                                    "name" to "03 Chapter 2.mp3",
                                    "size" to 30 * 1024 * 1024L,
                                    "type" to "audio"
                                )
                            ),
                            "magnetUrl" to "magnet:?xt=urn:btih:${"1234567890abcdef".repeat(2)}&dn=sample_audiobook",
                            "torrentUrl" to null,
                            "totalSize" to 65 * 1024 * 1024L,
                            "postDate" to "2024-01-15 14:30:00",
                            "viewCount" to 1234,
                            "replyCount" to 56
                        )
                        
                        newFixedLengthResponse(
                            Response.Status.OK,
                            "application/json",
                            gson.toJson(mapOf(
                                "success" to true,
                                "topic" to topic
                            ))
                        )
                    }
                } else {
                    newFixedLengthResponse(
                        Response.Status.METHOD_NOT_ALLOWED,
                        "application/json",
                        gson.toJson(mapOf("error" to "Method not allowed"))
                    )
                }
            }
            
            else -> {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error": "Endpoint not found", "path": "$uri"}"""
                )
            }
        }
    }
    
    /**
     * Handles streaming requests
     */
    private fun handleStreamRequest(session: IHTTPSession, uri: String): Response {
        val torrentId = uri.substring("/stream/".length)
        
        // For now, return a mock audio file
        val mockAudioFile = createMockAudioFile()
        
        if (mockAudioFile.exists()) {
            val fileInputStream = FileInputStream(mockAudioFile)
            val fileLength = mockAudioFile.length()
            
            // Handle Range headers for seeking
            val rangeHeader = session.headers["range"]
            var response: Response? = null
            
            if (rangeHeader != null) {
                val range = parseRangeHeader(rangeHeader, fileLength)
                if (range != null) {
                    response = newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT,
                        "audio/mpeg",
                        fileInputStream,
                        range.length
                    )
                    response.addHeader("Content-Range", "bytes ${range.start}-${range.end}/$fileLength")
                    response.addHeader("Accept-Ranges", "bytes")
                }
            }
            
            if (response == null) {
                response = newFixedLengthResponse(
                    Response.Status.OK,
                    "audio/mpeg",
                    fileInputStream,
                    fileLength
                )
                response.addHeader("Accept-Ranges", "bytes")
            }
            
            return response
        } else {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                """{"error": "Audio file not found", "id": "$torrentId"}"""
            )
        }
    }
    
    /**
     * Handles static file requests
     */
    private fun handleStaticRequest(session: IHTTPSession, uri: String): Response {
        // For now, return a simple HTML response
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>JaBook Local Server</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; }
                    .container { max-width: 800px; margin: 0 auto; }
                    .endpoint { background: #f5f5f5; padding: 15px; margin: 10px 0; border-radius: 5px; }
                    .status { display: inline-block; padding: 3px 8px; border-radius: 3px; font-size: 12px; }
                    .status.healthy { background: #4CAF50; color: white; }
                    .status.unhealthy { background: #f44336; color: white; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>JaBook Local Server</h1>
                    <p>Local HTTP server is running. Available endpoints:</p>
                    
                    <div class="endpoint">
                        <strong>GET /api/ping</strong> - Server status
                    </div>
                    
                    <div class="endpoint">
                        <strong>GET /api/me</strong> - Current user info
                    </div>
                    
                    <div class="endpoint">
                        <strong>GET /api/endpoints</strong> - List all endpoints
                    </div>
                    
                    <div class="endpoint">
                        <strong>GET /api/endpoints/active</strong> - Get active endpoint
                    </div>
                    
                    <div class="endpoint">
                        <strong>GET /stream/{id}</strong> - Stream audio file
                    </div>
                    
                    <h2>Current Endpoints</h2>
                    ${mockEndpoints.joinToString("\n") { endpoint ->
                        val status = endpoint["status"] as String
                        val healthy = endpoint["healthy"] as Boolean
                        """
                        <div class="endpoint">
                            <strong>${endpoint["url"]}</strong>
                            <span class="status ${if (healthy) "healthy" else "unhealthy"}">$status</span>
                        </div>
                        """.trimIndent()
                    }}
                </div>
            </body>
            </html>
        """.trimIndent()
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html",
            html
        )
    }
    
    /**
     * Parses Range header for partial content requests
     */
    private data class Range(val start: Long, val end: Long, val length: Long)
    
    private fun parseRangeHeader(rangeHeader: String, fileLength: Long): Range? {
        val rangePattern = Regex("bytes=(\\d+)-(\\d*)")
        val matchResult = rangePattern.find(rangeHeader)
        
        if (matchResult != null) {
            val start = matchResult.groupValues[1].toLong()
            val end = if (matchResult.groupValues[2].isEmpty()) {
                fileLength - 1
            } else {
                matchResult.groupValues[2].toLong()
            }
            
            if (start < fileLength && end >= start && end < fileLength) {
                return Range(start, end, end - start + 1)
            }
        }
        
        return null
    }
    
    /**
     * Gson instance for JSON serialization
     */
    private val gson = com.google.gson.Gson()
    
    /**
     * Parses POST parameters from session
     */
    private fun parsePostParameters(session: IHTTPSession): Map<String, String> {
        val params = mutableMapOf<String, String>()
        try {
            session.parseBody(mapOf())
            session.parms.forEach { (key, value) ->
                params[key] = value.firstOrNull() ?: ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse POST parameters", e)
        }
        return params
    }
    
    /**
     * Creates a mock audio file for testing
     */
    private fun createMockAudioFile(): File {
        val filesDir = context.filesDir
        val audioDir = File(filesDir, "audio")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        
        val mockFile = File(audioDir, "mock_audio.mp3")
        if (!mockFile.exists()) {
            // Create a small mock file (1MB)
            mockFile.writeBytes(ByteArray(1024 * 1024) { 0 })
        }
        
        return mockFile
    }
    
    /**
     * Stops the server
     */
    fun stopServer() {
        if (isRunning) {
            super.stop()
            isRunning = false
            Log.i(TAG, "Local HTTP server stopped")
        }
    }
    
    /**
     * Checks if server is running
     */
    fun isServerRunning(): Boolean = isRunning
    
    /**
     * Gets server status
     */
    fun getServerStatus(): Map<String, Any> {
        return mapOf(
            "running" to isRunning,
            "port" to 17171,
            "startTime" to System.currentTimeMillis()
        )
    }
}