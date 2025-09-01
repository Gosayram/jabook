// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:jabook/app/app.dart';

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
    expect(find.text('Library'), findsOneWidget);
    expect(find.text('Library content will be displayed here'), findsOneWidget);
  });

}
