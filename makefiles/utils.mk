# ============================================================================
# Utility Scripts — changelog, copyright, l10n, strings, report
# ============================================================================

## Utility (hack/)

.PHONY: changelog
changelog: ## Generate CHANGELOG.md from git history
	@echo "Generating CHANGELOG.md..."
	@bash hack/generate-changelog.sh
	@echo "✅ CHANGELOG.md generated"

.PHONY: module-graph
module-graph: ## Generate module dependency graph (dot)
	@./scripts/generate-module-graph.sh

.PHONY: check-module-graph
check-module-graph: ## Verify module dependency graph baseline is up to date
	@./scripts/check-module-graph.sh

.PHONY: check-copyright
check-copyright: ## Check copyright headers in source files
	@echo "Checking copyright headers..."
	@bash hack/check-copyright.sh

.PHONY: update-copyright
update-copyright: ## Update copyright headers
	@echo "Updating copyright headers..."
	@bash hack/update-copyright.sh
	@echo "✅ Copyright headers updated"

.PHONY: add-copyright
add-copyright: ## Add missing copyright headers
	@echo "Adding missing copyright headers..."
	@bash hack/add-copyright.sh
	@echo "✅ Copyright headers added"

## Localization

.PHONY: check-l10n
check-l10n: ## Check for l10n duplicates
	@echo "Checking for l10n duplicates..."
	@bash hack/check-l10n-duplicates.sh

.PHONY: clean-l10n
clean-l10n: ## Clean l10n duplicates (auto-removes, keeps first occurrence)
	@echo "Cleaning l10n duplicates..."
	@bash hack/check-l10n-duplicates.sh || echo "✅ Duplicates cleaned automatically"

.PHONY: fmt-l10n
fmt-l10n: clean-l10n ## Format localization files (cleanup duplicates)

.PHONY: strings
strings: ## Migrate hardcoded strings to resources
	@echo "🔄 Migrating hardcoded strings to resources..."
	@scripts/.venv/bin/python3 scripts/migrate_strings.py android
	@echo ""
	@echo "⚠️  Don't forget to run 'make clean-l10n' to remove any duplicates!"

.PHONY: strings-check
strings-check: ## Dry-run string migration (no changes)
	@echo "🔍 DRY RUN - Checking hardcoded strings..."
	@scripts/.venv/bin/python3 scripts/migrate_strings.py android --dry-run

## Reports

.PHONY: report
report: ## Analyze startup_profile.log and generate debug report
	@if [ ! -f "startup_profile.log" ]; then \
		echo "❌ Error: startup_profile.log not found"; \
		echo "Run 'make run-beta-debug' first to generate the log"; \
		exit 1; \
	fi
	@echo "📊 Analyzing startup_profile.log..."
	@python3 scripts/analyze_startup_log.py startup_profile.log --output .debug-report-docs.md
	@echo "✅ Debug report saved to .debug-report-docs.md"

## Arch Docs Sync

.PHONY: sync-arch-open
sync-arch-open: ## Sync open tasks: .closed-arch-docs.md -> .reborn-arch-docs.md
	@python3 scripts/sync_arch_docs_open_tasks.py --direction open-from-closed

.PHONY: sync-arch-closed
sync-arch-closed: ## Sync closed tasks: .reborn-arch-docs.md -> .closed-arch-docs.md
	@python3 scripts/sync_arch_docs_open_tasks.py --direction closed-from-reborn

.PHONY: sync-arch
sync-arch: ## Run both arch docs sync directions
	@python3 scripts/sync_arch_docs_open_tasks.py --direction both
