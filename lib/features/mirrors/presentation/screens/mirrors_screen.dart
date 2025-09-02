import 'package:flutter/material.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for displaying and managing RuTracker mirror URLs.
///
/// This screen provides a list of available RuTracker mirror URLs
/// and allows users to test connectivity and select preferred mirrors.
class MirrorsScreen extends StatelessWidget {
  /// Creates a new MirrorsScreen instance.
  ///
  /// The [key] parameter is optional and can be used to identify
  /// this widget in the widget tree.
  const MirrorsScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
      body: Center(
        child: Text(AppLocalizations.of(context)?.mirrorsScreenTitle ?? 'Mirrors Screen'),
      ),
    );
}