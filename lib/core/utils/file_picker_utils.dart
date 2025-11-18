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
/// Returns the path to the selected directory.
Future<String?> pickDirectory() async {
  try {
    return FilePicker.platform.getDirectoryPath();
  } catch (e) {
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
