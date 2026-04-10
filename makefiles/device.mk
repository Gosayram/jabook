# ============================================================================
# Device Commands — run, install, logcat, uninstall
# ============================================================================

## Device

.PHONY: run
run: ## Install and run dev debug APK on connected device
	@APK_PATH="android/app/build/outputs/apk/dev/debug/app-dev-debug.apk"; \
	if [ ! -f "$$APK_PATH" ]; then \
		echo "Error: APK not found at $$APK_PATH"; \
		echo "Please build the APK first using: make build-dev"; \
		exit 1; \
	fi; \
	echo "Installing APK to connected device..."; \
	adb install -r "$$APK_PATH"; \
	echo "✅ APK installed successfully"

.PHONY: devices
devices: ## List connected Android devices
	@adb devices -l

.PHONY: logcat
logcat: ## Show logcat filtered for jabook (Ctrl+C to stop)
	@echo "Showing logcat (Ctrl+C to stop)..."
	@adb logcat | grep -i --color=auto -E "jabook|AndroidRuntime|FATAL"

.PHONY: clear-logcat
clear-logcat: ## Clear logcat buffer
	@adb logcat -c
	@echo "✅ Logcat cleared"

.PHONY: install-dev
install-dev: ## Install dev debug APK via Gradle
	@cd android && ./gradlew :app:installDevDebug --no-daemon
	@echo "✅ Dev APK installed"

.PHONY: install-beta
install-beta: ## Install beta release APK to device
	@adb install -r build/app/outputs/apk/beta/release/app-beta-release.apk
	@echo "✅ Beta APK installed"

.PHONY: install-prod
install-prod: ## Install prod release APK via Gradle
	@cd android && ./gradlew :app:installProdRelease --no-daemon
	@echo "✅ Prod APK installed"

.PHONY: uninstall
uninstall: ## Uninstall app from connected device
	@adb uninstall com.jabook.app.jabook || echo "App not installed"
	@echo "✅ App uninstalled"

.PHONY: run-beta
run-beta: build-signed-apk-beta ## Build, install, and launch beta on device with logs
	@adb install -r build/app/outputs/apk/beta/release/app-beta-arm64-v8a-release.apk
	@adb logcat -c
	@echo "Launching Jabook Beta..."
	@adb shell am start -n com.jabook.app.jabook.beta/com.jabook.app.jabook.compose.ComposeMainActivity
	@echo ""
	@echo "📱 App launched! Watching logs (Ctrl+C to stop)..."
	@adb logcat | grep -E "(Jabook|jabook|FATAL|AndroidRuntime|DEBUG|ComposeMainActivity)"

.PHONY: run-beta-debug
run-beta-debug: build-signed-apk-beta ## Build, install beta with verbose logs saved to file
	@adb install -r build/app/outputs/apk/beta/release/app-beta-arm64-v8a-release.apk
	@adb logcat -c
	@echo "Launching Jabook Beta with debug flags..."
	@adb shell am start -n com.jabook.app.jabook.beta/com.jabook.app.jabook.compose.ComposeMainActivity
	@echo ""
	@echo "📱 App launched! Saving verbose logs to startup_profile.log..."
	@echo "Press Ctrl+C to stop logging"
	@adb logcat > startup_profile.log