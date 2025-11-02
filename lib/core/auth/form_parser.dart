import 'dart:convert';

import 'package:html/parser.dart' as html_parser;
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:windows1251/windows1251.dart';

/// Represents a single form field extracted from HTML.
class FormField {
  /// Creates a new FormField instance.
  FormField({
    required this.name,
    required this.type,
    this.value,
  });

  /// Field name attribute.
  final String name;

  /// Field type attribute (text, password, hidden, etc.).
  final String type;

  /// Field value (for hidden fields and default values).
  final String? value;
}

/// Represents a parsed login form from RuTracker.
class LoginForm {
  /// Creates a new LoginForm instance.
  LoginForm({
    required this.action,
    required this.method,
    required this.hiddenFields,
    this.usernameFieldName,
    this.passwordFieldName,
    this.baseUrl,
  });

  /// Form action URL (may be relative or absolute).
  final String action;

  /// HTTP method (usually POST).
  final String method;

  /// All hidden fields with their values (CSRF tokens, formhash, etc.).
  final List<FormField> hiddenFields;

  /// Detected username field name.
  final String? usernameFieldName;

  /// Detected password field name.
  final String? passwordFieldName;

  /// Base URL for resolving relative action URLs.
  final String? baseUrl;

  /// Returns absolute action URL.
  String get absoluteActionUrl {
    if (action.startsWith('http://') || action.startsWith('https://')) {
      return action;
    }
    if (baseUrl != null) {
      final base = baseUrl!.endsWith('/') ? baseUrl! : '$baseUrl/';
      final actionPath = action.startsWith('/') ? action.substring(1) : action;
      return '$base$actionPath';
    }
    return action;
  }
}

/// Parser for extracting login form fields from RuTracker HTML.
class FormParser {
  /// Private constructor to prevent instantiation (static-only class).
  FormParser._();

  /// Extracts login form from HTML page.
  ///
  /// The [html] parameter contains the HTML content of the login page.
  /// The [baseUrl] parameter is used to resolve relative action URLs.
  ///
  /// Returns a [LoginForm] object with all extracted fields and metadata.
  ///
  /// Throws [Exception] if form cannot be parsed.
  /// Decodes HTML content, trying UTF-8 first, then falling back to windows-1251.
  static String _decodeHtml(String html) {
    try {
      return utf8.decode(html.codeUnits);
    } on FormatException {
      return windows1251.decode(html.codeUnits);
    }
  }

  /// Extracts login form from HTML page.
  ///
  /// The [html] parameter contains the HTML content of the login page.
  /// The [baseUrl] parameter is used to resolve relative action URLs.
  ///
  /// Returns a [LoginForm] object with all extracted fields and metadata.
  ///
  /// Throws [Exception] if form cannot be parsed.
  static Future<LoginForm> extractLoginForm(
    String html,
    String? baseUrl,
  ) async {
    try {
      // Try UTF-8 first, fallback to windows-1251
      final decodedHtml = _decodeHtml(html);
      final document = html_parser.parse(decodedHtml);

      // Find login form (usually in /forum/login.php)
      // Try multiple selectors to find the form
      final formElement = document.querySelector('form[action*="login"]') ??
          document.querySelector('form[action*="ucp.php"]') ??
          document.querySelector('form#login') ??
          document.querySelector('form.login') ??
          document.querySelector('form');

      if (formElement == null) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Login form not found in HTML',
        );
        throw Exception('Login form not found in HTML');
      }

      // Extract form action and method
      var action = formElement.attributes['action'] ?? '';
      final method = formElement.attributes['method']?.toUpperCase() ?? 'POST';

      // Validate action URL
      if (action.isEmpty) {
        // Default action if not specified (relative to current page)
        action = 'login.php';
      } else if (action.startsWith('?')) {
        // Action is query string only, prepend current page
        action = 'login.php$action';
      }

      // Find all input fields in the form
      final inputElements = formElement.querySelectorAll('input');
      final allFields = <FormField>[];

      for (final input in inputElements) {
        final name = input.attributes['name'] ?? '';
        final type = input.attributes['type']?.toLowerCase() ?? 'text';
        final value = input.attributes['value'] ?? '';

        if (name.isNotEmpty) {
          allFields.add(FormField(
            name: name,
            type: type,
            value: value.isEmpty ? null : value,
          ));
        }
      }

      // Separate hidden fields
      final hiddenFields =
          allFields.where((field) => field.type == 'hidden').toList();

      // Detect username and password fields
      final usernameField = _detectUsernameField(allFields);
      final passwordField = _detectPasswordField(allFields);

      // Validate that we found essential fields
      if (hiddenFields.isEmpty &&
          usernameField == null &&
          passwordField == null) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Login form has no fields - may be invalid',
          extra: {
            'total_fields': allFields.length,
            'action': action,
          },
        );
      }

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'auth',
        message: 'Login form extracted successfully',
        extra: {
          'action': action,
          'method': method,
          'hidden_fields_count': hiddenFields.length,
          'total_fields': allFields.length,
          'username_field': usernameField,
          'password_field': passwordField,
        },
      );

      return LoginForm(
        action: action,
        method: method,
        hiddenFields: hiddenFields,
        usernameFieldName: usernameField,
        passwordFieldName: passwordField,
        baseUrl: baseUrl,
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'auth',
        message: 'Failed to extract login form',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Detects username field using heuristic approach.
  ///
  /// Priority order:
  /// 1. Exact matches: login_username, login_user, login_name
  /// 2. Fields starting with user/login (length > 2, < 20, not hidden/password)
  /// 3. Fields containing nick/name but not password/email
  ///
  /// Returns field name or null if not found.
  static String? _detectUsernameField(List<FormField> fields) {
    // Priority 1: exact matches
    for (final field in fields) {
      final name = field.name.toLowerCase();
      if (name == 'login_username' ||
          name == 'login_user' ||
          name == 'login_name' ||
          name == 'username') {
        return field.name;
      }
    }

    // Priority 2: starts with user/login, not hidden, not password, length > 2
    for (final field in fields) {
      if (field.type == 'hidden' || field.type == 'password') continue;
      final name = field.name.toLowerCase();
      if ((name.startsWith('user') || name.startsWith('login')) &&
          name.length > 2 &&
          name.length < 20) {
        // Exclude single-letter fields and common non-username fields
        if (!['q', 'id', 'key', 'email'].contains(name)) {
          return field.name;
        }
      }
    }

    // Priority 3: contains nick/name, but not pass/email
    for (final field in fields) {
      if (field.type == 'hidden' || field.type == 'password') continue;
      final name = field.name.toLowerCase();
      if ((name.contains('nick') || name.contains('name')) &&
          !name.contains('pass') &&
          !name.contains('email') &&
          name.length > 2 &&
          name.length < 20) {
        return field.name;
      }
    }

    // Fallback: return first non-hidden, non-password text field
    for (final field in fields) {
      if (field.type != 'hidden' &&
          field.type != 'password' &&
          field.type != 'submit' &&
          field.type != 'button') {
        return field.name;
      }
    }

    return null;
  }

  /// Detects password field using heuristic approach.
  ///
  /// Priority order:
  /// 1. Fields with type="password"
  /// 2. Fields with pass/pwd in name
  /// 3. Fallback to "login_password" or "password"
  ///
  /// Returns field name or null if not found.
  static String? _detectPasswordField(List<FormField> fields) {
    // Priority 1: type="password"
    for (final field in fields) {
      if (field.type == 'password') {
        return field.name;
      }
    }

    // Priority 2: contains pass/pwd in name
    for (final field in fields) {
      final name = field.name.toLowerCase();
      if (name.contains('pass') || name.contains('pwd')) {
        return field.name;
      }
    }

    // Priority 3: exact matches
    for (final field in fields) {
      final name = field.name.toLowerCase();
      if (name == 'login_password' || name == 'password') {
        return field.name;
      }
    }

    return null;
  }
}
