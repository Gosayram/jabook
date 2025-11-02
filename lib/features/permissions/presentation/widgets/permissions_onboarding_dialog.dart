import 'package:flutter/material.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Onboarding dialog that explains why permissions are needed.
///
/// This dialog is shown on first launch to help users understand
/// why the app needs various permissions. It provides context before
/// Android's system permission dialogs appear.
class PermissionsOnboardingDialog extends StatelessWidget {
  /// Creates a new PermissionsOnboardingDialog.
  const PermissionsOnboardingDialog({super.key});

  /// Shows the permissions onboarding dialog.
  ///
  /// Returns [true] if user wants to proceed with permission requests,
  /// [false] if user cancels.
  static Future<bool> show(BuildContext context) async {
    final result = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (context) => const PermissionsOnboardingDialog(),
    );
    return result ?? false;
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return AlertDialog(
      title: const Text('Разрешения для JaBook'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _PermissionItem(
              icon: Icons.folder_outlined,
              title: 'Доступ к файлам',
              description: 'Нужен для сохранения и воспроизведения аудиокниг.',
            ),
            const SizedBox(height: 16),
            const _PermissionItem(
              icon: Icons.notifications_outlined,
              title: 'Уведомления',
              description: 'Для управления воспроизведением из уведомлений.',
            ),
            const SizedBox(height: 16),
            const _PermissionItem(
              icon: Icons.battery_saver_outlined,
              title: 'Оптимизация батареи',
              description:
                  'Чтобы приложение работало в фоне для воспроизведения.',
            ),
            const SizedBox(height: 16),
            Text(
              'Эти разрешения помогут обеспечить лучший опыт использования приложения.',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: Text(localizations?.cancel ?? 'Отмена'),
        ),
        ElevatedButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: const Text('Продолжить'),
        ),
      ],
    );
  }
}

/// Widget for displaying a permission explanation item.
class _PermissionItem extends StatelessWidget {
  const _PermissionItem({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 24, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ],
      );
}
