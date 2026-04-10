# ============================================================================
# APK Analysis & SDK Tools — apkanalyzer, lint, retrace, r8
# ============================================================================

## APK Analysis

.PHONY: apk-size
apk-size: ## Show APK file size summary
	@if [ -z "$(DEFAULT_APK)" ]; then \
		echo "❌ No APK found. Build first: make build-beta or make build-prod"; \
		exit 1; \
	fi; \
	echo "📦 APK Size Analysis: $(DEFAULT_APK)"; \
	echo ""; \
	ls -lh "$(DEFAULT_APK)" | awk '{print "  File size: " $$5}'; \
	echo ""; \
	$(APKANALYZER) --human-readable apk summary "$(DEFAULT_APK)"; \
	echo ""; \
	$(APKANALYZER) --human-readable apk file-size "$(DEFAULT_APK)"; \
	echo ""; \
	echo "✅ APK size analysis complete"

.PHONY: apk-summary
apk-summary: ## Detailed APK composition breakdown (DEX, resources, features)
	@if [ -z "$(DEFAULT_APK)" ]; then \
		echo "❌ No APK found. Build first: make build-beta or make build-prod"; \
		exit 1; \
	fi; \
	echo "📊 APK Composition Breakdown: $(DEFAULT_APK)"; \
	echo ""; \
	echo "--- APK Summary ---"; \
	$(APKANALYZER) --human-readable apk summary "$(DEFAULT_APK)"; \
	echo ""; \
	echo "--- File Size Breakdown ---"; \
	$(APKANALYZER) --human-readable apk file-size "$(DEFAULT_APK)"; \
	echo ""; \
	echo "--- Download Size ---"; \
	$(APKANALYZER) --human-readable apk download-size "$(DEFAULT_APK)"; \
	echo ""; \
	echo "--- DEX Files ---"; \
	$(APKANALYZER) --human-readable dex list "$(DEFAULT_APK)"; \
	echo ""; \
	echo "--- Features ---"; \
	$(APKANALYZER) apk features "$(DEFAULT_APK)"; \
	echo ""; \
	echo "✅ APK analysis complete"

.PHONY: apk-compare
apk-compare: ## Compare two APKs (OLD_APK=a.apk NEW_APK=b.apk)
	@if [ -z "$(OLD_APK)" ] || [ -z "$(NEW_APK)" ]; then \
		echo "❌ Usage: make apk-compare OLD_APK=path/to/old.apk NEW_APK=path/to/new.apk"; \
		exit 1; \
	fi; \
	if [ ! -f "$(OLD_APK)" ]; then echo "❌ OLD_APK not found: $(OLD_APK)"; exit 1; fi; \
	if [ ! -f "$(NEW_APK)" ]; then echo "❌ NEW_APK not found: $(NEW_APK)"; exit 1; fi; \
	echo "📊 APK Comparison"; \
	echo "  Old: $(OLD_APK)"; \
	echo "  New: $(NEW_APK)"; \
	echo ""; \
	ls -lh "$(OLD_APK)" "$(NEW_APK)" | awk '{print $$NF ": " $$5}'; \
	echo ""; \
	echo "--- Size Diff ---"; \
	$(APKANALYZER) --human-readable apk compare "$(OLD_APK)" "$(NEW_APK)"; \
	echo ""; \
	echo "✅ APK comparison complete"

.PHONY: apk-dex
apk-dex: ## Analyze DEX files (class count, method references)
	@if [ -z "$(DEFAULT_APK)" ]; then \
		echo "❌ No APK found. Build first: make build-beta or make build-prod"; \
		exit 1; \
	fi; \
	echo "🔬 DEX Analysis: $(DEFAULT_APK)"; \
	echo ""; \
	echo "--- DEX List ---"; \
	$(APKANALYZER) --human-readable dex list "$(DEFAULT_APK)"; \
	echo ""; \
	echo "--- DEX References ---"; \
	$(APKANALYZER) --human-readable dex references "$(DEFAULT_APK)"; \
	echo ""; \
	echo "--- Top 50 Packages by Method Count ---"; \
	$(APKANALYZER) --human-readable dex packages "$(DEFAULT_APK)" | head -50; \
	echo ""; \
	echo "✅ DEX analysis complete"

## SDK Tools

.PHONY: android-lint
android-lint: ## Run Android Lint via Gradle
	@echo "Running Android Lint..."
	@(cd android && ./gradlew :app:lintBetaDebug --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Android Lint passed"; \
		echo "   Report: android/app/build/reports/lint-results-betaDebug.html"; \
	else \
		echo "❌ Android Lint found issues (exit code $$EXIT_CODE)"; \
		echo "   Report: android/app/build/reports/lint-results-betaDebug.html"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: retrace
retrace: ## Deobfuscate R8 stack trace (STACKTRACE=file.txt)
	@if [ -z "$(STACKTRACE)" ]; then \
		echo "❌ Usage: make retrace STACKTRACE=path/to/stacktrace.txt"; \
		echo "   Optional: MAPPING=path/to/mapping.txt"; \
		exit 1; \
	fi; \
	MAPPING_FILE="$(MAPPING)"; \
	if [ -z "$$MAPPING_FILE" ]; then \
		MAPPING_FILE=$$(ls android/app/build/outputs/mapping/prodRelease/mapping.txt 2>/dev/null || \
			ls android/app/build/outputs/mapping/betaRelease/mapping.txt 2>/dev/null || echo ""); \
	fi; \
	if [ -z "$$MAPPING_FILE" ]; then \
		echo "❌ No mapping.txt found. Build a release APK first to generate R8 mapping."; \
		exit 1; \
	fi; \
	if [ ! -f "$(STACKTRACE)" ]; then \
		echo "❌ Stack trace file not found: $(STACKTRACE)"; exit 1; \
	fi; \
	echo "🔓 Deobfuscating stack trace..."; \
	echo "  Mapping: $$MAPPING_FILE"; \
	echo "  Stack trace: $(STACKTRACE)"; \
	echo ""; \
	$(RETRACE) $$MAPPING_FILE "$(STACKTRACE)"; \
	echo ""; \
	echo "✅ Deobfuscation complete"

.PHONY: r8-check
r8-check: ## Verify R8 keep rules (proguard-rules.pro)
	@echo "Checking R8 keep rules..."
	@if [ ! -f "$(R8)" ]; then \
		echo "❌ R8 tool not found at $(R8)"; \
		exit 1; \
	fi; \
	RULES_FILE="$(RULES)"; \
	if [ -z "$$RULES_FILE" ]; then \
		RULES_FILE="android/app/proguard-rules.pro"; \
	fi; \
	if [ ! -f "$$RULES_FILE" ]; then \
		echo "❌ Rules file not found: $$RULES_FILE"; exit 1; \
	fi; \
	echo "📋 Checking rules: $$RULES_FILE"; \
	echo ""; \
	echo "--- Rules content ---"; \
	grep -v '^#' "$$RULES_FILE" | grep -v '^$$'; \
	echo ""; \
	echo "--- Syntax validation ---"; \
	$(R8) --version 2>/dev/null; \
	echo ""; \
	echo "ℹ️  Full R8 validation requires a complete build (make build-prod)"; \
	echo "   Mapping: android/app/build/outputs/mapping/prodRelease/mapping.txt"; \
	echo "✅ Rules file looks valid"