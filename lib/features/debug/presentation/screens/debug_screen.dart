import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Debug screen for development and troubleshooting purposes.
///
/// This screen provides debugging tools and information to help
/// developers diagnose issues during development and testing.
class DebugScreen extends ConsumerStatefulWidget {
  /// Creates a new DebugScreen instance.
  ///
  /// The [key] parameter is optional and can be used to identify
  /// this widget in the widget tree.
  const DebugScreen({super.key});

  @override
  ConsumerState<DebugScreen> createState() => _DebugScreenState();
}

class _DebugScreenState extends ConsumerState<DebugScreen> with SingleTickerProviderStateMixin {
  late EnvironmentLogger _logger;
  late EndpointManager _endpointManager;
  late AudiobookTorrentManager _torrentManager;
  late RuTrackerCacheService _cacheService;

  late TabController _tabController;
  List<String> _logEntries = [];
  List<Map<String, dynamic>> _mirrors = [];
  List<Map<String, dynamic>> _downloads = [];
  Map<String, dynamic> _cacheStats = {};

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    _initializeServices();
    _loadDebugData();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  void _initializeServices() {
    _logger = EnvironmentLogger();
    _torrentManager = AudiobookTorrentManager();
    _cacheService = RuTrackerCacheService();
  }

  Future<void> _loadDebugData() async {
    await _loadLogs();
    await _loadMirrors();
    await _loadDownloads();
    await _loadCacheStats();
  }

  Future<void> _loadLogs() async {
    try {
      final structuredLogger = StructuredLogger();
      await structuredLogger.initialize();
      
      final logs = await structuredLogger.getLogs(limit: 50);
      
      setState(() {
        _logEntries = logs.map((logEntry) {
          final level = logEntry['level'] ?? 'INFO';
          final subsystem = logEntry['subsystem'] ?? 'unknown';
          final message = logEntry['msg'] ?? 'No message';
          final timestamp = logEntry['ts'] ?? DateTime.now().toIso8601String();
          final cause = logEntry['cause'];
          
          return '$level: $subsystem - $message${cause != null ? ' (Cause: $cause)' : ''} [$timestamp]';
        }).toList();
      });
    } on Exception catch (e) {
      _logger.e('Failed to load logs: $e');
      // Fallback to placeholder logs
      setState(() {
        _logEntries = [
          'INFO: App started at ${DateTime.now()}',
          'DEBUG: Cache initialized successfully',
          'WARNING: No active downloads found',
          'ERROR: Failed to connect to mirror rutracker.me',
          'ERROR: Failed to load logs: $e',
        ];
      });
    }
  }

  Future<void> _loadMirrors() async {
    try {
      final appDatabase = AppDatabase();
      await appDatabase.initialize();
      _endpointManager = EndpointManager(appDatabase.database);
      await _endpointManager.initializeDefaultEndpoints();
      
      final mirrors = await _endpointManager.getAllEndpoints();
      setState(() {
        _mirrors = mirrors;
      });
    } on Exception catch (e) {
      _logger.e('Failed to load mirrors: $e');
    }
  }

  Future<void> _loadDownloads() async {
    try {
      final downloads = await _torrentManager.getActiveDownloads();
      setState(() {
        _downloads = downloads;
      });
    } on Exception catch (e) {
      _logger.e('Failed to load downloads: $e');
    }
  }

  Future<void> _exportLogs(BuildContext context) async {
    final currentLocalizations = AppLocalizations.of(context);
    final scaffoldMessenger = ScaffoldMessenger.of(context);
    try {
      final structuredLogger = StructuredLogger();
      await structuredLogger.initialize();
      await structuredLogger.shareLogs();
      
      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        SnackBar(content: Text(currentLocalizations?.logsExportedSuccessfully ?? 'Logs exported successfully')),
      );
    } on Exception catch (e) {
      _logger.e('Failed to export logs: $e');
      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        SnackBar(content: Text('${currentLocalizations?.failedToExportLogs ?? 'Failed to export logs'}: $e')),
      );
    }
  }

  Future<void> _loadCacheStats() async {
    try {
      final stats = await _cacheService.getStatistics();
      setState(() {
        _cacheStats = stats;
      });
    } on Exception catch (e) {
      _logger.e('Failed to load cache stats: $e');
      // Fallback to placeholder stats
      setState(() {
        _cacheStats = {
          'total_entries': 0,
          'search_cache_size': 0,
          'topic_cache_size': 0,
          'memory_usage': '0 B',
        };
      });
    }
  }

  Future<void> _clearCache(BuildContext context) async {
    final localizations = AppLocalizations.of(context);
    final scaffoldMessenger = ScaffoldMessenger.of(context);
    try {
      await _cacheService.clearSearchResultsCache();
      await _loadCacheStats();
      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        SnackBar(content: Text(localizations?.cacheClearedSuccessfully ?? 'Cache cleared successfully')),
      );
    } on Exception catch (e) {
      _logger.e('Failed to clear cache: $e');
      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        SnackBar(content: Text('Failed to clear cache: $e')),
      );
    }
  }

  Future<void> _testAllMirrors(BuildContext context) async {
    final localizations = AppLocalizations.of(context);
    final scaffoldMessenger = ScaffoldMessenger.of(context);
    try {
      // Health check for all mirrors
      final mirrors = await _endpointManager.getAllEndpoints();
      for (final mirror in mirrors) {
        final url = mirror['url'] as String?;
        if (url != null) {
          await _endpointManager.healthCheck(url);
        }
      }
      await _loadMirrors();
      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        SnackBar(content: Text(localizations?.mirrorHealthCheckCompleted ?? 'Mirror health check completed')),
      );
    } on Exception catch (e) {
      _logger.e('Failed to test mirrors: $e');
      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        SnackBar(content: Text('Failed to test mirrors: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    
    return Scaffold(
      appBar: AppBar(
        title: Text(localizations?.debugTools ?? 'Debug Tools'),
        bottom: TabBar(
          controller: _tabController,
          tabs: [
            Tab(text: localizations?.logsTab ?? 'Logs'),
            Tab(text: localizations?.mirrorsTab ?? 'Mirrors'),
            Tab(text: localizations?.downloadsTab ?? 'Downloads'),
            Tab(text: localizations?.cacheTab ?? 'Cache'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildLogsTab(),
          _buildMirrorsTab(localizations),
          _buildDownloadsTab(),
          _buildCacheTab(localizations),
        ],
      ),
      floatingActionButton: _buildFloatingActionButtons(context),
    );
  }

  Widget _buildLogsTab() => ListView.builder(
    itemCount: _logEntries.length,
    itemBuilder: (context, index) {
      final entry = _logEntries[index];
      final backgroundColor = switch (entry) {
        final s when s.contains('ERROR') => Colors.red.shade100,
        final s when s.contains('WARNING') => Colors.orange.shade100,
        final s when s.contains('INFO') => Colors.blue.shade100,
        final s when s.contains('DEBUG') => Colors.green.shade100,
        _ => null,
      };

      return Card(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        color: backgroundColor,
        child: ListTile(
          title: Text(
            entry,
            style: TextStyle(
              fontSize: 12,
              color: backgroundColor != null ? Colors.black87 : null,
            ),
          ),
        ),
      );
    },
  );

  Widget _buildMirrorsTab(AppLocalizations? localizations) => Column(
    children: [
      Padding(
        padding: const EdgeInsets.all(8.0),
        child: ElevatedButton(
          onPressed: () => _testAllMirrors(context),
          child: Text(localizations?.testAllMirrors ?? 'Test All Mirrors'),
        ),
      ),
      Expanded(
        child: ListView.builder(
          itemCount: _mirrors.length,
          itemBuilder: (context, index) {
            final mirror = _mirrors[index];
            final isActive = mirror['enabled'] == true;
            final lastOk = mirror['last_ok'];
            final rtt = mirror['rtt'];

            return Card(
              margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              color: isActive ? Colors.green.shade50 : Colors.grey.shade200,
              child: ListTile(
                title: Text(mirror['url'] ?? 'Unknown'),
                subtitle: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${localizations?.statusLabel ?? 'Status: '}${isActive ? localizations?.activeStatus ?? 'Active' : localizations?.disabledStatus ?? 'Disabled'}'),
                    if (lastOk != null) Text('${localizations?.lastOkLabel ?? 'Last OK: '}$lastOk'),
                    if (rtt != null) Text('${localizations?.rttLabel ?? 'RTT: '}$rtt${localizations?.milliseconds ?? 'ms'}'),
                  ],
                ),
                trailing: Icon(
                  isActive ? Icons.check_circle : Icons.cancel,
                  color: isActive ? Colors.green : Colors.red,
                ),
              ),
            );
          },
        ),
      ),
    ],
  );

  Widget _buildDownloadsTab() => ListView.builder(
    itemCount: _downloads.length,
    itemBuilder: (context, index) {
      final download = _downloads[index];
      final progress = download['progress'] ?? 0.0;
      final status = download['status'] ?? 'unknown';

      return Card(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: ListTile(
          title: Text('Download ${download['id']}'),
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Status: $status'),
              LinearProgressIndicator(value: progress / 100),
              Text('Progress: ${progress.toStringAsFixed(1)}%'),
            ],
          ),
          trailing: IconButton(
            icon: const Icon(Icons.delete),
            onPressed: () {
              // TODO: Implement download removal
            },
          ),
        ),
      );
    },
  );

  Widget _buildCacheTab(AppLocalizations? localizations) => Column(
    children: [
      Padding(
        padding: const EdgeInsets.all(16.0),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  localizations?.cacheStatistics ?? 'Cache Statistics',
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(localizations?.totalEntries ?? 'Total entries: '),
                    Text(_cacheStats['total_entries'].toString()),
                  ],
                ),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(localizations?.searchCache ?? 'Search cache: '),
                    Text(_cacheStats['search_cache_size'].toString()),
                  ],
                ),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(localizations?.topicCache ?? 'Topic cache: '),
                    Text(_cacheStats['topic_cache_size'].toString()),
                  ],
                ),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(localizations?.memoryUsage ?? 'Memory usage: '),
                    Text(_cacheStats['memory_usage'].toString()),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
      ElevatedButton(
        onPressed: () => _clearCache(context),
        child: Text(localizations?.clearAllCache ?? 'Clear All Cache'),
      ),
    ],
  );

  Widget _buildFloatingActionButtons(BuildContext context) => Column(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        FloatingActionButton(
          heroTag: 'refresh',
          mini: true,
          onPressed: _loadDebugData,
          child: const Icon(Icons.refresh),
        ),
        const SizedBox(height: 8),
        FloatingActionButton(
          heroTag: 'export',
          mini: true,
          onPressed: () => _exportLogs(context),
          child: const Icon(Icons.file_download),
        ),
      ],
    );
  }