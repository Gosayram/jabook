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

/// Defines restrictions for guest mode.
///
/// This class contains the list of features that are restricted
/// when the user is in guest mode.
class GuestModeRestrictions {
  /// Private constructor to prevent instantiation.
  GuestModeRestrictions._();

  /// Set of restricted features in guest mode.
  static const Set<String> restrictedFeatures = {
    'search', // Search for audiobooks
    'downloads', // Downloads
    'favorites', // Favorites
    'playlists', // Playlists
    'history', // Listening history
    'recommendations', // Recommendations
  };

  /// Map of feature names to their restriction messages.
  static const Map<String, String> featureMessages = {
    'search': 'searchRestricted',
    'downloads': 'downloadRestricted',
    'favorites': 'favorites_restricted_message',
    'playlists': 'playlists_restricted_message',
    'history': 'history_restricted_message',
    'recommendations': 'recommendations_restricted_message',
  };

  /// Checks if a feature is restricted in guest mode.
  static bool isRestricted(String feature) =>
      restrictedFeatures.contains(feature);

  /// Gets the restriction message key for a feature.
  static String? getRestrictionMessageKey(String feature) =>
      featureMessages[feature];
}
