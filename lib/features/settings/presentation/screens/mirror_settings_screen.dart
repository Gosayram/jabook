import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/endpoints/endpoint_provider.dart';
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
  bool _isLoading = false;
  final _testingStates = <String, bool>{};
  bool _isBulkTesting = false;

  @override
  void initState() {
    super.initState();
    _loadMirrors();
  }

  Future<void> _loadMirrors() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final mirrors = await endpointManager.getAllEndpointsWithHealth();
      // Sort mirrors by priority (ascending)
      mirrors.sort(
          (a, b) => (a['priority'] as int).compareTo(b['priority'] as int));
      setState(() {
        _mirrors = mirrors;
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
      await endpointManager.healthCheck(url);

      // Reload mirrors to get updated status
      await _loadMirrors();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(
                  AppLocalizations.of(context)?.mirrorTestSuccessMessage ??
                      'Mirror $url tested successfully')),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(
                  AppLocalizations.of(context)?.mirrorTestFailedMessage ??
                      'Failed to test mirror $url: $e')),
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
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      // Test all enabled mirrors sequentially to avoid burst
      for (final m in _mirrors) {
        if (m['enabled'] == true) {
          await endpointManager.healthCheck(m['url'] as String);
        }
      }
      await _loadMirrors();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(AppLocalizations.of(context)
                      ?.mirrorHealthCheckCompletedMessage ??
                  'Mirror health check completed')),
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
      await endpointManager.updateEndpointStatus(url, enabled);

      // Reload mirrors to get updated status
      await _loadMirrors();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(AppLocalizations.of(context)?.mirrorStatusText ??
                  'Mirror ${enabled ? 'enabled' : 'disabled'}')),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(
                  AppLocalizations.of(context)?.failedToUpdateMirrorMessage ??
                      'Failed to update mirror: $e')),
        );
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
        final endpointManager = ref.read(endpointManagerProvider);
        final priority = int.tryParse(priorityController.text) ?? 5;
        await endpointManager.addEndpoint(urlController.text, priority);
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
                  Expanded(
                    child: ListView.separated(
                      itemCount: _mirrors.length,
                      itemBuilder: (context, index) =>
                          _buildMirrorTile(_mirrors[index]),
                      separatorBuilder: (context, index) =>
                          const SizedBox(height: 8),
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
      elevation: 2,
      color: Theme.of(context).colorScheme.surface,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Domain header with bold font
            Text(
              url,
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurface,
                fontWeight: FontWeight.bold,
                fontSize: 16,
              ),
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

            // Priority information
            Text(
              AppLocalizations.of(context)?.priorityText ??
                  'Priority: $priority',
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurface,
                fontSize: 14,
              ),
            ),

            // Response time and last check - secondary information
            if (rtt != null) ...[
              const SizedBox(height: 4),
              Text(
                AppLocalizations.of(context)?.responseTimeText ??
                    'Response time: $rtt ms',
                style: TextStyle(
                  color: Theme.of(context)
                      .colorScheme
                      .onSurface
                      .withAlpha(178), // 0.7 opacity equivalent
                  fontSize: 12,
                ),
              ),
            ],

            if (lastOk != null) ...[
              const SizedBox(height: 4),
              Text(
                AppLocalizations.of(context)?.lastCheckedText ??
                    'Last checked: ${_formatDate(lastOk)}',
                style: TextStyle(
                  color: Theme.of(context)
                      .colorScheme
                      .onSurface
                      .withAlpha(178), // 0.7 opacity equivalent
                  fontSize: 12,
                ),
              ),
            ],

            const SizedBox(height: 12),

            // Action buttons row
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                // Individual test button
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
                        'Test this mirror',
                    style: const TextStyle(fontSize: 12),
                  ),
                  style: ElevatedButton.styleFrom(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  ),
                ),

                // Enable/disable switch
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
