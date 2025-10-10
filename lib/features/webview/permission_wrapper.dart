import 'package:flutter/material.dart';
import 'package:jabook/core/permissions/permission_service.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// A wrapper widget that ensures required permissions are granted
/// before allowing the child widget to be used.
class PermissionWrapper extends StatefulWidget {
  @override
  State<PermissionWrapper> createState() => _PermissionWrapperState();

  /// The child widget that requires permissions.
  final Widget child;

  /// A callback to call when permissions are granted.
  final VoidCallback? onPermissionsGranted;

  const PermissionWrapper({
    super.key,
    required this.child,
    this.onPermissionsGranted,
  });
}

/// State class for PermissionWrapper widget.
class _PermissionWrapperState extends State<PermissionWrapper> {
  final PermissionService _permissionService = PermissionService();
  bool _permissionsGranted = false;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  /// Checks if all required permissions are granted.
  Future<void> _checkPermissions() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final granted = await _permissionService.hasAllPermissions() ?? false;
      setState(() {
        _permissionsGranted = granted;
        _isLoading = false;
      });

      if (granted && widget.onPermissionsGranted != null) {
        widget.onPermissionsGranted!();
      }
    } on Exception catch (_) {
      setState(() {
        _permissionsGranted = false;
        _isLoading = false;
      });
    }
  }

  /// Requests all required permissions.
  Future<void> _requestPermissions() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final granted = await _permissionService.requestAllPermissions() ?? false;
      setState(() {
        _permissionsGranted = granted;
        _isLoading = false;
      });

      if (granted && widget.onPermissionsGranted != null) {
        widget.onPermissionsGranted!();
      }
    } on Exception catch (_) {
      setState(() {
        _permissionsGranted = false;
        _isLoading = false;
      });
    }
  }

  /// Shows permission denied dialog.
  void _showPermissionDeniedDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppLocalizations.of(context)!.permissionDeniedTitle ?? 'Permission Denied'),
        content: Text(AppLocalizations.of(context)!.permissionDeniedMessage ?? 'Permissions are required to use this feature.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(AppLocalizations.of(context)!.cancel ?? 'Cancel'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(context);
              await _permissionService.openAppSettings();
            },
            child: Text(AppLocalizations.of(context)!.permissionDeniedButton ?? 'Open Settings'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    if (!_permissionsGranted) {
      return _buildPermissionScreen();
    }

    return widget.child;
  }

  /// Builds the permission request screen.
  Widget _buildPermissionScreen() => Scaffold(
    appBar: AppBar(
      title: Text(AppLocalizations.of(context)!.permissionsRequired ?? 'Permissions Required'),
    ),
    body: Center(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.security,
              size: 80,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(height: 24),
            Text(
              AppLocalizations.of(context)!.permissionExplanation ?? 'This feature requires certain permissions to function properly.',
              style: Theme.of(context).textTheme.bodyLarge,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 32),
            ElevatedButton(
              onPressed: _requestPermissions,
              child: Text(AppLocalizations.of(context)!.grantPermissions ?? 'Grant Permissions'),
            ),
            const SizedBox(height: 16),
            TextButton(
              onPressed: _showPermissionDeniedDialog,
              child: Text(AppLocalizations.of(context)!.permissionDeniedButton ?? 'Open Settings'),
            ),
          ],
        ),
      ),
    ),
  );
}