import 'dart:async';
import 'dart:io';

import 'package:jabook/core/logging/structured_logger.dart';

/// Result of a DNS lookup operation.
class DnsLookupResult {
  /// Creates a new DNS lookup result.
  ///
  /// [host] - The hostname that was resolved.
  /// [ipAddresses] - List of IP addresses found.
  /// [resolveTime] - Time taken to resolve the hostname.
  /// [type] - Type of address resolved (IPv4, IPv6, or any).
  /// [success] - Whether the lookup was successful.
  /// [error] - Error message if lookup failed.
  DnsLookupResult({
    required this.host,
    required this.ipAddresses,
    required this.resolveTime,
    required this.type,
    required this.success,
    this.error,
  });

  /// The hostname that was resolved.
  final String host;

  /// List of IP addresses found.
  final List<String> ipAddresses;

  /// Time taken to resolve the hostname.
  final Duration resolveTime;

  /// Type of address resolved (IPv4, IPv6, or any).
  final InternetAddressType type;

  /// Whether the lookup was successful.
  final bool success;

  /// Error message if lookup failed.
  final String? error;

  @override
  String toString() => success
      ? 'DNS "$host": ${ipAddresses.join(", ")} (type=$type, ${resolveTime.inMilliseconds} ms)'
      : 'DNS "$host": failed (${resolveTime.inMilliseconds} ms) - ${error ?? "unknown error"}';
}

/// Performs a DNS lookup for the given hostname and measures resolution time.
///
/// [host] - The hostname to resolve (without protocol, e.g., "rutracker.net").
/// [type] - The type of address to resolve (IPv4, IPv6, or any).
/// [operationId] - Optional operation ID for logging correlation.
///
/// Returns [DnsLookupResult] with IP addresses and resolution time.
Future<DnsLookupResult> dnsLookup(
  String host, {
  InternetAddressType type = InternetAddressType.any,
  String? operationId,
}) async {
  final logger = StructuredLogger();
  final startTime = DateTime.now();
  final stopwatch = Stopwatch()..start();

  await logger.log(
    level: 'debug',
    subsystem: 'network',
    message: 'DNS lookup started',
    operationId: operationId,
    context: 'dns_lookup',
    extra: {
      'host': host,
      'address_type': type.toString(),
    },
  );

  try {
    final addresses = await InternetAddress.lookup(
      host,
      type: type,
    ).timeout(
      const Duration(seconds: 10),
      onTimeout: () =>
          throw TimeoutException('DNS lookup timeout after 10 seconds'),
    );

    stopwatch.stop();
    final resolveTime = stopwatch.elapsed;
    final ipAddresses = addresses.map((addr) => addr.address).toList();

    final result = DnsLookupResult(
      host: host,
      ipAddresses: ipAddresses,
      resolveTime: resolveTime,
      type: type,
      success: true,
    );

    final duration = DateTime.now().difference(startTime).inMilliseconds;

    await logger.log(
      level: 'info',
      subsystem: 'network',
      message: 'DNS lookup completed successfully',
      operationId: operationId,
      context: 'dns_lookup',
      durationMs: duration,
      extra: {
        'host': host,
        'address_type': type.toString(),
        'ip_addresses': ipAddresses,
        'ip_count': ipAddresses.length,
        'resolve_time_ms': resolveTime.inMilliseconds,
        'has_ipv4': addresses.any((a) => a.type == InternetAddressType.IPv4),
        'has_ipv6': addresses.any((a) => a.type == InternetAddressType.IPv6),
        'all_addresses': addresses
            .map((a) => {
                  'address': a.address,
                  'type': a.type.toString(),
                  'host': a.host,
                  'is_loopback': a.isLoopback,
                })
            .toList(),
      },
    );

    return result;
  } on SocketException catch (e) {
    stopwatch.stop();
    final resolveTime = stopwatch.elapsed;
    final duration = DateTime.now().difference(startTime).inMilliseconds;

    final result = DnsLookupResult(
      host: host,
      ipAddresses: [],
      resolveTime: resolveTime,
      type: type,
      success: false,
      error: e.message,
    );

    await logger.log(
      level: 'error',
      subsystem: 'network',
      message: 'DNS lookup failed: SocketException',
      operationId: operationId,
      context: 'dns_lookup',
      durationMs: duration,
      cause: e.toString(),
      extra: {
        'host': host,
        'address_type': type.toString(),
        'resolve_time_ms': resolveTime.inMilliseconds,
        'error_code': e.osError?.errorCode,
        'error_message': e.osError?.message,
        'socket_exception_message': e.message,
      },
    );

    return result;
  } on TimeoutException catch (e) {
    stopwatch.stop();
    final resolveTime = stopwatch.elapsed;
    final duration = DateTime.now().difference(startTime).inMilliseconds;

    final result = DnsLookupResult(
      host: host,
      ipAddresses: [],
      resolveTime: resolveTime,
      type: type,
      success: false,
      error: 'Timeout: ${e.message}',
    );

    await logger.log(
      level: 'error',
      subsystem: 'network',
      message: 'DNS lookup failed: Timeout',
      operationId: operationId,
      context: 'dns_lookup',
      durationMs: duration,
      cause: e.toString(),
      extra: {
        'host': host,
        'address_type': type.toString(),
        'resolve_time_ms': resolveTime.inMilliseconds,
        'timeout_seconds': 10,
      },
    );

    return result;
  } on Exception catch (e) {
    stopwatch.stop();
    final resolveTime = stopwatch.elapsed;
    final duration = DateTime.now().difference(startTime).inMilliseconds;

    final result = DnsLookupResult(
      host: host,
      ipAddresses: [],
      resolveTime: resolveTime,
      type: type,
      success: false,
      error: e.toString(),
    );

    await logger.log(
      level: 'error',
      subsystem: 'network',
      message: 'DNS lookup failed: Exception',
      operationId: operationId,
      context: 'dns_lookup',
      durationMs: duration,
      cause: e.toString(),
      extra: {
        'host': host,
        'address_type': type.toString(),
        'resolve_time_ms': resolveTime.inMilliseconds,
        'stack_trace': (e is Error) ? (e as Error).stackTrace.toString() : null,
      },
    );

    return result;
  }
}
