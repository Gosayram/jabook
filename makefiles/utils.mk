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

## Library Sources Sync

.PHONY: sync-lib-sources
sync-lib-sources: ## Sync test_results/source_codes/jabook_libs_sources from project direct deps
	@python3 scripts/sync_jabook_lib_sources.py

.PHONY: sync-lib-sources-dry
sync-lib-sources-dry: ## Dry-run library sources sync without file changes
	@python3 scripts/sync_jabook_lib_sources.py --dry-run --verbose

## CodeRabbit

.PHONY: coderabbit-recs
coderabbit-recs: ## Extract open CodeRabbit recs (OUT optional; default: test_results/coderabbit_<pr>_<org>_<repo>_issue.md)
	@if [ -z "$(PR)" ] && [ -z "$(PR_NUMBER)" ]; then \
		echo "❌ Missing PR/PR_NUMBER."; \
		echo "Usage examples:"; \
		echo "  export PR=127 REPO=owner/repo && make coderabbit-recs"; \
		echo "  export PR_NUMBER=127 REPO=owner/repo && make coderabbit-recs"; \
		echo "  export PR=owner/repo/pull/127 && make coderabbit-recs"; \
		echo "  export PR=https://github.com/owner/repo/pull/127 && make coderabbit-recs"; \
		exit 1; \
	fi
	@PR_INPUT="$(PR)"; \
	if [ -z "$$PR_INPUT" ]; then PR_INPUT="$(PR_NUMBER)"; fi; \
	case "$$PR_INPUT" in \
		http*|*"/pull/"*) FINAL_PR="$$PR_INPUT" ;; \
		*[!0-9]*) FINAL_PR="$$PR_INPUT" ;; \
		*) \
			if [ -z "$(REPO)" ]; then \
				echo "❌ REPO is required when PR is numeric."; \
				echo "Example: export PR=127 REPO=owner/repo"; \
				exit 1; \
			fi; \
			FINAL_PR="$(REPO)/pull/$$PR_INPUT" ;; \
	esac; \
	PR_NUM=$$(printf '%s' "$$FINAL_PR" | awk -F'/pull/' '{print $$2}' | awk -F'/' '{print $$1}'); \
	if [ -z "$$PR_NUM" ]; then PR_NUM="unknown"; fi; \
	OWNER_REPO=$$(printf '%s' "$$FINAL_PR" | sed -E 's#^https?://github.com/##' | sed -E 's#/pull/.*$$##'); \
	ORG=$$(printf '%s' "$$OWNER_REPO" | awk -F'/' '{print $$1}'); \
	REPO_NAME=$$(printf '%s' "$$OWNER_REPO" | awk -F'/' '{print $$2}'); \
	if [ -z "$$ORG" ]; then ORG="unknown"; fi; \
	if [ -z "$$REPO_NAME" ]; then REPO_NAME="repo"; fi; \
	OUT_FILE="$(OUT)"; \
	if [ -z "$$OUT_FILE" ]; then OUT_FILE="test_results/coderabbit_$${PR_NUM}_$${ORG}_$${REPO_NAME}_issue.md"; fi; \
	mkdir -p "$$(dirname "$$OUT_FILE")"; \
	python3 scripts/extract_coderabbit_recommendations.py "$$FINAL_PR" --output "$$OUT_FILE"; \
	echo "✅ CodeRabbit open-only report saved to $$OUT_FILE"

.PHONY: coderabbit-recs-debug
coderabbit-recs-debug: ## Extract all CodeRabbit recs incl. resolved (OUT optional; default: test_results/coderabbit_<pr>_<org>_<repo>_debug.md)
	@if [ -z "$(PR)" ] && [ -z "$(PR_NUMBER)" ]; then \
		echo "❌ Missing PR/PR_NUMBER."; \
		echo "Usage: export PR=127 REPO=owner/repo && make coderabbit-recs-debug"; \
		exit 1; \
	fi
	@PR_INPUT="$(PR)"; \
	if [ -z "$$PR_INPUT" ]; then PR_INPUT="$(PR_NUMBER)"; fi; \
	case "$$PR_INPUT" in \
		http*|*"/pull/"*) FINAL_PR="$$PR_INPUT" ;; \
		*[!0-9]*) FINAL_PR="$$PR_INPUT" ;; \
		*) \
			if [ -z "$(REPO)" ]; then \
				echo "❌ REPO is required when PR is numeric."; \
				echo "Example: export PR=127 REPO=owner/repo"; \
				exit 1; \
			fi; \
			FINAL_PR="$(REPO)/pull/$$PR_INPUT" ;; \
	esac; \
	PR_NUM=$$(printf '%s' "$$FINAL_PR" | awk -F'/pull/' '{print $$2}' | awk -F'/' '{print $$1}'); \
	if [ -z "$$PR_NUM" ]; then PR_NUM="unknown"; fi; \
	OWNER_REPO=$$(printf '%s' "$$FINAL_PR" | sed -E 's#^https?://github.com/##' | sed -E 's#/pull/.*$$##'); \
	ORG=$$(printf '%s' "$$OWNER_REPO" | awk -F'/' '{print $$1}'); \
	REPO_NAME=$$(printf '%s' "$$OWNER_REPO" | awk -F'/' '{print $$2}'); \
	if [ -z "$$ORG" ]; then ORG="unknown"; fi; \
	if [ -z "$$REPO_NAME" ]; then REPO_NAME="repo"; fi; \
	OUT_FILE="$(OUT)"; \
	if [ -z "$$OUT_FILE" ]; then OUT_FILE="test_results/coderabbit_$${PR_NUM}_$${ORG}_$${REPO_NAME}_debug.md"; fi; \
	mkdir -p "$$(dirname "$$OUT_FILE")"; \
	python3 scripts/extract_coderabbit_recommendations.py "$$FINAL_PR" --debug --output "$$OUT_FILE"; \
	echo "✅ CodeRabbit debug report saved to $$OUT_FILE"

.PHONY: coderabbit-recs-json
coderabbit-recs-json: ## Extract open CodeRabbit recs as JSON (OUT optional; default: test_results/coderabbit_pr<pr>_open_only.json)
	@if [ -z "$(PR)" ] && [ -z "$(PR_NUMBER)" ]; then \
		echo "❌ Missing PR/PR_NUMBER."; \
		echo "Usage: export PR=127 REPO=owner/repo OUT=report.json"; \
		exit 1; \
	fi
	@PR_INPUT="$(PR)"; \
	if [ -z "$$PR_INPUT" ]; then PR_INPUT="$(PR_NUMBER)"; fi; \
	case "$$PR_INPUT" in \
		http*|*"/pull/"*) FINAL_PR="$$PR_INPUT" ;; \
		*[!0-9]*) FINAL_PR="$$PR_INPUT" ;; \
		*) \
			if [ -z "$(REPO)" ]; then \
				echo "❌ REPO is required when PR is numeric."; \
				echo "Example: export PR=127 REPO=owner/repo"; \
				exit 1; \
			fi; \
			FINAL_PR="$(REPO)/pull/$$PR_INPUT" ;; \
	esac; \
	PR_NUM=$$(printf '%s' "$$FINAL_PR" | awk -F'/pull/' '{print $$2}' | awk -F'/' '{print $$1}'); \
	if [ -z "$$PR_NUM" ]; then PR_NUM="unknown"; fi; \
	OUT_FILE="$(OUT)"; \
	if [ -z "$$OUT_FILE" ]; then OUT_FILE="test_results/coderabbit_pr$${PR_NUM}_open_only.json"; fi; \
	mkdir -p "$$(dirname "$$OUT_FILE")"; \
	python3 scripts/extract_coderabbit_recommendations.py "$$FINAL_PR" --format json --output "$$OUT_FILE"; \
	echo "✅ CodeRabbit JSON report saved to $$OUT_FILE"
