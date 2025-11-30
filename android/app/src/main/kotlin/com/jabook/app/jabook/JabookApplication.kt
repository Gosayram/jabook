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

package com.jabook.app.jabook

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Jabook with Dagger Hilt support.
 *
 * This class initializes Dagger Hilt for dependency injection.
 * Inspired by lissen-android implementation.
 */
@HiltAndroidApp
class JabookApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("JabookApplication", "Application created with Hilt support")
    }
}
