# ADR-0001: Media3 over direct ExoPlayer API

## Context

JaBook has foreground playback service, MediaSession integration, widgets/notification controls, and platform integrations (Android Auto / system media controls). We need stable long-term APIs with AndroidX lifecycle and tooling alignment.

## Options considered

1. Use direct ExoPlayer-only APIs.
2. Use AndroidX Media3 stack (session + player + compose/ui modules).

## Decision

Adopt Media3 as the primary media stack, using ExoPlayer through Media3 abstractions.

## Consequences

- Better alignment with AndroidX releases and platform integrations.
- Cleaner session/service architecture for controllers and external surfaces.
- Slightly larger upgrade surface when Media3 introduces breaking changes, but centralized in one stack.
