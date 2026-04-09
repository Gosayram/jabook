# ============================================================================
# Quick Workflows — combined commands for common development patterns
# ============================================================================

## Workflows

.PHONY: dev
dev: fmt-kotlin compile-dev install-dev ## Format → compile → install dev (full dev cycle)

.PHONY: beta
beta: fmt-kotlin compile-beta install-beta ## Format → compile → install beta (full beta cycle)

.PHONY: lint
lint: clean fmt-kotlin compile ## Clean → format → compile (full lint cycle)