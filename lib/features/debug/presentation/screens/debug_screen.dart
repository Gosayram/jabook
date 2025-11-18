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

class _DebugScreenState extends ConsumerState<DebugScreen>
    with SingleTickerProviderStateMixin {
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
    _initializeServices().then((_) => _loadDebugData());
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _initializeServices() async {
    _logger = EnvironmentLogger();
    _torrentManager = AudiobookTorrentManager();
    _cacheService = RuTrackerCacheService();
    // Initialize EndpointManager with database
    final appDatabase = AppDatabase();
    await appDatabase.initialize();
    _endpointManager = EndpointManager(appDatabase.database);
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
          'INFO: JaBook started at ${DateTime.now()}',
          'DEBUG: Cache cleared successfully',
          'WARNING: No active downloads found',
          'ERROR: Failed to connect to active RuTracker mirror',
          'ERROR: Failed to load logs: $e',
        ];
      });
    }
  }

  Future<void> _loadMirrors() async {
    try {
      final mirrors = await _endpointManager.getAllEndpointsWithHealth();
      setState(() {
        _mirrors = mirrors;
      });
    } on Exception catch (e) {
      _logger.e('Failed to load mirrors: $e');
      // Fallback to empty list
      setState(() {
        _mirrors = [];
      });
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
    final scaffoldMessenger = ScaffoldMessenger.of(context);
    try {
      final structuredLogger = StructuredLogger();
      await structuredLogger.initialize();
      await structuredLogger.shareLogs();

      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        const SnackBar(content: Text('Logs exported successfully')),
      );
    } on Exception catch (e) {
      _logger.e('Failed to export logs: $e');
      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        SnackBar(content: Text('Failed to export logs: $e')),
      );
    }
  }

  Future<void> _loadCacheStats() async {
    try {
      // Initialize database first
      final appDatabase = AppDatabase();
      await appDatabase.initialize();
      await _cacheService.initialize(appDatabase.database);

      final stats = await _cacheService.getStatistics();
      setState(() {
        _cacheStats = stats;
      });
    } on Exception catch (e) {
      _logger.e('Failed to load cache stats: $e');
      // Fallback to placeholder stats
      setState(() {
        _cacheStats = {
          'total_entries': 42,
          'search_cache_size': 25,
          'topic_cache_size': 17,
          'memory_usage': '2.5 MB',
        };
      });
    }
  }

  Future<void> _clearCache(BuildContext context) async {
    final scaffoldMessenger = ScaffoldMessenger.of(context);
    try {
      await _cacheService.clearSearchResultsCache();
      await _loadCacheStats();
      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        const SnackBar(content: Text('Cache cleared successfully')),
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
    final scaffoldMessenger = ScaffoldMessenger.of(context);
    try {
      // Test all mirrors using EndpointManager
      final mirrors = await _endpointManager.getAllEndpoints();
      for (final mirror in mirrors) {
        final url = mirror['url'] as String;
        await _endpointManager.healthCheck(url);
      }

      // Reload updated mirror status
      await _loadMirrors();

      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        const SnackBar(content: Text('Mirror health check completed')),
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
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(
              AppLocalizations.of(context)?.debugToolsTitle ?? 'Debug Tools'),
          bottom: TabBar(
            controller: _tabController,
            tabs: const [
              Tab(
                text: 'Logs',
                icon: Icon(Icons.description),
              ),
              Tab(
                text: 'Mirrors',
                icon: Icon(Icons.dns),
              ),
              Tab(
                text: 'Downloads',
                icon: Icon(Icons.download),
              ),
              Tab(
                text: 'Cache',
                icon: Icon(Icons.cached),
              ),
            ],
          ),
        ),
        body: TabBarView(
          controller: _tabController,
          children: [
            _buildLogsTab(),
            _buildMirrorsTab(null),
            _buildDownloadsTab(),
            _buildCacheTab(null),
          ],
        ),
        floatingActionButton: _buildFloatingActionButtons(context),
      );

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
            child: Semantics(
              button: true,
              label: 'Test all mirrors',
              child: ElevatedButton(
                onPressed: () => _testAllMirrors(context),
                child: Text(
                    AppLocalizations.of(context)?.testAllMirrorsButton ??
                        'Test All Mirrors'),
              ),
            ),
          ),
          Expanded(
            child: ListView.builder(
              itemCount: _mirrors.length,
              itemBuilder: (context, index) {
                final mirror = _mirrors[index];
                final isActive = mirror['enabled'] == true;
                final healthScore = mirror['health_score'] as int? ?? 0;
                final healthStatus =
                    mirror['health_status'] as String? ?? 'Unknown';
                final lastOk = mirror['last_ok'];
                final rtt = mirror['rtt'];

                // Determine actual status based on both enabled flag and health
                final bool isActuallyActive;
                final String statusText;
                final Color statusColor;

                if (!isActive) {
                  isActuallyActive = false;
                  statusText =
                      AppLocalizations.of(context)?.disabledStatusText ??
                          'Disabled';
                  statusColor = Colors.red;
                } else if (healthScore >= 60) {
                  isActuallyActive = true;
                  statusText = healthStatus;
                  statusColor = Colors.green;
                } else if (healthScore >= 30) {
                  isActuallyActive = true;
                  statusText =
                      'Degraded'; // TODO: Add localization key for degraded status
                  statusColor = Colors.orange;
                } else {
                  isActuallyActive = false;
                  statusText =
                      'Unhealthy'; // TODO: Add localization key for unhealthy status
                  statusColor = Colors.red;
                }

                return Semantics(
                  container: true,
                  label:
                      'Mirror: ${mirror['url'] ?? 'Unknown'}, Status: ${isActive ? 'Active' : 'Disabled'}',
                  child: Card(
                    margin:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    color: isActuallyActive
                        ? Theme.of(context).colorScheme.surfaceContainerHighest
                        : Theme.of(context).colorScheme.errorContainer,
                    child: ListTile(
                      title: Text(
                        mirror['url'] ?? 'Unknown',
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.onSurface,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Status: $statusText',
                            style: TextStyle(
                              color: statusColor,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          if (lastOk != null)
                            Text(
                                '${AppLocalizations.of(context)?.lastOkLabelText ?? 'Last OK:'} $lastOk'),
                          if (rtt != null)
                            Text(
                                '${AppLocalizations.of(context)?.rttLabelText ?? 'RTT:'} $rtt ${AppLocalizations.of(context)?.millisecondsText ?? 'ms'}'),
                        ],
                      ),
                      trailing: Semantics(
                        label: isActive
                            ? AppLocalizations.of(context)?.activeStatusText ??
                                'Active mirror'
                            : AppLocalizations.of(context)
                                    ?.disabledStatusText ??
                                'Disabled mirror',
                        child: Icon(
                          isActive ? Icons.check_circle : Icons.cancel,
                          color: statusColor,
                        ),
                      ),
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

          return Semantics(
            container: true,
            label:
                'Download ${download['id']}, Status: $status, Progress: ${progress.toStringAsFixed(1)}%',
            child: Card(
              margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: ListTile(
                title: Text(AppLocalizations.of(context)?.downloadLabel ??
                    'Download ${download['id']}'),
                subtitle: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                        '${AppLocalizations.of(context)?.statusLabel ?? 'Status:'} $status'),
                    Semantics(
                      value: '${progress.toStringAsFixed(1)}%',
                      child: LinearProgressIndicator(value: progress / 100),
                    ),
                    Text(
                        '${AppLocalizations.of(context)?.downloadProgressLabel ?? 'Progress:'} ${progress.toStringAsFixed(1)}%'),
                  ],
                ),
                trailing: Semantics(
                  button: true,
                  label: 'Delete download',
                  child: IconButton(
                    icon: const Icon(Icons.delete),
                    onPressed: () {
                      // TODO: Implement download removal
                    },
                  ),
                ),
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
                      AppLocalizations.of(context)?.cacheStatisticsTitle ??
                          'Cache Statistics',
                      style: const TextStyle(
                          fontSize: 18, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(AppLocalizations.of(context)?.totalEntriesText ??
                            'Total entries:'),
                        Text(_cacheStats['total_entries'].toString()),
                      ],
                    ),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(AppLocalizations.of(context)?.searchCacheText ??
                            'Search cache:'),
                        Text(_cacheStats['search_cache_size'].toString()),
                      ],
                    ),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(AppLocalizations.of(context)?.topicCacheText ??
                            'Topic cache:'),
                        Text(_cacheStats['topic_cache_size'].toString()),
                      ],
                    ),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(AppLocalizations.of(context)?.memoryUsageText ??
                            'Memory usage:'),
                        Text(_cacheStats['memory_usage'].toString()),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          // Build Clear Cache button with proper disabled state
          Builder(
            builder: (context) {
              final totalEntries = _cacheStats['total_entries'] as int? ?? 0;
              final searchCacheSize =
                  _cacheStats['search_cache_size'] as int? ?? 0;
              final topicCacheSize =
                  _cacheStats['topic_cache_size'] as int? ?? 0;

              final hasCache =
                  totalEntries > 0 || searchCacheSize > 0 || topicCacheSize > 0;

              return Column(
                children: [
                  Semantics(
                    button: true,
                    enabled: hasCache,
                    label: 'Clear all cache',
                    child: ElevatedButton(
                      onPressed: hasCache ? () => _clearCache(context) : null,
                      child: Text(
                          AppLocalizations.of(context)?.clearAllCacheButton ??
                              'Clear All Cache'),
                    ),
                  ),
                  if (!hasCache)
                    Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: Text(
                        AppLocalizations.of(context)?.cacheIsEmptyMessage ??
                            'Cache is empty',
                        style: const TextStyle(
                            color: Colors.grey, fontStyle: FontStyle.italic),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      );

  Widget _buildFloatingActionButtons(BuildContext context) => Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Semantics(
            button: true,
            label: 'Refresh debug data',
            child: FloatingActionButton(
              heroTag: 'refresh',
              mini: true,
              onPressed: _loadDebugData,
              child: const Icon(Icons.refresh),
            ),
          ),
          const SizedBox(height: 8),
          Semantics(
            button: true,
            label: 'Export logs',
            child: FloatingActionButton(
              heroTag: 'export',
              mini: true,
              onPressed: () => _exportLogs(context),
              child: const Icon(Icons.file_download),
            ),
          ),
        ],
      );
}
