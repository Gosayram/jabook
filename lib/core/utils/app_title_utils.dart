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

import 'package:jabook/core/config/app_config.dart';

/// Extension for adding flavor suffix to app titles.
extension AppTitleExtension on String {
  /// Adds flavor suffix to the title if not production build.
  ///
  /// Returns the title with flavor suffix for non-prod flavors:
  /// - prod: returns title as-is
  /// - beta: returns "title (beta)"
  /// - dev: returns "title (dev)"
  /// - stage: returns "title (stage)"
  String withFlavorSuffix() {
    final config = AppConfig();
    if (config.isProd) {
      return this;
    }
    return '$this (${config.flavor})';
  }
}
