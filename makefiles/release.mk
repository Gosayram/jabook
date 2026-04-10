# ============================================================================
# Release Commands — copy APKs, build-and-copy, aliases
# ============================================================================

## Release

.PHONY: copy-apk
copy-apk: ## Copy prod APKs to ~/Downloads/Jabook
	@echo "Copying prod APK files with version $(VERSION)..."
	@mkdir -p $(APK_DEST_DIR)
	@for apk in build/app/outputs/apk/prod/release/*.apk; do \
		if [ -f "$$apk" ]; then \
			filename=$$(basename "$$apk"); \
			newname="Jabook_$(VERSION)_$${filename#app-prod-release-}"; \
			cp -f "$$apk" "$(APK_DEST_DIR)/$$newname" && \
			echo "✅ Copied: $$newname"; \
		fi \
	done
	@echo "✅ Prod APK files copied to $(APK_DEST_DIR)/"

.PHONY: copy-apk-beta
copy-apk-beta: ## Copy beta APKs to ~/Downloads/Jabook
	@echo "Copying beta APK files with version $(VERSION)..."
	@mkdir -p $(APK_DEST_DIR)
	@if [ -f "build/app/outputs/apk/beta/release/app-beta-release.apk" ]; then \
		cp -f "build/app/outputs/apk/beta/release/app-beta-release.apk" "$(APK_DEST_DIR)/Jabook_$(VERSION)_beta.apk" && \
		echo "✅ Copied: Jabook_$(VERSION)_beta.apk"; \
	else \
		for apk in build/app/outputs/apk/beta/release/app-beta-*.apk; do \
			if [ -f "$$apk" ]; then \
				filename=$$(basename "$$apk"); \
				arch=$${filename#app-beta-}; \
				arch=$${arch%.apk}; \
				newname="Jabook_$(VERSION)_beta_$$arch.apk"; \
				cp -f "$$apk" "$(APK_DEST_DIR)/$$newname" && \
				echo "✅ Copied: $$newname"; \
			fi \
		done \
	fi
	@echo "✅ Beta APK files copied to $(APK_DEST_DIR)/"

.PHONY: build-and-copy
build-and-copy: build-signed-apk copy-apk ## Build signed prod APK and copy to Downloads

.PHONY: build-beta-and-copy
build-beta-and-copy: increment-build build-signed-apk-beta copy-apk-beta ## Increment build, build beta APK, copy to Downloads

# Aliases
.PHONY: build-signed-apk-copy
build-signed-apk-copy: build-and-copy ## Alias for build-and-copy

.PHONY: build-signed-apk-beta-copy
build-signed-apk-beta-copy: build-beta-and-copy ## Alias for build-beta-and-copy

.PHONY: build-android-signed-apk-copy
build-android-signed-apk-copy: build-and-copy ## Alias for build-and-copy

.PHONY: build-android-signed-apk-beta-copy
build-android-signed-apk-beta-copy: build-beta-and-copy ## Alias for build-beta-and-copy