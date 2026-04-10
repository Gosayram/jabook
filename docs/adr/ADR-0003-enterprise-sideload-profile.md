# ADR-0003: Enterprise sideload profile strategy

## Context

JaBook distributes outside a single app store path (direct APK, enterprise channels, internal QA). We need predictable behavior for storage/network/security defaults across sideload environments.

## Options considered

1. Single runtime profile for all environments.
2. Flavor/profile-specific policy overlays with shared core defaults.

## Decision

Use profile-aware policy overlays for sideload/enterprise scenarios while keeping core behavior shared and deterministic.

## Consequences

- Safer environment-specific defaults without forking business logic.
- Easier release validation per channel (beta/prod/sideload).
- Requires explicit documentation and CI checks to avoid profile drift.
