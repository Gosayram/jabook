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

package com.jabook.app.jabook.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

/**
 * Temporary test activity for Compose UI development.
 *
 * This activity allows testing the Compose UI in parallel with the existing
 * Flutter implementation. Once the Compose UI is complete and tested,
 * MainActivity will be migrated to use this pattern.
 *
 * To test this activity:
 * 1. Add to AndroidManifest.xml temporarily with a launcher intent
 * 2. Or launch programmatically from MainActivity for A/B testing
 *
 * @see MainActivity for the current Flutter-based implementation
 */
@AndroidEntryPoint
class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge display (Android 15+ recommended pattern)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            JabookApp()
        }
    }
}
