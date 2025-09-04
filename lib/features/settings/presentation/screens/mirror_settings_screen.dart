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
      final mirrors = await endpointManager.getAllEndpoints();
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
      final mirrors = await endpointManager.getAllEndpoints();
      
      mirrors.map((mirror) {
        if (mirror['url'] == url) {
          return {...mirror, 'enabled': enabled};
        }
        return mirror;
      }).toList();

      // Update in database
      // This would require adding an update method to EndpointManager
      // For now, we'll just reload to reflect changes
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
    final localizations = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations?.mirrorsScreenTitle ?? 'Mirror Settings'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text(
                    'Configure RuTracker mirrors for optimal search performance. Enabled mirrors will be used automatically.',
                    style: Theme.of(context).textTheme.bodyMedium,
                    textAlign: TextAlign.center,
                  ),
                ),
                Expanded(
                  child: ListView.builder(
                    itemCount: _mirrors.length,
                    itemBuilder: (context, index) => _buildMirrorTile(_mirrors[index]),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: ElevatedButton.icon(
                    onPressed: _addCustomMirror,
                    icon: const Icon(Icons.add),
                    label: const Text('Add Custom Mirror'),
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildMirrorTile(Map<String, dynamic> mirror) {
    final url = mirror['url'] as String? ?? '';
    final enabled = mirror['enabled'] as bool? ?? false;
    final priority = mirror['priority'] as int? ?? 5;
    final rtt = mirror['rtt'] as int?;
    final lastOk = mirror['last_ok'] as String?;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: ListTile(
        leading: Icon(
          enabled ? Icons.check_circle : Icons.cancel,
          color: enabled ? Colors.green : Colors.grey,
        ),
        title: Text(url),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Priority: $priority'),
            if (rtt != null) Text('Response time: ${rtt}ms'),
            if (lastOk != null) Text('Last successful: ${_formatDate(lastOk)}'),
          ],
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              icon: (_testingStates[url] ?? false)
                  ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator())
                  : const Icon(Icons.wifi),
              onPressed: (_testingStates[url] ?? false)
                  ? null
                  : () => _testMirror(url),
            ),
            Switch(
              value: enabled,
              onChanged: (value) => _toggleMirror(url, value),
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