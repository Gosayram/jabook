// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.util

import java.io.File

object FileUtils {
    /**
     * Calculate total size of a directory recursively
     */
    fun getDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0
        if (!directory.isDirectory) return directory.length()

        var length: Long = 0
        directory.listFiles()?.forEach { file ->
            length += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return length
    }
}
