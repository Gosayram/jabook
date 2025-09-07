
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:html/parser.dart' as parser;
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:windows1251/windows1251.dart';

/// Service for handling RuTracker-specific operations including pagination,
/// filtering, and category navigation.
class RuTrackerService {
  /// Creates a new RuTrackerService instance.
  RuTrackerService();

  /// Fetches a page with optional pagination and filtering
  Future<String> fetchPage({
    required String url,
    int page = 1,
    int perPage = 50,
    Map<String, String>? filters,
  }) async {
    try {
      final dio = await DioClient.instance;
      final fullUrl = _buildUrlWithParams(url, page, perPage, filters);
      
      final response = await dio.get(
        fullUrl,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      return response.data.toString();
    } on DioException catch (e) {
      throw NetworkFailure('Failed to fetch page: ${e.message}');
    }
  }

  /// Extracts pagination information from HTML
  Map<String, dynamic> parsePaginationInfo(String html) {
    try {
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      final paginationInfo = <String, dynamic>{
        'currentPage': 1,
        'totalPages': 1,
        'hasNext': false,
        'hasPrevious': false,
      };

      // Parse pagination links
      final pageLinks = document.querySelectorAll('a.pg');
      if (pageLinks.isNotEmpty) {
        final pageNumbers = pageLinks
            .map((link) => _extractPageNumber(link.attributes['href'] ?? ''))
            .where((page) => page != null)
            .cast<int>()
            .toList();

        if (pageNumbers.isNotEmpty) {
          paginationInfo['totalPages'] = pageNumbers.reduce((a, b) => a > b ? a : b);
        }

        // Check for next/previous links
        final nextLink = document.querySelector('a.pg[href*="start="]');
        paginationInfo['hasNext'] = nextLink != null;
      }

      // Parse current page from URL or active page indicator
      final currentPageMatch = RegExp(r'start=(\d+)').firstMatch(document.documentElement?.outerHtml ?? '');
      if (currentPageMatch != null) {
        final start = int.parse(currentPageMatch.group(1)!);
        paginationInfo['currentPage'] = (start ~/ 50) + 1; // Default perPage is 50
      }

      return paginationInfo;
    } on Exception {
      return {'currentPage': 1, 'totalPages': 1, 'hasNext': false, 'hasPrevious': false};
    }
  }

  /// Extracts sorting options from forum page
  List<Map<String, String>> parseSortingOptions(String html) {
    final options = <Map<String, String>>[];
    try {
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      final sortSelect = document.querySelector('select#sort');
      
      if (sortSelect != null) {
        final optionsElements = sortSelect.querySelectorAll('option');
        for (final option in optionsElements) {
          options.add({
            'value': option.attributes['value'] ?? '',
            'label': option.text.trim(),
          });
        }
      }
    } on Exception {
      // Fallback to default options
    }

    if (options.isEmpty) {
      options.addAll([
        {'value': '0', 'label': 'Посл. сообщение'},
        {'value': '1', 'label': 'Название темы'},
        {'value': '2', 'label': 'Время размещения'},
      ]);
    }

    return options;
  }

  /// Builds URL with pagination and filter parameters
  String _buildUrlWithParams(String baseUrl, int page, int perPage, Map<String, String>? filters) {
    final uri = Uri.parse(baseUrl);
    final params = Map<String, String>.from(uri.queryParameters);

    // Add pagination
    if (page > 1) {
      params['start'] = ((page - 1) * perPage).toString();
    }

    // Add filters
    if (filters != null) {
      params.addAll(filters);
    }

    return '${uri.origin}${uri.path}${params.isNotEmpty ? '?${Uri(queryParameters: params).query}' : ''}';
  }

  /// Extracts page number from URL
  int? _extractPageNumber(String url) {
    final match = RegExp(r'start=(\d+)').firstMatch(url);
    if (match != null) {
      final start = int.parse(match.group(1)!);
      return (start ~/ 50) + 1; // Assuming 50 items per page
    }
    return null;
  }

  /// Checks if a URL requires authentication
  bool requiresAuthentication(String html) {
    try {
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      
      // Check for login redirects or authentication requirements
      final documentText = document.text ?? '';
      return document.querySelector('form[action*="login.php"]') != null ||
             documentText.contains('войдите') ||
             documentText.contains('авторизац') ||
             documentText.contains('login') ||
             documentText.contains('authorization');
    } on Exception {
      return false;
    }
  }
}