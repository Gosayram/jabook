# ============================================================================
# Version Management — show, update, increment build numbers
# ============================================================================

## Version

.PHONY: version
version: ## Show current version from .release-version
	@echo "Current version: $(VERSION)"
	@echo "Full version (with build): $(FULL_VERSION)"

.PHONY: increment-build
increment-build: ## Increment build number in .release-version
	@bash scripts/increment-build.sh

.PHONY: update-version
update-version: ## Update version (NEW_VERSION=x.y.z or interactive)
	@if [ -z "$(NEW_VERSION)" ]; then \
		hack/update-version.sh; \
	else \
		hack/update-version.sh "$(NEW_VERSION)"; \
	fi