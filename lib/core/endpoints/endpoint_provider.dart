import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/data/db/app_database.dart';

/// Provider for EndpointManager instance
final endpointManagerProvider = Provider<EndpointManager>((ref) {
  final appDatabase = ref.watch(appDatabaseProvider);
  return EndpointManager(appDatabase.database);
});

/// Provider for AppDatabase instance
final appDatabaseProvider = Provider<AppDatabase>((ref) => AppDatabase());