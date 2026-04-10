# ============================================================================
# Git Commands — commit, tag, push
# ============================================================================

## Git

.PHONY: commit
commit: ## Generate AI commit message and copy to clipboard
	@aicommit2 -acs 2>&1 | tail -n 2 | head -n 1
	@echo "📋 Commit message copied to clipboard"

.PHONY: tag
tag: ## Create git tag from current version
	@if [ ! -f ".release-version" ]; then \
		echo "Error: .release-version file not found"; \
		exit 1; \
	fi
	@TAG_VERSION="$(VERSION)"; \
	if git rev-parse "$$TAG_VERSION" >/dev/null 2>&1; then \
		echo "Error: Tag $$TAG_VERSION already exists"; \
		exit 1; \
	fi; \
	echo "Creating tag $$TAG_VERSION..."; \
	git tag -a "$$TAG_VERSION" -m "Release $$TAG_VERSION"; \
	echo "✅ Tag $$TAG_VERSION created"

.PHONY: push-tag
push-tag: tag ## Create tag and push to remote
	@TAG_VERSION="$(VERSION)"; \
	CURRENT_BRANCH=$$(git branch --show-current 2>/dev/null || echo ""); \
	REMOTE=$$(git config branch.$$CURRENT_BRANCH.remote 2>/dev/null || echo "origin"); \
	if [ -z "$$REMOTE" ] || [ "$$REMOTE" = "" ]; then \
		REMOTE="origin"; \
	fi; \
	echo "Pushing tag $$TAG_VERSION to $$REMOTE..."; \
	git push $$REMOTE "$$TAG_VERSION"; \
	echo "✅ Tag $$TAG_VERSION pushed to $$REMOTE"