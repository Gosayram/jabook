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

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:jabook/features/library/presentation/widgets/delete_confirmation_dialog.dart';
import 'package:jabook/l10n/app_localizations.dart';

void main() {
  group('DeleteConfirmationDialog Widget Tests', () {
    /// Helper to create a test LocalAudiobook
    LocalAudiobook createTestAudiobook({
      required String filePath,
      String? fileName,
      int? fileSize,
    }) =>
        LocalAudiobook(
          filePath: filePath,
          fileName: fileName ?? 'test.mp3',
          fileSize: fileSize ?? 1024,
          title: 'Test Audiobook',
          author: 'Test Author',
        );

    /// Helper to create a test LocalAudiobookGroup
    LocalAudiobookGroup createTestGroup({
      required String groupPath,
      required String groupName,
      int fileCount = 3,
      int? fileSize,
    }) {
      final files = List.generate(
        fileCount,
        (i) => createTestAudiobook(
          filePath: '$groupPath/file_$i.mp3',
          fileName: 'file_$i.mp3',
          fileSize: fileSize ?? 1024,
        ),
      );

      return LocalAudiobookGroup(
        groupPath: groupPath,
        groupName: groupName,
        files: files,
      );
    }

    /// Helper to build MaterialApp with dialog
    Widget buildTestApp(Widget dialog) => MaterialApp(
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [
            Locale('en', ''),
            Locale('ru', ''),
          ],
          locale: const Locale('en'),
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () => showDialog(
                  context: context,
                  builder: (_) => dialog,
                ),
                child: const Text('Show Dialog'),
              ),
            ),
          ),
        );

    group('Display', () {
      testWidgets('should display dialog with correct title', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(find.text('Delete Audiobook?'), findsOneWidget);
      });

      testWidgets('should display group name', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'My Test Group',
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(find.text('My Test Group'), findsOneWidget);
      });

      testWidgets('should display file count', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
          fileCount: 5,
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(find.text('5 files'), findsOneWidget);
      });

      testWidgets('should display file count as singular for one file',
          (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
          fileCount: 1,
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(find.text('1 file'), findsOneWidget);
      });

      testWidgets('should display total size from group', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
          fileSize: 1024 * 1024, // 1 MB per file
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        // Total size should be 3 MB = 3.0 MB
        expect(find.textContaining('MB'), findsOneWidget);
      });

      testWidgets('should display total size from parameter when provided',
          (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
          fileCount: 2,
          fileSize: 1024,
        );
        final dialog = DeleteConfirmationDialog(
          group: group,
          totalSize: 5 * 1024 * 1024, // 5 MB
        );

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        // Should show 5.0 MB from parameter, not calculated from group
        expect(find.textContaining('5.0 MB'), findsOneWidget);
      });

      testWidgets('should display warning message', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(
          find.text(
            'Files will be permanently deleted and cannot be recovered.',
          ),
          findsOneWidget,
        );
      });

      testWidgets('should display warning icon', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(find.byIcon(Icons.warning), findsOneWidget);
      });
    });

    group('Interaction', () {
      testWidgets(
          'should return DeleteAction.deleteFiles when delete button is pressed',
          (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
        );

        // Act
        await tester.pumpWidget(
          MaterialApp(
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
            ],
            supportedLocales: const [Locale('en', '')],
            locale: const Locale('en'),
            home: Scaffold(
              body: Builder(
                builder: (context) => ElevatedButton(
                  onPressed: () async {
                    final result = await DeleteConfirmationDialog.show(
                      context,
                      group,
                    );
                    expect(result, equals(DeleteAction.deleteFiles));
                  },
                  child: const Text('Show Dialog'),
                ),
              ),
            ),
          ),
        );

        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Tap delete button
        await tester.tap(find.text('Delete Files'));
        await tester.pumpAndSettle();

        // Assert - dialog should be closed
        expect(find.text('Delete Audiobook?'), findsNothing);
      });

      testWidgets(
          'should return DeleteAction.removeFromLibrary when remove button is pressed',
          (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
        );

        // Act
        await tester.pumpWidget(
          MaterialApp(
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
            ],
            supportedLocales: const [Locale('en', '')],
            locale: const Locale('en'),
            home: Scaffold(
              body: Builder(
                builder: (context) => ElevatedButton(
                  onPressed: () async {
                    final result = await DeleteConfirmationDialog.show(
                      context,
                      group,
                    );
                    expect(result, equals(DeleteAction.removeFromLibrary));
                  },
                  child: const Text('Show Dialog'),
                ),
              ),
            ),
          ),
        );

        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Tap remove from library button
        await tester.tap(find.text('Remove from Library'));
        await tester.pumpAndSettle();

        // Assert - dialog should be closed
        expect(find.text('Delete Audiobook?'), findsNothing);
      });

      testWidgets(
          'should return DeleteAction.cancel when cancel button is pressed',
          (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
        );

        // Act
        await tester.pumpWidget(
          MaterialApp(
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
            ],
            supportedLocales: const [Locale('en', '')],
            locale: const Locale('en'),
            home: Scaffold(
              body: Builder(
                builder: (context) => ElevatedButton(
                  onPressed: () async {
                    final result = await DeleteConfirmationDialog.show(
                      context,
                      group,
                    );
                    expect(result, equals(DeleteAction.cancel));
                  },
                  child: const Text('Show Dialog'),
                ),
              ),
            ),
          ),
        );

        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Tap cancel button
        await tester.tap(find.text('Cancel'));
        await tester.pumpAndSettle();

        // Assert - dialog should be closed
        expect(find.text('Delete Audiobook?'), findsNothing);
      });

      testWidgets('should return null when dialog is dismissed',
          (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
        );

        // Act
        await tester.pumpWidget(
          MaterialApp(
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
            ],
            supportedLocales: const [Locale('en', '')],
            locale: const Locale('en'),
            home: Scaffold(
              body: Builder(
                builder: (context) => ElevatedButton(
                  onPressed: () async {
                    final result = await DeleteConfirmationDialog.show(
                      context,
                      group,
                    );
                    expect(result, isNull);
                  },
                  child: const Text('Show Dialog'),
                ),
              ),
            ),
          ),
        );

        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Dismiss dialog by tapping outside or back button
        await tester.tapAt(const Offset(10, 10));
        await tester.pumpAndSettle();

        // Assert - dialog should be closed
        expect(find.text('Delete Audiobook?'), findsNothing);
      });
    });

    group('Size Formatting', () {
      testWidgets('should format size in bytes correctly', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
          fileCount: 1,
          fileSize: 512, // Less than 1 KB
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(find.textContaining('512 B'), findsOneWidget);
      });

      testWidgets('should format size in KB correctly', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
          fileCount: 1,
          fileSize: 2 * 1024, // 2 KB
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(find.textContaining('2.0 KB'), findsOneWidget);
      });

      testWidgets('should format size in MB correctly', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
          fileCount: 1,
          fileSize: 5 * 1024 * 1024, // 5 MB
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(find.textContaining('5.0 MB'), findsOneWidget);
      });

      testWidgets('should format size in GB correctly', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
          fileCount: 1,
          fileSize: 2 * 1024 * 1024 * 1024, // 2 GB
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(buildTestApp(dialog));
        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert
        expect(find.textContaining('2.0 GB'), findsOneWidget);
      });
    });

    group('Localization', () {
      testWidgets('should use English localization by default', (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(
          MaterialApp(
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
            ],
            supportedLocales: const [Locale('en', '')],
            locale: const Locale('en'),
            home: Scaffold(
              body: Builder(
                builder: (context) => ElevatedButton(
                  onPressed: () => showDialog(
                    context: context,
                    builder: (_) => dialog,
                  ),
                  child: const Text('Show Dialog'),
                ),
              ),
            ),
          ),
        );

        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert - should show English text
        expect(find.text('Delete Audiobook?'), findsOneWidget);
        expect(find.text('Cancel'), findsOneWidget);
      });

      testWidgets('should fallback to English when localization is missing',
          (tester) async {
        // Arrange
        final group = createTestGroup(
          groupPath: '/test/group',
          groupName: 'Test Group',
        );
        final dialog = DeleteConfirmationDialog(group: group);

        // Act
        await tester.pumpWidget(
          MaterialApp(
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
            ],
            supportedLocales: const [Locale('en', '')],
            locale: const Locale('fr'), // Unsupported locale
            home: Scaffold(
              body: Builder(
                builder: (context) => ElevatedButton(
                  onPressed: () => showDialog(
                    context: context,
                    builder: (_) => dialog,
                  ),
                  child: const Text('Show Dialog'),
                ),
              ),
            ),
          ),
        );

        await tester.tap(find.text('Show Dialog'));
        await tester.pumpAndSettle();

        // Assert - should fallback to English
        expect(find.text('Delete Audiobook?'), findsOneWidget);
      });
    });
  });
}
