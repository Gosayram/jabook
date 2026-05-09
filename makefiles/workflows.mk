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

.PHONY: check-all
check-all: lint-kotlin compile hilt-graph-check ## Full local quality gate (lint + compile + Hilt graph)

.PHONY: test-all
test-all: test-ci ## Backward-compatible alias for CI-grade unit test suite

.PHONY: check-all-with-tests
check-all-with-tests: lint-kotlin compile hilt-graph-check test-ci ## Full gate incl. strict unit tests

.PHONY: check
check: check-all ## Backward-compatible alias for check-all
