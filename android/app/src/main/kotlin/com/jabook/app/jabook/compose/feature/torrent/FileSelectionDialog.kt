// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.feature.torrent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.torrent.TorrentFile

/**
 * Node representing a file or directory in the torrent
 */
public data class FileNode(
    public val name: String,
    public val path: String,
    public val size: Long,
    public val fileIndex: Int?, // null for directories
    public val children: List<FileNode> = emptyList(),
) {
    public val isDirectory: Boolean get() = fileIndex == null
}

/**
 * Dialog for selecting files to download
 */
@Composable
public fun FileSelectionDialog(
    files: List<TorrentFile>,
    onConfirm: (Set<Int>) -> Unit, // Returns set of selected file indices
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Build tree structure
    val rootNodes = remember(files) { buildFileTree(files) }

    // State for selection and expansion
    // Map of path -> isSelected
    // For directories, this means all children are selected
    val selectionState = remember { mutableStateMapOf<String, Boolean>() }
    val trailingSelectionState = remember { mutableStateMapOf<String, Boolean>() } // For restoring selection when unchecked
    val expansionState = remember { mutableStateMapOf<String, Boolean>() }

    // Initialize selection (select all by default)
    remember(files) {
        files.forEach { file ->
            selectionState[file.path] = true
        }
        true
    }

    // Helper to check if a node is selected (considering parent selection logic if needed)
    // Here we use a flat map for simplicity, where each file key is present

    // Calculate total size
    val selectedSize by remember {
        derivedStateOf {
            files.filter { selectionState[it.path] == true }.sumOf { it.size }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_files)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.selected_size_format, formatSize(selectedSize)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(
                        items = rootNodes,
                        key = { node -> node.path },
                    ) { node ->
                        FileNodeItem(
                            node = node,
                            depth = 0,
                            selectionState = selectionState,
                            expansionState = expansionState,
                            onToggleSelection = { path, isSelected ->
                                toggleSelection(path, isSelected, rootNodes, selectionState)
                            },
                            onToggleExpansion = { path ->
                                expansionState[path] = !(expansionState[path] ?: false)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedIndices =
                        files
                            .filter { selectionState[it.path] == true }
                            .mapNotNull { it.index }
                            .toSet()
                    onConfirm(selectedIndices)
                },
            ) {
                Text(stringResource(R.string.download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun FileNodeItem(
    node: FileNode,
    depth: Int,
    selectionState: MutableMap<String, Boolean>,
    expansionState: MutableMap<String, Boolean>,
    onToggleSelection: (String, Boolean) -> Unit,
    onToggleExpansion: (String) -> Unit,
) {
    val isExpanded = expansionState[node.path] ?: false

    // Determine selection state
    // For directories:
    // - true if ALL children selected
    // - false if NO children selected
    // - null (tri-state) if SOME children selected
    public val isSelected: Boolean? =
        if (node.isDirectory) {
            val childrenStates = getAllFilePaths(node).map { selectionState[it] ?: false }
            if (childrenStates.all { it }) {
                true
            } else if (childrenStates.none { it }) {
                false
            } else {
                null // Mixed
            }
        } else {
            selectionState[node.path] ?: false
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) {
                        onToggleExpansion(node.path)
                    } else {
                        onToggleSelection(node.path, !(isSelected == true))
                    }
                }.padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp),
    ) {
        // Expand/Collapse icon for directories
        if (node.isDirectory) {
            IconButton(
                onClick = { onToggleExpansion(node.path) },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                )
            }
        } else {
            Spacer(Modifier.width(24.dp))
        }

        // Checkbox
        Checkbox(
            checked = isSelected == true,
            onCheckedChange = { checked ->
                onToggleSelection(node.path, checked)
            },
            modifier = Modifier.size(24.dp),
            // Note: Compose Material3 Checkbox doesn't support tri-state visual natively easily without custom implementation
            // or TriStateCheckbox. Let's use standard for now, but TriState would be better.
        )

        Spacer(Modifier.width(8.dp))

        // Icon
        Icon(
            imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (node.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )

        Spacer(Modifier.width(8.dp))

        // Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatSize(node.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Recursively render children if expanded
    if (isExpanded && node.children.isNotEmpty()) {
        node.children.forEach { child ->
            FileNodeItem(
                node = child,
                depth = depth + 1,
                selectionState = selectionState,
                expansionState = expansionState,
                onToggleSelection = onToggleSelection,
                onToggleExpansion = onToggleExpansion,
            )
        }
    }
}

/**
 * Helper to toggle selection recursively
 */
private fun toggleSelection(
    path: String,
    isSelected: Boolean,
    rootNodes: List<FileNode>,
    selectionState: MutableMap<String, Boolean>,
) {
    val node = findNode(rootNodes, path) ?: return

    if (node.isDirectory) {
        // Toggle all children
        val allFiles = getAllFilePaths(node)
        allFiles.forEach { filePath ->
            selectionState[filePath] = isSelected
        }
    } else {
        selectionState[path] = isSelected
    }
}

private fun findNode(
    nodes: List<FileNode>,
    path: String,
): FileNode? {
    for (node in nodes) {
        if (node.path == path) return node
        if (node.children.isNotEmpty()) {
            val found = findNode(node.children, path)
            if (found != null) return found
        }
    }
    return null
}

private fun getAllFilePaths(node: FileNode): List<String> {
    if (!node.isDirectory) return listOf(node.path)
    return node.children.flatMap { getAllFilePaths(it) }
}

/**
 * Builds a tree from a flat list of files
 */
private fun buildFileTree(files: List<TorrentFile>): List<FileNode> {
    val root = FileNode("root", "", 0, null, mutableListOf())
    val rootChildren = mutableListOf<FileNode>()

    // Simple implementation assuming paths use "/" separator
    // This can be optimized, but works for basic structures

    // Group by directory structure is complex with flat list.
    // Let's implement a simplified builder.

    val nodeMap = mutableMapOf<String, MutableList<FileNode>>() // ParentPath -> Children

    // First, identify all unique directories and files

    // This is getting complicated to do inline. Let's do a recursive build or path splitting.
    // Simplest: Split path by /, build hierarchy

    val rootNodes = mutableListOf<FileNode>()

    // Helper class to build tree temporarily
    public data class TempNode(
        public val name: String,
        var size: Int = ,
        var fileIndex: Int? = null,
        public val children: MutableMap<String, TempNode> = mutableMapOf(),
    )

    val rootTemp = TempNode("root")

    files.forEach { file ->
        val parts = file.path.split("/")
        var current = rootTemp

        parts.forEachIndexed { index, part ->
            val isFile = index == parts.lastIndex

            val child =
                current.children.getOrPut(part) {
                    TempNode(part)
                }

            if (isFile) {
                child.size = file.size
                child.fileIndex = file.index
            } else {
                child.size += file.size // Accumulate size for directories (will be partial until full traversal)
            }
            current = child
        }
    }

    // Recalculate directory sizes correctly (sum of children)
    public fun calculateSizes(node: TempNode): Long {
        if (node.children.isEmpty()) return node.size

        val childrenSize = node.children.values.sumOf { calculateSizes(it) }
        node.size = childrenSize
        return childrenSize
    }

    calculateSizes(rootTemp)

    // Convert to FileNode
    public fun convert(
        temp: TempNode,
        parentPath: String,
    ): FileNode {
        val path = if (parentPath.isEmpty()) temp.name else "$parentPath/${temp.name}"
        return FileNode(
            name = temp.name,
            path = path,
            size = temp.size,
            fileIndex = temp.fileIndex,
            children =
                temp.children.values
                    .map { convert(it, path) }
                    .sortedBy { !it.isDirectory },
            // Dirs first
        )
    }

    return rootTemp.children.values
        .map { convert(it, "") }
        .sortedBy { !it.isDirectory }
}

@Composable
private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> stringResource(R.string.size_gb, gb)
        mb >= 1.0 -> stringResource(R.string.size_mb, mb)
        kb >= 1.0 -> stringResource(R.string.size_kb, kb)
        else -> stringResource(R.string.size_bytes, bytes)
    }
}
