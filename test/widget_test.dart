// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:jabook/app/app.dart';
import 'package:jabook/features/library/presentation/screens/library_screen.dart';

void main() {
  testWidgets('Library screen loads correctly', (tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(
      const ProviderScope(
        child: JaBookApp(),
      ),
    );

    // Wait for localization to load
    await tester.pumpAndSettle();

    // Verify that the library screen loads correctly with localized text.
    // Use more specific finders to avoid ambiguity
    expect(find.text('Library'), findsAtLeastNWidgets(1));
    // Look for a more specific text that should be unique to the library screen
    expect(find.byType(LibraryScreen), findsOneWidget);
  });
}
