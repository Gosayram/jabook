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

package com.jabook.app.jabook.audio.bridge

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import javax.inject.Inject

/**
 * Unified bridge for Flutter communication.
 *
 * Combines MethodChannel and EventChannel into a single entry point.
 */
class FlutterBridge
    @Inject
    constructor(
        private val methodChannelHandler: MethodChannelHandler,
        private val eventChannelHandler: EventChannelHandler,
    ) {
        companion object {
            const val METHOD_CHANNEL_NAME = "com.jabook.app.jabook/audio_player"
            const val EVENT_CHANNEL_NAME = "com.jabook.app.jabook/audio_player_events"
        }

        /**
         * Registers the bridge with Flutter plugin.
         */
        fun registerWith(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
            // Register MethodChannel
            val methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, METHOD_CHANNEL_NAME)
            methodChannel.setMethodCallHandler(methodChannelHandler)

            // Register EventChannel
            val eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, EVENT_CHANNEL_NAME)
            eventChannel.setStreamHandler(
                object : EventChannel.StreamHandler {
                    override fun onListen(
                        arguments: Any?,
                        events: EventChannel.EventSink,
                    ) {
                        eventChannelHandler.setEventSink(events)
                    }

                    override fun onCancel(arguments: Any?) {
                        eventChannelHandler.clearEventSink()
                    }
                },
            )
        }
    }
