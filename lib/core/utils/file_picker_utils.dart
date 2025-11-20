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

import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show debugPrint;
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

/// Utility functions for file picking without requiring storage permissions.
///
/// Uses system APIs (Photo Picker on Android 13+, SAF, system camera app)
/// to avoid needing storage permissions in AndroidManifest.xml.
final ImagePicker _imagePicker = ImagePicker();

/// Picks media files (photos/videos) without requiring storage permissions.
///
/// On Android 13+: Uses Photo Picker (no permissions needed)
/// On Android 12 and below: Uses image_picker with system gallery
///
/// Returns a list of selected media files.
Future<List<XFile>> pickMediaSmart({
  bool multiple = true,
  ImageSource source = ImageSource.gallery,
}) async {
  try {
    // Check Android version
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      if (sdkInt >= 33) {
        // Android 13+: Photo Picker, no permissions needed
        if (multiple) {
          return await _imagePicker.pickMultipleMedia();
        } else {
          final media = await _imagePicker.pickMedia();
          return media != null ? [media] : [];
        }
      } else {
        // Android 12 and below: image_picker uses system picker
        if (multiple) {
          return await _imagePicker.pickMultiImage();
        } else {
          final image = await _imagePicker.pickImage(source: source);
          return image != null ? [image] : [];
        }
      }
    } else {
      // iOS: use standard image_picker
      if (multiple) {
        return await _imagePicker.pickMultiImage();
      } else {
        final image = await _imagePicker.pickImage(source: source);
        return image != null ? [image] : [];
      }
    }
  } catch (e) {
    throw Exception('Failed to pick media: $e');
  }
}

/// Picks any files using SAF (Storage Access Framework) without permissions.
///
/// This works on all Android versions without requiring storage permissions.
/// The user selects files through the system document picker.
Future<List<String>> pickAnyFiles({
  List<String>? allowedExtensions,
  bool allowMultiple = true,
  bool withData = false,
}) async {
  try {
    final result = await FilePicker.platform.pickFiles(
      allowMultiple: allowMultiple,
      withData: withData,
      type: allowedExtensions != null ? FileType.custom : FileType.any,
      allowedExtensions: allowedExtensions,
    );

    if (result?.files != null) {
      return result!.files
          .map((file) => file.path)
          .whereType<String>()
          .toList();
    }
    return [];
  } catch (e) {
    // Handle "already_active" error - file picker is already open
    if (e.toString().contains('already_active')) {
      // Return empty list instead of throwing - caller should handle this gracefully
      return [];
    }
    throw Exception('Failed to pick files: $e');
  }
}

/// Picks a directory using SAF without permissions.
///
/// Returns the path or URI to the selected directory.
/// On Android 13+ (API 33+), uses native SAF implementation with proper permission handling.
/// On Android 11-12 (API 30-32), uses FilePicker plugin.
/// On older Android versions and iOS, uses FilePicker plugin.
/// The method may return null if the user cancels or if there's an issue.
Future<String?> pickDirectory() async {
  try {
    // On Android, check if we're on a version that supports SAF properly
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;
      
      debugPrint('[file_picker_utils] Picking directory on Android SDK $sdkInt');
      
      // Android 13+ (API 33+): Use native SAF implementation with proper permission handling
      // This ensures takePersistableUriPermission is called correctly
      if (sdkInt >= 33) {
        try {
          const methodChannel = MethodChannel('directory_picker_channel');
          final result = await methodChannel.invokeMethod<String>('pickDirectory');
          
          if (result == null || result.isEmpty) {
            debugPrint('[file_picker_utils] Directory picker returned null on Android SDK $sdkInt (user cancelled)');
            return null;
          }
          
          debugPrint('[file_picker_utils] Directory selected via native SAF: $result');
          // Result is a URI string (e.g., "content://com.android.externalstorage.documents/tree/primary%3ADownload")
          // This URI can be used with DocumentFile or ContentResolver for file operations
          return result;
        } on PlatformException catch (e) {
          debugPrint('[file_picker_utils] Native directory picker error: ${e.code} - ${e.message}');
          // Fallback to FilePicker if native method fails
          debugPrint('[file_picker_utils] Falling back to FilePicker plugin');
        } on Exception catch (e) {
          debugPrint('[file_picker_utils] Exception in native directory picker: $e');
          // Fallback to FilePicker if native method fails
          debugPrint('[file_picker_utils] Falling back to FilePicker plugin');
        }
      }
      
      // Android 11-12 (API 30-32) or fallback: Use FilePicker plugin
      // Android 11+ (API 30+) uses Scoped Storage, SAF is required
      // getDirectoryPath() should work, but may return null silently
      final directory = await FilePicker.platform.getDirectoryPath();
      
      if (directory == null) {
        debugPrint('[file_picker_utils] Directory picker returned null on Android SDK $sdkInt');
        // On Android 11+, if null is returned, it might be due to:
        // 1. User cancelled (normal - this is expected)
        // 2. SAF permission issue (should be handled by system)
        // 3. Platform issue
        // We return null gracefully - caller should handle this
        return null;
      }
      
      debugPrint('[file_picker_utils] Directory selected: $directory');
      return directory;
    } else {
      // iOS and other platforms
      debugPrint('[file_picker_utils] Picking directory on ${Platform.operatingSystem}');
      final directory = await FilePicker.platform.getDirectoryPath();
      if (directory == null) {
        debugPrint('[file_picker_utils] Directory picker returned null on ${Platform.operatingSystem}');
      } else {
        debugPrint('[file_picker_utils] Directory selected: $directory');
      }
      return directory;
    }
  } catch (e, stackTrace) {
    // Handle "already_active" error - file picker is already open
    if (e.toString().contains('already_active') ||
        e.toString().contains('already active')) {
      debugPrint('[file_picker_utils] File picker is already active');
      // Return null instead of throwing - caller should handle this gracefully
      return null;
    }
    
    debugPrint('[file_picker_utils] Error picking directory: $e');
    debugPrint('[file_picker_utils] Stack trace: $stackTrace');
    
    // On Android, some errors might be platform-specific
    if (Platform.isAndroid) {
      // Log the error for debugging but don't throw
      // The caller should handle null gracefully
      return null;
    }
    
    throw Exception('Failed to pick directory: $e');
  }
}

/// Picks audio files specifically (for audiobook files).
///
/// Uses SAF to avoid storage permissions.
Future<List<String>> pickAudioFiles({bool allowMultiple = true}) =>
    pickAnyFiles(
      allowedExtensions: ['mp3', 'm4a', 'aac', 'flac', 'wav', 'ogg'],
      allowMultiple: allowMultiple,
    );

/// Picks image files specifically.
///
/// Uses Photo Picker on Android 13+ or SAF on older versions.
Future<List<String>> pickImageFiles({bool allowMultiple = true}) async {
  if (Platform.isAndroid) {
    final androidInfo = await DeviceInfoPlugin().androidInfo;
    final sdkInt = androidInfo.version.sdkInt;

    if (sdkInt >= 33) {
      // Use Photo Picker for images
      final media = await pickMediaSmart(multiple: allowMultiple);
      return media.map((file) => file.path).toList();
    } else {
      // Use SAF for older Android versions
      return pickAnyFiles(
        allowedExtensions: ['jpg', 'jpeg', 'png', 'gif', 'webp'],
        allowMultiple: allowMultiple,
      );
    }
  } else {
    // iOS: use image_picker
    final media = await pickMediaSmart(multiple: allowMultiple);
    return media.map((file) => file.path).toList();
  }
}

/// Takes a photo using the system camera app.
///
/// No camera permission needed as it uses the system camera app.
Future<String?> takePhoto() async {
  try {
    final image = await _imagePicker.pickImage(source: ImageSource.camera);
    return image?.path;
  } catch (e) {
    throw Exception('Failed to take photo: $e');
  }
}
