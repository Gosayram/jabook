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

package com.jabook.app.jabook.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity for Android TV.
 *
 * This activity hosts the TV browsing experience using Leanback library.
 * It provides a 10-foot UI optimized for large screens and D-pad navigation.
 *
 * Features:
 * - Browse audiobook library with card-based UI
 * - D-pad navigation support
 * - Integration with existing AudioPlayerService
 * - Media session for TV remote controls
 */
@AndroidEntryPoint
public class TvActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the main TV browse fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, TvBrowseFragment())
                .commitNow()
        }
    }
}
