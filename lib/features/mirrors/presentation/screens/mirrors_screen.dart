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

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:jabook/core/endpoints/endpoint_provider.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for displaying and managing RuTracker mirror URLs.
///
/// This screen provides a list of available RuTracker mirror URLs
/// and allows users to test connectivity and select preferred mirrors.
class MirrorsScreen extends ConsumerStatefulWidget {
  /// Creates a new MirrorsScreen instance.
  ///
  /// The [key] parameter is optional and can be used to identify
  /// this widget in the widget tree.
  const MirrorsScreen({super.key});

  @override
  ConsumerState<MirrorsScreen> createState() => _MirrorsScreenState();
}

class _MirrorsScreenState extends ConsumerState<MirrorsScreen> {
  List<Map<String, dynamic>> _mirrors = [];
  bool _isLoading = false;
  String? _activeMirror;
  final _logger = StructuredLogger();

  @override
  void initState() {
    super.initState();
    _loadMirrors();
  }

  Future<void> _loadMirrors() async {
    setState(() => _isLoading = true);
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final mirrors = await endpointManager.getAllEndpoints();
      final active = await endpointManager.getActiveEndpoint();

      setState(() {
        _mirrors = mirrors;
        _activeMirror = active;
      });
    } on Exception catch (e) {
      unawaited(_logger.log(
          level: 'ERROR',
          subsystem: 'mirrors',
          message: 'Failed to load mirrors',
          cause: e.toString()));
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _testMirror(String url) async {
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      await endpointManager.healthCheck(url);
      await _loadMirrors(); // Refresh list
    } on Exception catch (e) {
      unawaited(_logger.log(
          level: 'ERROR',
          subsystem: 'mirrors',
          message: 'Failed to test mirror',
          cause: e.toString(),
          extra: {'url': url}));
    }
  }

  Future<void> _setActiveMirror(String url) async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'mirrors',
        message: 'Setting active mirror',
        extra: {'url': url},
      );

      final endpointManager = ref.read(endpointManagerProvider);
      await endpointManager.setActiveEndpoint(url);

      // Reload to get updated active mirror
      await _loadMirrors();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Активное зеркало установлено: $url'),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'ERROR',
        subsystem: 'mirrors',
        message: 'Failed to set active mirror',
        cause: e.toString(),
        extra: {'url': url},
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Не удалось установить активное зеркало: $e'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }

  Future<void> _testAllMirrors() async {
    setState(() => _isLoading = true);
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      // Test all enabled endpoints
      final mirrors = await endpointManager.getAllEndpoints();
      for (final mirror in mirrors) {
        final url = mirror['url'] as String;
        if (mirror['enabled'] == true) {
          await endpointManager.healthCheck(url);
        }
      }
      await _loadMirrors();
    } on Exception catch (e) {
      unawaited(_logger.log(
          level: 'ERROR',
          subsystem: 'mirrors',
          message: 'Failed to test all mirrors',
          cause: e.toString()));
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(AppLocalizations.of(context)?.mirrorsScreenTitle ??
              'RuTracker Mirrors'),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: _isLoading ? null : _testAllMirrors,
            ),
          ],
        ),
        body: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : _mirrors.isEmpty
                ? RefreshIndicator(
                    onRefresh: _loadMirrors,
                    child: SingleChildScrollView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      child: SizedBox(
                        height: MediaQuery.of(context).size.height * 0.7,
                        child: Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.cloud_off, size: 64),
                              const SizedBox(height: 16),
                              Text(
                                'No mirrors available',
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              const SizedBox(height: 8),
                              Text(
                                'Check your connection and try again',
                                style: Theme.of(context).textTheme.bodyMedium,
                              ),
                              const SizedBox(height: 16),
                              ElevatedButton(
                                onPressed: _loadMirrors,
                                child: Text(
                                    AppLocalizations.of(context)?.retry ??
                                        'Retry'),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  )
                : RefreshIndicator(
                    onRefresh: _loadMirrors,
                    child: ListView.builder(
                      itemCount: _mirrors.length,
                      itemBuilder: (context, index) {
                        final mirror = _mirrors[index];
                        final url = mirror['url'] as String;
                        final isActive = url == _activeMirror;
                        final healthScore =
                            mirror['health_score'] as double? ?? 0.0;
                        final lastChecked = mirror['last_checked'] as DateTime?;
                        final isHealthy = healthScore > 0.5;

                        return Card(
                          margin: const EdgeInsets.symmetric(
                              horizontal: 16, vertical: 4),
                          child: ListTile(
                            leading: CircleAvatar(
                              backgroundColor:
                                  isHealthy ? Colors.green : Colors.red,
                              child: Icon(
                                isHealthy ? Icons.check : Icons.close,
                                color: Colors.white,
                              ),
                            ),
                            title: Text(
                              url,
                              style: TextStyle(
                                fontWeight: isActive
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                              ),
                            ),
                            subtitle: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                    'Health Score: ${(healthScore * 100).toStringAsFixed(1)}%'),
                                if (lastChecked != null)
                                  Text(
                                    'Last checked: ${_formatDateTime(lastChecked)}',
                                    style:
                                        Theme.of(context).textTheme.bodySmall,
                                  ),
                              ],
                            ),
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                if (!isActive)
                                  IconButton(
                                    icon: const Icon(Icons.play_arrow),
                                    onPressed: () => _setActiveMirror(url),
                                    tooltip: 'Set as active',
                                  ),
                                IconButton(
                                  icon: const Icon(Icons.refresh),
                                  onPressed: () => unawaited(_testMirror(url)),
                                  tooltip: 'Test mirror',
                                ),
                              ],
                            ),
                            isThreeLine: true,
                          ),
                        );
                      },
                    ),
                  ),
      );

  String _formatDateTime(DateTime dateTime) {
    final now = DateTime.now();
    final difference = now.difference(dateTime);

    if (difference.inMinutes < 1) {
      return 'Just now';
    } else if (difference.inHours < 1) {
      return '${difference.inMinutes}m ago';
    } else if (difference.inDays < 1) {
      return '${difference.inHours}h ago';
    } else {
      return '${difference.inDays}d ago';
    }
  }
}
