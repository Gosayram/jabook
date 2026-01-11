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

package com.jabook.app.jabook.compose.data.model

public sealed class ScanProgress {
    data object Idle : ScanProgress()

    public data class Discovery(
        val fileCount: Int,
    ) : ScanProgress()

    public data class Parsing(
        val currentBook: String,
        val progress: Int,
        val total: Int,
    ) : ScanProgress()

    data object Saving : ScanProgress()

    public data class Completed(
        val booksAdded: Int,
        val durationMs: Long,
    ) : ScanProgress()

    public data class Error(
        val message: String,
    ) : ScanProgress()
}
