import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/material.dart';
import 'package:jabook/core/logging/structured_logger.dart';

/// Service for managing app permissions.
///
/// This service handles requesting and checking various permissions
/// required for the app to function properly.
class PermissionService {
  /// Private constructor for singleton pattern.
  PermissionService._();

  /// Factory constructor to get the singleton instance.
  factory PermissionService() => _instance;

  /// Singleton instance of the PermissionService.
  static final PermissionService _instance = PermissionService._();

  /// Logger instance for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Checks if storage permission is granted.
  ///
  /// Returns `true` if storage permission is granted, `false` otherwise.
  Future<bool> hasStoragePermission() async {
    try {
      // For Android 13+, we need to use photos instead of storage
      final status = await Permission.photos.status;
      if (status.isGranted) return true;

      // For older Android versions
      final storageStatus = await Permission.storage.status;
      return storageStatus.isGranted;
    } catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking storage permission',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Requests storage permission.
  ///
  /// Returns `true` if permission was granted, `false` otherwise.
  Future<bool> requestStoragePermission() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Requesting storage permission',
      );

      // For Android 13+, request photos permission
      final photosStatus = await Permission.photos.request();
      if (photosStatus.isGranted) {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'Photos permission granted',
        );
        return true;
      }

      // For older Android versions, request storage permission
      final storageStatus = await Permission.storage.request();
      
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Storage permission result: ${storageStatus.name}',
      );

      return storageStatus.isGranted;
    } catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error requesting storage permission',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if all required permissions are granted.
  ///
  /// Returns `true` if all permissions are granted, `false` otherwise.
  Future<bool> hasAllPermissions() async {
    // Internet permission is automatically granted on Android
    // We only need to check storage permission
    final hasStorage = await hasStoragePermission();
    
    return hasStorage;
  }

  /// Requests all required permissions.
  ///
  /// Returns `true` if all permissions were granted, `false` otherwise.
  Future<bool> requestAllPermissions() async {
    // Internet permission is automatically granted on Android
    // We only need to request storage permission
    final storageGranted = await requestStoragePermission();
    
    return storageGranted;
  }

  /// Shows a dialog explaining why a permission is needed.
  ///
  /// Returns `true` if user accepts, `false` otherwise.
  Future<bool> showPermissionDialog({
    required BuildContext context,
    required String title,
    required String message,
    required Future<bool> Function() requestPermission,
  }) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text('Allow'),
          ),
        ],
      ),
    );

    if (result == true) {
      return await requestPermission();
    }

    return false;
  }

  /// Opens app settings so user can manually grant permissions.
  Future<void> openAppSettings() async {
    try {
      await openAppSettings();
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Opened app settings',
      );
    } catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error opening app settings',
        cause: e.toString(),
      );
    }
  }
}