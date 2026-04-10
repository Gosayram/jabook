# ADR-0002: Coil3 over Glide

## Context

JaBook UI is Compose-first. Running multiple image stacks increases APK size, memory pressure, and cache fragmentation.

## Options considered

1. Keep both Glide and Coil.
2. Standardize on Glide.
3. Standardize on Coil3.

## Decision

Standardize on Coil3 as the default image loading stack.

## Consequences

- Compose-native integration with simpler image pipelines.
- Reduced dependency overlap and easier cache tuning.
- Migration cost for legacy Glide call sites, tracked as incremental cleanup tasks.
