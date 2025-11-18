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
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/endpoints/endpoint_provider.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for managing RuTracker mirror settings.
///
/// This screen allows users to view, test, and configure RuTracker mirrors
/// for optimal search performance.
class MirrorSettingsScreen extends ConsumerStatefulWidget {
  /// Creates a new MirrorSettingsScreen instance.
  const MirrorSettingsScreen({super.key});

  @override
  ConsumerState<MirrorSettingsScreen> createState() =>
      _MirrorSettingsScreenState();
}

class _MirrorSettingsScreenState extends ConsumerState<MirrorSettingsScreen> {
  List<Map<String, dynamic>> _mirrors = [];
  List<Map<String, dynamic>> _filteredMirrors = [];
  bool _isLoading = false;
  final _testingStates = <String, bool>{};
  bool _isBulkTesting = false;
  String? _activeMirror;

  // Filter states
  bool _showOnlyEnabled = false;
  bool _showOnlyHealthy = false;

  // Sort options
  String _sortBy = 'priority'; // 'priority', 'health', 'rtt'

  @override
  void initState() {
    super.initState();
    _filteredMirrors = [];
    _loadMirrors();
  }

  Future<void> _loadMirrors() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final mirrors = await endpointManager.getAllEndpointsWithHealth();
      // Initial sort by priority (will be re-sorted by _applyFilters)

      // Get active mirror
      try {
        _activeMirror = await endpointManager.getActiveEndpoint();
      } on Exception {
        _activeMirror = null;
      }

      setState(() {
        _mirrors = mirrors;
        _applyFilters();
        _isLoading = false;
      });
    } on Exception catch (e) {
      setState(() {
        _isLoading = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(
                  AppLocalizations.of(context)?.failedToLoadMirrorsMessage ??
                      'Failed to load mirrors: $e')),
        );
      }
    }
  }

  Future<void> _testMirror(String url) async {
    setState(() {
      _testingStates[url] = true;
    });

    try {
      final endpointManager = ref.read(endpointManagerProvider);

      // Log start of test
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'mirrors',
        message: 'Starting health check',
        extra: {'url': url},
      );

      await endpointManager.healthCheck(url, force: true);

      // Reload mirrors to get updated status
      await _loadMirrors();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.mirrorTestSuccessMessage ??
                  'Mirror $url tested successfully',
            ),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } on NetworkFailure catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'mirrors',
        message: 'Health check network failure',
        cause: e.message,
        extra: {'url': url},
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.mirrorTestFailedMessage ??
                  'Network error: ${e.message}',
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'mirrors',
        message: 'Health check failed',
        cause: e.toString(),
        extra: {'url': url},
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.mirrorTestFailedMessage ??
                  'Failed to test mirror: ${e.toString()}',
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } finally {
      setState(() {
        _testingStates[url] = false;
      });
    }
  }

  Future<void> _testAllMirrors() async {
    setState(() {
      _isBulkTesting = true;
    });
    var tested = 0;
    var total = 0;
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      // Count enabled mirrors
      final enabledMirrors =
          _mirrors.where((m) => m['enabled'] == true).toList();
      total = enabledMirrors.length;

      if (total == 0) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Нет активных зеркал для проверки'),
              duration: Duration(seconds: 2),
            ),
          );
        }
        return;
      }

      // Test all enabled mirrors sequentially to avoid burst
      for (final m in enabledMirrors) {
        final url = m['url'] as String;
        // Set testing state for individual mirror
        setState(() {
          _testingStates[url] = true;
        });

        try {
          await endpointManager.healthCheck(url, force: true);
          tested++;
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'mirrors',
            message: 'Bulk test failed for mirror',
            cause: e.toString(),
            extra: {'url': url},
          );
        } finally {
          setState(() {
            _testingStates[url] = false;
          });
        }
      }

      await _loadMirrors();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.mirrorHealthCheckCompletedMessage ??
                  'Проверка завершена: $tested/$total зеркал',
            ),
            backgroundColor: tested == total ? Colors.green : Colors.orange,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isBulkTesting = false;
        });
      }
    }
  }

  void _applyFilters() {
    var filtered = List<Map<String, dynamic>>.from(_mirrors);

    if (_showOnlyEnabled) {
      filtered = filtered.where((m) => m['enabled'] == true).toList();
    }

    if (_showOnlyHealthy) {
      filtered = filtered.where((m) {
        final healthScore = m['health_score'] as int? ?? 0;
        return healthScore >= 60;
      }).toList();
    }

    // Apply sorting
    switch (_sortBy) {
      case 'health':
        filtered.sort((a, b) {
          final scoreA = a['health_score'] as int? ?? 0;
          final scoreB = b['health_score'] as int? ?? 0;
          return scoreB.compareTo(scoreA); // Descending
        });
        break;
      case 'rtt':
        filtered.sort((a, b) {
          final rttA = a['rtt'] as int? ?? 999999;
          final rttB = b['rtt'] as int? ?? 999999;
          return rttA.compareTo(rttB); // Ascending (lower is better)
        });
        break;
      case 'priority':
      default:
        filtered.sort((a, b) {
          final priorityA = a['priority'] as int? ?? 5;
          final priorityB = b['priority'] as int? ?? 5;
          return priorityA.compareTo(priorityB); // Ascending
        });
        break;
    }

    setState(() {
      _filteredMirrors = filtered;
    });
  }

  Future<void> _setActiveMirror(String url) async {
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      await endpointManager.setActiveEndpoint(url);
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

  Future<void> _editPriority(String url, int currentPriority) async {
    final priorityController =
        TextEditingController(text: currentPriority.toString());

    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Изменить приоритет'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('URL: $url'),
            const SizedBox(height: 16),
            TextField(
              controller: priorityController,
              decoration: const InputDecoration(
                labelText: 'Приоритет (1-10)',
                hintText: '5',
                helperText: 'Меньше число = выше приоритет',
              ),
              keyboardType: TextInputType.number,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(AppLocalizations.of(context)?.cancel ?? 'Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Сохранить'),
          ),
        ],
      ),
    );

    if ((result ?? false) && priorityController.text.isNotEmpty) {
      try {
        final endpointManager = ref.read(endpointManagerProvider);
        final priority =
            int.tryParse(priorityController.text) ?? currentPriority;
        await endpointManager.updateEndpointPriority(url, priority);
        await _loadMirrors();

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Приоритет обновлен: $priority'),
              backgroundColor: Colors.green,
              duration: const Duration(seconds: 2),
            ),
          );
        }
      } on Exception catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Не удалось обновить приоритет: $e'),
              backgroundColor: Colors.red,
              duration: const Duration(seconds: 3),
            ),
          );
        }
      }
    }
  }

  Future<void> _setBestMirrorAsActive() async {
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      // Getting active endpoint triggers selection by health; we can show result
      final best = await endpointManager.getActiveEndpoint();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Active mirror: $best')),
        );
      }
      await _loadMirrors();
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to set best mirror: $e')),
        );
      }
    }
  }

  Future<void> _toggleMirror(String url, bool enabled) async {
    try {
      final endpointManager = ref.read(endpointManagerProvider);

      // Check if this mirror is currently active
      final currentActive = await endpointManager.getActiveEndpoint();
      final isCurrentlyActive = currentActive == url;

      // Update status
      await endpointManager.updateEndpointStatus(url, enabled);

      // If disabling active mirror, select new best one
      if (isCurrentlyActive && !enabled) {
        try {
          final newActive = await endpointManager.getActiveEndpoint();
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                    'Активное зеркало отключено. Переключено на: $newActive'),
                backgroundColor: Colors.blue,
                duration: const Duration(seconds: 3),
              ),
            );
          }
        } on Exception catch (e) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                    'Предупреждение: не удалось выбрать новое активное зеркало: $e'),
                backgroundColor: Colors.orange,
                duration: const Duration(seconds: 3),
              ),
            );
          }
        }
      }

      // Reload mirrors to get updated status
      await _loadMirrors();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)?.mirrorStatusText ??
                'Mirror ${enabled ? 'enabled' : 'disabled'}'),
            backgroundColor: enabled ? Colors.green : Colors.orange,
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
                AppLocalizations.of(context)?.failedToUpdateMirrorMessage ??
                    'Failed to update mirror: $e'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }

  Future<void> _copyUrlToClipboard(String url) async {
    await Clipboard.setData(ClipboardData(text: url));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('URL скопирован в буфер обмена'),
          duration: Duration(seconds: 2),
        ),
      );
    }
  }

  Future<void> _deleteMirror(String url) async {
    // Check if it's one of default mirrors
    final defaultMirrors = [
      'https://rutracker.net',
      'https://rutracker.me',
      'https://rutracker.org',
    ];
    final isDefault = defaultMirrors.contains(url);

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Удалить зеркало?'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('URL: $url'),
            if (isDefault) ...[
              const SizedBox(height: 8),
              const Text(
                'Внимание: это зеркало по умолчанию. Оно будет удалено из списка, но может быть добавлено снова.',
                style: TextStyle(color: Colors.orange),
              ),
            ],
            const SizedBox(height: 8),
            const Text('Вы уверены, что хотите удалить это зеркало?'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(AppLocalizations.of(context)?.cancel ?? 'Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Удалить'),
          ),
        ],
      ),
    );

    if (confirmed ?? false) {
      try {
        final endpointManager = ref.read(endpointManagerProvider);

        // If deleting active mirror, switch to another first
        if (url == _activeMirror) {
          try {
            final newActive = await endpointManager.getActiveEndpoint();
            if (newActive == url) {
              // If it's still selected, force select another
              final allMirrors = await endpointManager.getAllEndpoints();
              final otherMirror = allMirrors.firstWhere(
                (m) => m['url'] != url && m['enabled'] == true,
                orElse: () => {},
              )['url'];
              if (otherMirror != null) {
                await endpointManager.setActiveEndpoint(otherMirror);
              }
            }
          } on Exception catch (e) {
            await StructuredLogger().log(
              level: 'warning',
              subsystem: 'mirrors',
              message: 'Failed to switch active mirror before deletion',
              cause: e.toString(),
            );
          }
        }

        await endpointManager.removeEndpoint(url);
        await _loadMirrors();

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Зеркало удалено: $url'),
              backgroundColor: Colors.orange,
              duration: const Duration(seconds: 2),
            ),
          );
        }
      } on Exception catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Не удалось удалить зеркало: $e'),
              backgroundColor: Colors.red,
              duration: const Duration(seconds: 3),
            ),
          );
        }
      }
    }
  }

  Future<void> _addCustomMirror() async {
    final urlController = TextEditingController();
    final priorityController = TextEditingController(text: '5');

    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppLocalizations.of(context)?.addCustomMirrorTitle ??
            'Add Custom Mirror'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: urlController,
              decoration: const InputDecoration(
                labelText: 'Mirror URL',
                hintText: 'https://rutracker.example.com',
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: priorityController,
              decoration: const InputDecoration(
                labelText: 'Priority (1-10)',
                hintText: '5',
              ),
              keyboardType: TextInputType.number,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(AppLocalizations.of(context)?.cancel ?? 'Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(
                AppLocalizations.of(context)?.addMirrorButtonText ?? 'Add'),
          ),
        ],
      ),
    );

    if ((result ?? false) && urlController.text.isNotEmpty) {
      try {
        final url = urlController.text.trim();

        // Validate URL format
        if (!url.startsWith('http://') && !url.startsWith('https://')) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('URL должен начинаться с http:// или https://'),
                backgroundColor: Colors.orange,
                duration: Duration(seconds: 3),
              ),
            );
          }
          return;
        }

        try {
          final uri = Uri.parse(url);
          if (uri.host.isEmpty) {
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Неверный формат URL'),
                  backgroundColor: Colors.orange,
                  duration: Duration(seconds: 3),
                ),
              );
            }
            return;
          }
        } on Exception {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Неверный формат URL'),
                backgroundColor: Colors.orange,
                duration: Duration(seconds: 3),
              ),
            );
          }
          return;
        }

        final endpointManager = ref.read(endpointManagerProvider);
        final priority = int.tryParse(priorityController.text) ?? 5;
        final clampedPriority = priority.clamp(1, 10);

        // Check if mirror already exists
        final allMirrors = await endpointManager.getAllEndpoints();
        if (allMirrors.any((m) => m['url'] == url)) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Это зеркало уже существует в списке'),
                backgroundColor: Colors.orange,
                duration: Duration(seconds: 3),
              ),
            );
          }
          return;
        }

        await endpointManager.addEndpoint(url, clampedPriority);
        await _loadMirrors();

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text(
                    AppLocalizations.of(context)?.mirrorAddedMessage ??
                        'Mirror ${urlController.text} added')),
          );
        }
      } on Exception catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text(
                    AppLocalizations.of(context)?.failedToAddMirrorMessage ??
                        'Failed to add mirror: $e')),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(AppLocalizations.of(context)?.mirrorSettingsTitle ??
              'Mirror Settings'),
        ),
        body: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Text(
                      AppLocalizations.of(context)?.configureMirrorsSubtitle ??
                          'Configure RuTracker mirrors for optimal search performance. Enabled mirrors will be used automatically.',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: Theme.of(context).colorScheme.onSurface,
                          ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                  // Filter and sort chips
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Wrap(
                          spacing: 8.0,
                          runSpacing: 8.0,
                          children: [
                            FilterChip(
                              label: const Text('Только активные'),
                              selected: _showOnlyEnabled,
                              onSelected: (selected) {
                                setState(() {
                                  _showOnlyEnabled = selected;
                                  _applyFilters();
                                });
                              },
                            ),
                            FilterChip(
                              label: const Text('Только здоровые'),
                              selected: _showOnlyHealthy,
                              onSelected: (selected) {
                                setState(() {
                                  _showOnlyHealthy = selected;
                                  _applyFilters();
                                });
                              },
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8.0,
                          runSpacing: 8.0,
                          children: [
                            ChoiceChip(
                              label: const Text('По приоритету'),
                              selected: _sortBy == 'priority',
                              onSelected: (selected) {
                                if (selected) {
                                  setState(() {
                                    _sortBy = 'priority';
                                    _applyFilters();
                                  });
                                }
                              },
                            ),
                            ChoiceChip(
                              label: const Text('По здоровью'),
                              selected: _sortBy == 'health',
                              onSelected: (selected) {
                                if (selected) {
                                  setState(() {
                                    _sortBy = 'health';
                                    _applyFilters();
                                  });
                                }
                              },
                            ),
                            ChoiceChip(
                              label: const Text('По скорости'),
                              selected: _sortBy == 'rtt',
                              onSelected: (selected) {
                                if (selected) {
                                  setState(() {
                                    _sortBy = 'rtt';
                                    _applyFilters();
                                  });
                                }
                              },
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  Expanded(
                    child: RefreshIndicator(
                      onRefresh: _loadMirrors,
                      child: _filteredMirrors.isEmpty && !_isLoading
                          ? Center(
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const Icon(Icons.filter_list, size: 64),
                                  const SizedBox(height: 16),
                                  Text(
                                    'Нет зеркал, соответствующих фильтрам',
                                    style:
                                        Theme.of(context).textTheme.titleMedium,
                                  ),
                                  const SizedBox(height: 8),
                                  TextButton(
                                    onPressed: () {
                                      setState(() {
                                        _showOnlyEnabled = false;
                                        _showOnlyHealthy = false;
                                        _applyFilters();
                                      });
                                    },
                                    child: const Text('Сбросить фильтры'),
                                  ),
                                ],
                              ),
                            )
                          : ListView.separated(
                              itemCount: _filteredMirrors.length,
                              itemBuilder: (context, index) =>
                                  _buildMirrorTile(_filteredMirrors[index]),
                              separatorBuilder: (context, index) =>
                                  const SizedBox(height: 8),
                            ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        ElevatedButton.icon(
                          onPressed: _isBulkTesting ? null : _testAllMirrors,
                          icon: _isBulkTesting
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator())
                              : const Icon(Icons.health_and_safety),
                          label: Text(AppLocalizations.of(context)
                                  ?.testAllMirrorsButton ??
                              'Test All Mirrors'),
                        ),
                        const SizedBox(height: 8),
                        OutlinedButton.icon(
                          onPressed: _setBestMirrorAsActive,
                          icon: const Icon(Icons.auto_mode),
                          label: const Text('Set Best Mirror'),
                        ),
                        const SizedBox(height: 8),
                        ElevatedButton.icon(
                          onPressed: _addCustomMirror,
                          icon: const Icon(Icons.add),
                          label: Text(
                            AppLocalizations.of(context)
                                    ?.addCustomMirrorButtonText ??
                                'Add Custom Mirror',
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.onPrimary,
                            ),
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor:
                                Theme.of(context).colorScheme.primary,
                            foregroundColor:
                                Theme.of(context).colorScheme.onPrimary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
      );

  Widget _buildMirrorTile(Map<String, dynamic> mirror) {
    final url = mirror['url'] as String? ?? '';
    final enabled = mirror['enabled'] as bool? ?? false;
    final healthScore = mirror['health_score'] as int? ?? 0;
    final healthStatus = mirror['health_status'] as String? ?? 'Unknown';
    final priority = mirror['priority'] as int? ?? 5;
    final rtt = mirror['rtt'] as int?;
    final lastOk = mirror['last_ok'] as String?;
    final isActive = url == _activeMirror;

    // Determine status based on mirror properties and health
    final String statusText;
    final Color statusColor;

    if (!enabled) {
      statusText =
          AppLocalizations.of(context)?.disabledStatusText ?? 'Disabled';
      statusColor = Colors.grey;
    } else if (healthScore >= 80) {
      statusText = healthStatus;
      statusColor = Colors.green;
    } else if (healthScore >= 60) {
      statusText = healthStatus;
      statusColor = Colors.green.shade600;
    } else if (healthScore >= 40) {
      statusText = 'Degraded';
      statusColor = Colors.orange;
    } else if (healthScore >= 20) {
      statusText = 'Poor';
      statusColor = Colors.orange.shade800;
    } else {
      statusText = 'Unhealthy';
      statusColor = Colors.red;
    }

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      elevation: isActive ? 4 : 2,
      color: Theme.of(context).colorScheme.surface,
      // Add border if active
      shape: isActive
          ? RoundedRectangleBorder(
              side: const BorderSide(color: Colors.green, width: 2),
              borderRadius: BorderRadius.circular(12),
            )
          : null,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Domain header with bold font and active indicator
            Row(
              children: [
                Expanded(
                  child: Text(
                    url,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.onSurface,
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),
                ),
                if (isActive) ...[
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.green,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.check_circle,
                          color: Colors.white,
                          size: 16,
                        ),
                        SizedBox(width: 4),
                        Text(
                          'Active',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ),

            const SizedBox(height: 8),

            // Status row with icon and text
            Row(
              children: [
                Icon(
                  enabled ? Icons.check_circle : Icons.cancel,
                  color: statusColor,
                  size: 16,
                ),
                const SizedBox(width: 8),
                Text(
                  statusText,
                  style: TextStyle(
                    color: statusColor,
                    fontWeight: FontWeight.w500,
                    fontSize: 14,
                  ),
                ),
              ],
            ),

            const SizedBox(height: 8),

            // Health score progress bar
            if (enabled && healthScore > 0) ...[
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Здоровье: $healthScore%',
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.onSurface,
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      if (rtt != null)
                        Text(
                          'RTT: $rtt ms',
                          style: TextStyle(
                            color: Theme.of(context)
                                .colorScheme
                                .onSurface
                                .withAlpha(178),
                            fontSize: 12,
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: healthScore / 100,
                      backgroundColor: Colors.grey.shade300,
                      valueColor: AlwaysStoppedAnimation<Color>(statusColor),
                      minHeight: 8,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
            ],

            // Priority information with edit capability
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                InkWell(
                  onTap: () => _editPriority(url, priority),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        AppLocalizations.of(context)?.priorityText ??
                            'Priority: $priority',
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.onSurface,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Icon(
                        Icons.edit,
                        size: 16,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                    ],
                  ),
                ),
                if (lastOk != null)
                  Text(
                    AppLocalizations.of(context)?.lastCheckedText ??
                        'Проверено: ${_formatDate(lastOk)}',
                    style: TextStyle(
                      color: Theme.of(context)
                          .colorScheme
                          .onSurface
                          .withAlpha(178),
                      fontSize: 12,
                    ),
                  ),
              ],
            ),

            const SizedBox(height: 12),

            // Action buttons row
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                // Left side: test button, set as active, copy, delete
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ElevatedButton.icon(
                      onPressed: (_testingStates[url] ?? false)
                          ? null
                          : () => _testMirror(url),
                      icon: (_testingStates[url] ?? false)
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator())
                          : const Icon(Icons.wifi, size: 16),
                      label: Text(
                        AppLocalizations.of(context)?.testMirrorButtonText ??
                            'Test',
                        style: const TextStyle(fontSize: 12),
                      ),
                      style: ElevatedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 8),
                      ),
                    ),
                    if (!isActive && enabled) ...[
                      const SizedBox(width: 4),
                      IconButton(
                        icon: const Icon(Icons.star, size: 20),
                        color: Colors.amber,
                        tooltip: 'Установить как активное',
                        onPressed: () => _setActiveMirror(url),
                      ),
                    ],
                    const SizedBox(width: 4),
                    IconButton(
                      icon: const Icon(Icons.copy, size: 18),
                      tooltip: 'Копировать URL',
                      onPressed: () => _copyUrlToClipboard(url),
                    ),
                    const SizedBox(width: 4),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, size: 18),
                      color: Colors.red.shade400,
                      tooltip: 'Удалить зеркало',
                      onPressed: () => _deleteMirror(url),
                    ),
                  ],
                ),

                // Right side: Enable/disable switch
                Row(
                  children: [
                    Text(
                      enabled
                          ? AppLocalizations.of(context)?.activeStatusText ??
                              'Active'
                          : AppLocalizations.of(context)?.disabledStatusText ??
                              'Disabled',
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.onSurface,
                        fontSize: 12,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Switch(
                      value: enabled,
                      onChanged: (value) => _toggleMirror(url, value),
                      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  String _formatDate(String? isoDate) {
    if (isoDate == null) {
      return AppLocalizations.of(context)?.neverDateText ?? 'Never';
    }
    try {
      final date = DateTime.parse(isoDate);
      return '${date.day}.${date.month}.${date.year} ${date.hour}:${date.minute.toString().padLeft(2, '0')}';
    } on Exception {
      return AppLocalizations.of(context)?.invalidDateText ?? 'Invalid date';
    }
  }
}
