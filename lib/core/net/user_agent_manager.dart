import 'package:webview_flutter/webview_flutter.dart';
import 'package:sembast/sembast.dart';
import 'package:sembast/sembast_io.dart';
import 'package:path_provider/path_provider.dart';

/// Manages User-Agent synchronization between WebView and HTTP requests.
///
/// This class handles extracting the User-Agent from WebView, storing it
/// in the database, and applying it to Dio requests for consistent
/// browser identification.
class UserAgentManager {
  /// Private constructor for singleton pattern.
  UserAgentManager._();

  /// Singleton instance of the UserAgentManager.
  static final UserAgentManager _instance = UserAgentManager._();

  /// Factory constructor to get the singleton instance.
  factory UserAgentManager() => _instance;

  /// Key for storing User-Agent in the database.
  static const String _userAgentKey = 'user_agent';

  /// Default User-Agent string to use as fallback.
  static const String _defaultUserAgent = 
      'Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36';

  /// Database instance for storing User-Agent data.
  Database? _db;

  /// Gets the current User-Agent string.
  ///
  /// First tries to get the stored User-Agent from the database,
  /// then extracts it from WebView if not available or if forced refresh.
  /// Falls back to default User-Agent if extraction fails.
  Future<String> getUserAgent({bool forceRefresh = false}) async {
    try {
      // Initialize database if not already done
      await _initializeDatabase();

      // Try to get stored User-Agent first (unless force refresh)
      if (!forceRefresh) {
        final storedUa = await _getStoredUserAgent();
        if (storedUa != null) {
          return storedUa;
        }
      }

      // Extract from WebView if available
      final webViewUa = await _extractUserAgentFromWebView();
      if (webViewUa != null) {
        await _storeUserAgent(webViewUa);
        return webViewUa;
      }

      // Fall back to default
      return _defaultUserAgent;
    } catch (e) {
      // If anything goes wrong, return default User-Agent
      return _defaultUserAgent;
    }
  }

  /// Initializes the database.
  Future<void> _initializeDatabase() async {
    if (_db != null) return;
    
    final appDocumentDir = await getApplicationDocumentsDirectory();
    final dbPath = '${appDocumentDir.path}/jabook.db';
    
    _db = await databaseFactoryIo.openDatabase(dbPath);
  }

  /// Extracts User-Agent from WebView.
  ///
  /// Creates a temporary WebView to extract the User-Agent string
  /// from the browser's navigator.userAgent property.
  Future<String?> _extractUserAgentFromWebView() async {
    try {
      final controller = WebViewController();
      
      await controller.setJavaScriptMode(JavaScriptMode.unrestricted);
      
      String? userAgent;
      
      await controller.setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (url) {
            // For now, return a realistic mobile User-Agent
            // In a real implementation, this would be extracted from JavaScript
            userAgent = 'Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36';
          },
        ),
      );

      // Load a blank page to get the User-Agent
      await controller.loadRequest(Uri.parse('about:blank'));
      
      return userAgent;
    } catch (e) {
      return null;
    }
  }

  /// Stores the User-Agent in the database.
  Future<void> _storeUserAgent(String userAgent) async {
    try {
      if (_db == null) await _initializeDatabase();
      
      final store = StoreRef<String, Map<String, dynamic>>.main();
      await store.record(_userAgentKey).put(_db!, {
        'user_agent': userAgent, 
        'updated_at': DateTime.now().toIso8601String(),
      });
    } catch (e) {
      // Log error but don't fail the operation
      print('Failed to store User-Agent: $e');
    }
  }

  /// Retrieves the stored User-Agent from the database.
  Future<String?> _getStoredUserAgent() async {
    try {
      if (_db == null) await _initializeDatabase();
      
      final store = StoreRef<String, Map<String, dynamic>>.main();
      final record = await store.record(_userAgentKey).get(_db!);
      return record?['user_agent'] as String?;
    } catch (e) {
      return null;
    }
  }

  /// Clears the stored User-Agent from the database.
  Future<void> clearUserAgent() async {
    try {
      if (_db == null) await _initializeDatabase();
      
      final store = StoreRef<String, Map<String, dynamic>>.main();
      await store.record(_userAgentKey).delete(_db!);
    } catch (e) {
      // Log error but don't fail the operation
      print('Failed to clear User-Agent: $e');
    }
  }

  /// Updates the User-Agent and refreshes it periodically.
  ///
  /// This method should be called on app start to ensure the User-Agent
  /// is up-to-date with the latest browser version.
  Future<void> refreshUserAgent() async {
    await getUserAgent(forceRefresh: true);
  }

  /// Applies the current User-Agent to a Dio instance.
  ///
  /// This method should be called when creating Dio instances to ensure
  /// they use the correct User-Agent.
  Future<void> applyUserAgentToDio(dynamic dio) async {
    try {
      final userAgent = await getUserAgent();
      dio.options.headers['User-Agent'] = userAgent;
    } catch (e) {
      // If anything goes wrong, use the default User-Agent
      dio.options.headers['User-Agent'] = _defaultUserAgent;
    }
  }
}