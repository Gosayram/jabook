# Contributing to JaBook

## Before Opening a PR

Run the required quality gates from repository root:

```bash
make fmt-kotlin
make lint-kotlin
make test
make compile
```

## Test Selection by Change Type

- Audio/player logic changes:
  - `make test-audio`
  - `make test-player`
- Storage, migration, scanner, repository changes:
  - `make test-storage`
- Broad refactor or cross-feature changes:
  - run full `make test`

## PR Checklist

- Code compiles for both `beta` and `prod` debug variants.
- Lint passes (`ktlint` + `detekt` + i18n key checks).
- Unit tests pass for impacted areas.
- New behavior includes tests (or justification is provided).
- Planning/roadmap docs are updated when relevant.

## Commit and Scope Guidelines

- Keep PR scope focused (single feature/fix where possible).
- Avoid mixing refactor and behavior changes in one commit when possible.
- Do not include unrelated formatting-only changes outside touched files.

## Notes for Background Work

- For WorkManager tasks, use project constraint policy:
  - library scan/sync: battery-safe + storage/network constraints
  - user-triggered flows can use softer constraints where UX requires
