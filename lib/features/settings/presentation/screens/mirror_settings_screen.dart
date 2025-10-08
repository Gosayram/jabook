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
  ConsumerState<MirrorSettingsScreen> createState() => _MirrorSettingsScreenState();
}

class _MirrorSettingsScreenState extends ConsumerState<MirrorSettingsScreen> {
  List<Map<String, dynamic>> _mirrors = [];
  bool _isLoading = false;
  final _testingStates = <String, bool>{};

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
      mirrors.sort((a, b) => (a['priority'] as int).compareTo(b['priority'] as int));
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
          SnackBar(content: Text('Failed to load mirrors: $e')),
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
          SnackBar(content: Text('Mirror $url tested successfully')),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to test mirror $url: $e')),
        );
      }
    } finally {
      setState(() {
        _testingStates[url] = false;
      });
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
          SnackBar(content: Text('Mirror ${enabled ? 'enabled' : 'disabled'}')),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to update mirror: $e')),
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
        title: const Text('Add Custom Mirror'),
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
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Add'),
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
            SnackBar(content: Text('Mirror ${urlController.text} added')),
          );
        }
      } on Exception catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to add mirror: $e')),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {

    return Scaffold(
      appBar: AppBar(
        title: Text('Mirror Settings'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text(
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
                    itemBuilder: (context, index) => _buildMirrorTile(_mirrors[index]),
                    separatorBuilder: (context, index) => const SizedBox(height: 8),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: ElevatedButton.icon(
                    onPressed: _addCustomMirror,
                    icon: const Icon(Icons.add),
                    label: Text(
                      'Add Custom Mirror',
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.onPrimary,
                      ),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Theme.of(context).colorScheme.primary,
                      foregroundColor: Theme.of(context).colorScheme.onPrimary,
                    ),
                  ),
                ),
              ],
            ),
    );
  }

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
      statusText = 'Disabled';
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
                'Response time: $rtt ms',
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurface.withAlpha(178), // 0.7 opacity equivalent
                  fontSize: 12,
                ),
              ),
            ],
            
            if (lastOk != null) ...[
              const SizedBox(height: 4),
              Text(
                'Last checked: ${_formatDate(lastOk)}',
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurface.withAlpha(178), // 0.7 opacity equivalent
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
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator())
                      : const Icon(Icons.wifi, size: 16),
                  label: Text(
                    'Test this mirror',
                    style: const TextStyle(fontSize: 12),
                  ),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  ),
                ),
                
                // Enable/disable switch
                Row(
                  children: [
                    Text(
                      enabled
                          ? 'Active'
                          : 'Disabled',
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
    if (isoDate == null) return 'Never';
    try {
      final date = DateTime.parse(isoDate);
      return '${date.day}.${date.month}.${date.year} ${date.hour}:${date.minute.toString().padLeft(2, '0')}';
    } on Exception {
      return 'Invalid date';
    }
  }
}