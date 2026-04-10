# ============================================================================
# Build Commands — assemble APKs and App Bundles
# ============================================================================

## Build

.PHONY: build-dev
build-dev: ## Build dev debug APK
	@cd android && ./gradlew :app:assembleDevDebug
	@echo "✅ Dev debug APK built at: build/app/outputs/apk/dev/debug"

.PHONY: build-beta
build-beta: ## Build beta release APK
	@cd android && ./gradlew :app:assembleBetaRelease
	@echo "✅ Beta release APK built at: build/app/outputs/apk/beta/release"

.PHONY: build-prod
build-prod: ## Build prod release APK
	@cd android && ./gradlew :app:assembleProdRelease
	@echo "✅ Prod release APK built at: build/app/outputs/apk/prod/release"

.PHONY: build-bundle-prod
build-bundle-prod: ## Build prod App Bundle (AAB)
	@cd android && ./gradlew :app:bundleProdRelease
	@echo "✅ Prod AAB built at: build/app/outputs/bundle/prodRelease"

.PHONY: build-signed-apk
build-signed-apk: use-existing-android-cert patch-gradle-signing ## Build signed prod APKs (split + universal)
	@cd android && ./gradlew :app:assembleProdRelease
	@echo "✅ Signed prod APKs built at: android/app/build/outputs/apk/prod/release/"

.PHONY: build-signed-apk-beta
build-signed-apk-beta: use-existing-android-cert patch-gradle-signing ## Build signed beta APKs (split + universal)
	@cd android && ./gradlew :app:assembleBetaRelease
	@echo "✅ Signed beta APKs built at: build/app/outputs/apk/beta/release"