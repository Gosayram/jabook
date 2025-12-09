package com.jabook.app.jabook.handlers

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class ContentUriMethodHandler(
    private val context: Context,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "content_uri_channel")

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "listDirectory" -> {
                val uriString = call.argument<String>("uri")
                if (uriString != null) {
                    try {
                        val uri = Uri.parse(uriString)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            val files = listDirectoryViaContentResolver(uri)
                            result.success(files)
                        } else {
                            result.error("UNSUPPORTED", "Requires Android Lollipop+", null)
                        }
                    } catch (e: Exception) {
                        result.error("LIST_ERROR", e.message, null)
                    }
                } else {
                    result.error("INVALID_ARGUMENT", "URI is required", null)
                }
            }
            "checkUriAccess" -> {
                val uriString = call.argument<String>("uri")
                if (uriString != null) {
                    try {
                        val uri = Uri.parse(uriString)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            val hasAccess = checkUriAccess(uri)
                            result.success(hasAccess)
                        } else {
                            // Pre-Lollipop we use regular File/Path access usually, but for SAF it's strictly > L
                            result.success(false)
                        }
                    } catch (e: Exception) {
                        result.error("CHECK_ERROR", e.message, null)
                    }
                } else {
                    result.error("INVALID_ARGUMENT", "URI is required", null)
                }
            }
            else -> result.notImplemented()
        }
    }

    fun stopListening() {
        channel.setMethodCallHandler(null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun listDirectoryViaContentResolver(uri: Uri): List<Map<String, String>> {
        val files = mutableListOf<Map<String, String>>()

        try {
            val contentResolver = context.contentResolver

            // Use DocumentsContract for tree URIs
            val childrenUri: Uri =
                run {
                    if (DocumentsContract.isTreeUri(uri)) {
                        val treeDocumentId = DocumentsContract.getTreeDocumentId(uri)
                        DocumentsContract.buildChildDocumentsUriUsingTree(uri, treeDocumentId)
                    } else {
                        try {
                            val documentId = DocumentsContract.getDocumentId(uri)
                            // Try to find the tree URI from persisted permissions
                            val persistedPermissions = contentResolver.persistedUriPermissions
                            val treeUri =
                                persistedPermissions
                                    .firstOrNull { perm ->
                                        DocumentsContract.isTreeUri(perm.uri) &&
                                            (
                                                documentId.startsWith(
                                                    "${DocumentsContract.getTreeDocumentId(perm.uri)}/",
                                                ) ||
                                                    documentId == DocumentsContract.getTreeDocumentId(perm.uri)
                                            )
                                    }?.uri

                            if (treeUri != null) {
                                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
                            } else {
                                DocumentsContract.buildChildDocumentsUri(uri.authority ?: "", documentId)
                            }
                        } catch (e: Exception) {
                            try {
                                val documentId = DocumentsContract.getDocumentId(uri)
                                DocumentsContract.buildChildDocumentsUri(uri.authority ?: "", documentId)
                            } catch (e2: Exception) {
                                throw Exception("Cannot list directory: ${e.message}", e)
                            }
                        }
                    }
                }

            val cursor =
                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                    ),
                    null,
                    null,
                    null,
                )

            if (cursor == null) {
                return files
            }

            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (it.moveToNext()) {
                    val documentId = it.getString(idColumn)
                    val name = it.getString(nameColumn)
                    val mimeType = it.getString(mimeColumn)
                    val size = it.getLong(sizeColumn)

                    // Build child URI
                    val childUri =
                        if (DocumentsContract.isTreeUri(uri)) {
                            DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                        } else {
                            val persistedPermissions = contentResolver.persistedUriPermissions
                            val treeUri =
                                persistedPermissions
                                    .firstOrNull { perm ->
                                        DocumentsContract.isTreeUri(perm.uri)
                                    }?.uri

                            if (treeUri != null) {
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            } else {
                                DocumentsContract.buildDocumentUri(uri.authority ?: "", documentId)
                            }
                        }

                    files.add(
                        mapOf(
                            "uri" to childUri.toString(),
                            "name" to (name ?: ""),
                            "mimeType" to (mimeType ?: ""),
                            "size" to size.toString(),
                            "isDirectory" to (mimeType == DocumentsContract.Document.MIME_TYPE_DIR).toString(),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ContentUriHandler", "Error listing directory via ContentResolver", e)
            throw e
        }

        return files
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun checkUriAccess(uri: Uri): Boolean {
        try {
            val contentResolver = context.contentResolver
            val persistedUriPermissions = contentResolver.persistedUriPermissions

            val normalizedUri = uri.normalizeScheme()

            // Step 1: Check exact match
            var hasPermission =
                persistedUriPermissions.any {
                    val persistedUri = it.uri.normalizeScheme()
                    val uriMatches = persistedUri == normalizedUri
                    val hasReadOrWrite = it.isReadPermission || it.isWritePermission
                    uriMatches && hasReadOrWrite
                }

            // Step 2: Check tree match
            if (!hasPermission) {
                try {
                    val isTreeUri = DocumentsContract.isTreeUri(uri)
                    if (isTreeUri) {
                        val treeDocumentId = DocumentsContract.getTreeDocumentId(uri)
                        hasPermission =
                            persistedUriPermissions.any { perm ->
                                if (DocumentsContract.isTreeUri(perm.uri)) {
                                    val persistedTreeId = DocumentsContract.getTreeDocumentId(perm.uri)
                                    val isPartOfTree =
                                        treeDocumentId == persistedTreeId ||
                                            treeDocumentId.startsWith("$persistedTreeId/")
                                    val hasReadOrWrite = perm.isReadPermission || perm.isWritePermission
                                    isPartOfTree && hasReadOrWrite
                                } else {
                                    false
                                }
                            }
                    } else {
                        // Check document URI against tree
                        try {
                            val documentId = DocumentsContract.getDocumentId(uri)
                            hasPermission =
                                persistedUriPermissions.any { perm ->
                                    if (DocumentsContract.isTreeUri(perm.uri)) {
                                        val persistedTreeId = DocumentsContract.getTreeDocumentId(perm.uri)
                                        val isUnderTree =
                                            documentId.startsWith("$persistedTreeId/") ||
                                                documentId == persistedTreeId
                                        val hasReadOrWrite = perm.isReadPermission || perm.isWritePermission
                                        isUnderTree && hasReadOrWrite
                                    } else {
                                        false
                                    }
                                }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            // Step 3: Query verification
            if (!hasPermission) {
                try {
                    val cursor =
                        contentResolver.query(
                            uri,
                            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                            null,
                            null,
                            null,
                        )
                    if (cursor != null) {
                        cursor.close()
                        hasPermission = true
                    }
                } catch (e: SecurityException) {
                    // ignore
                } catch (e: Exception) {
                    // ignore
                }
            }

            return hasPermission
        } catch (e: Exception) {
            return false
        }
    }
}
