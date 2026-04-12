# ============================================================================
# Development Commands — clean, compile, format, lint, test
# ============================================================================

## Development

.PHONY: clean
clean: ## Clean build artifacts
	@echo "Cleaning Android build artifacts..."
	@cd android && ./gradlew clean
	@rm -rf android/build android/app/build debug-info/
	@echo "✅ Cleaned build artifacts"

.PHONY: compile
compile: ## Compile Kotlin code for all flavors (syntax check)
	@echo "Compiling Kotlin code for all flavors..."
	@(cd android && rm -rf app/build/generated/room-schemas && ./gradlew :app:compileBetaDebugKotlin --no-daemon --no-parallel 2>&1); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -ne 0 ]; then \
		echo "❌ Kotlin compilation failed on beta with exit code $$EXIT_CODE"; \
		exit $$EXIT_CODE; \
	fi; \
	(cd android && rm -rf app/build/generated/room-schemas && ./gradlew :app:compileProdDebugKotlin --no-daemon --no-parallel 2>&1); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Kotlin compilation successful for all flavors"; \
	else \
		echo "❌ Kotlin compilation failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: compile-dev
compile-dev: ## Compile dev flavor only
	@cd android && ./gradlew :app:compileDevDebugKotlin --no-daemon
	@echo "✅ Dev flavor compiled"

.PHONY: compile-beta
compile-beta: ## Compile beta flavor only
	@cd android && ./gradlew :app:compileBetaDebugKotlin --no-daemon
	@echo "✅ Beta flavor compiled"

.PHONY: compile-prod
compile-prod: ## Compile prod flavor only
	@cd android && ./gradlew :app:compileProdDebugKotlin --no-daemon
	@echo "✅ Prod flavor compiled"

.PHONY: fmt-kotlin
fmt-kotlin: ## Format Kotlin code (ktlint + detekt auto-correct) + regenerate verification metadata
	@echo "Formatting Kotlin code with ktlint + detekt..."
	@(cd android && ./gradlew :app:ktlintFormat :app:detekt --auto-correct --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Kotlin code formatted successfully (ktlint + detekt)"; \
	else \
		echo "❌ Kotlin formatting failed with exit code $$EXIT_CODE"; \
		exit $$EXIT_CODE; \
	fi; \
	echo "Regenerating dependency verification metadata..."; \
	(cd android && ./gradlew --write-verification-metadata sha256 help --no-daemon 2>&1); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Dependency verification metadata regenerated"; \
	else \
		echo "⚠️  Dependency verification metadata regeneration failed (non-fatal)"; \
	fi

.PHONY: lint-kotlin
lint-kotlin: ## Lint Kotlin code (ktlint + detekt check + dependency verification)
	@echo "Linting Kotlin code with ktlint + detekt..."
	@echo "Checking coroutine test discipline (no Thread.sleep in unit tests)..."
	@if rg -n "Thread\\.sleep\\(" android/app/src/test/kotlin >/dev/null; then \
		rg -n "Thread\\.sleep\\(" android/app/src/test/kotlin; \
		echo "❌ Found Thread.sleep(...) in unit tests. Use runTest + advanceTimeBy/advanceUntilIdle instead."; \
		exit 1; \
	fi
	@echo "Verifying dependency checksums against verification-metadata.xml..."
	@(cd android && ./gradlew --dependency-verification=strict help --no-daemon 2>&1); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -ne 0 ]; then \
		echo "❌ Dependency verification failed — run 'make fmt-kotlin' to regenerate verification-metadata.xml"; \
		exit $$EXIT_CODE; \
	fi; \
	echo "✅ Dependency verification passed"
	@(cd android && ./gradlew :app:ktlintCheck :app:detekt --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		./scripts/check-i18n-keys.sh; \
		EXIT_CODE=$$?; \
		if [ $$EXIT_CODE -eq 0 ]; then \
			./scripts/check-logging-guard.sh; \
			EXIT_CODE=$$?; \
			if [ $$EXIT_CODE -eq 0 ]; then \
				./scripts/check-backup-rules.sh; \
				EXIT_CODE=$$?; \
				if [ $$EXIT_CODE -eq 0 ]; then \
					./scripts/check-deterministic-build.sh; \
					EXIT_CODE=$$?; \
					if [ $$EXIT_CODE -eq 0 ]; then \
						./scripts/check-module-graph.sh; \
						EXIT_CODE=$$?; \
						if [ $$EXIT_CODE -eq 0 ]; then \
							./scripts/check-plugin-version-lock.sh; \
							EXIT_CODE=$$?; \
							if [ $$EXIT_CODE -eq 0 ]; then \
								./scripts/check-receiver-export-guard.sh; \
								EXIT_CODE=$$?; \
								if [ $$EXIT_CODE -eq 0 ]; then \
									./scripts/check-edge-to-edge.sh; \
									EXIT_CODE=$$?; \
									if [ $$EXIT_CODE -eq 0 ]; then \
										./scripts/check-no-entity-in-presentation.sh; \
										EXIT_CODE=$$?; \
										if [ $$EXIT_CODE -eq 0 ]; then \
											echo "✅ Kotlin linting passed (ktlint + detekt + dependency verification + i18n keys + logging guard + backup audit + deterministic build guard + module graph guard + plugin/version lock guard + receiver export guard + edge-to-edge guard + entity presentation guard)"; \
										else \
											echo "❌ Entity presentation guard failed with exit code $$EXIT_CODE"; \
										fi; \
									else \
										echo "❌ Edge-to-edge guard failed with exit code $$EXIT_CODE"; \
									fi; \
								else \
									echo "❌ Receiver export guard failed with exit code $$EXIT_CODE"; \
								fi; \
							else \
								echo "❌ Plugin/version lock guard failed with exit code $$EXIT_CODE"; \
							fi; \
						else \
							echo "❌ Module graph guard failed with exit code $$EXIT_CODE"; \
						fi; \
					else \
						echo "❌ Deterministic build guard failed with exit code $$EXIT_CODE"; \
					fi; \
				else \
					echo "❌ Backup rules audit failed with exit code $$EXIT_CODE"; \
				fi; \
			else \
				echo "❌ Logging guard failed with exit code $$EXIT_CODE"; \
			fi; \
		else \
			echo "❌ i18n key check failed with exit code $$EXIT_CODE"; \
		fi; \
	else \
		echo "❌ Kotlin linting failed with exit code $$EXIT_CODE"; \
		echo "Run 'make fmt-kotlin' to auto-fix issues"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: check-i18n-keys
check-i18n-keys: ## Verify base values keys are present in values-ru
	@./scripts/check-i18n-keys.sh

.PHONY: ktlint-strace
ktlint-strace: ## Run ktlint format with stacktrace (debug formatting issues)
	@(cd android && ./gradlew :app:runKtlintFormatOverMainSourceSet --no-daemon --stacktrace); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Kotlin code formatted successfully"; \
	else \
		echo "❌ Kotlin formatting failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: detekt
detekt: ## Run detekt static analysis
	@echo "Running detekt static analysis..."
	@(cd android && ./gradlew :app:detekt --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Detekt analysis passed"; \
	else \
		echo "❌ Detekt analysis failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: hilt-graph-check
hilt-graph-check: ## Validate Hilt dependency graph for beta/prod debug variants
	@echo "Validating Hilt dependency graph..."
	@(cd android && ./gradlew :app:hiltAggregateDepsBetaDebug :app:hiltAggregateDepsProdDebug --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Hilt graph validation passed"; \
	else \
		echo "❌ Hilt graph validation failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: test
test: ## Run unit tests (beta + prod)
	@echo "Running unit tests..."
	@(cd android && ./gradlew :app:testBetaDebugUnitTest :app:testProdDebugUnitTest --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Unit tests passed"; \
	else \
		echo "❌ Unit tests failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: test-audio
test-audio: ## Run audio-focused unit tests (beta + prod)
	@echo "Running audio-focused unit tests..."
	@(cd android && ./gradlew :app:testBetaDebugUnitTest :app:testProdDebugUnitTest \
		--tests "com.jabook.app.jabook.audio.*" \
		--tests "com.jabook.app.jabook.audio.*.*" \
		--tests "com.jabook.app.jabook.download.*" \
		--no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Audio-focused unit tests passed"; \
	else \
		echo "❌ Audio-focused unit tests failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: test-storage
test-storage: ## Run storage/data-layer unit tests (beta + prod)
	@echo "Running storage-focused unit tests..."
	@(cd android && ./gradlew :app:testBetaDebugUnitTest :app:testProdDebugUnitTest \
		--tests "com.jabook.app.jabook.compose.data.local.*" \
		--tests "com.jabook.app.jabook.compose.data.local.*.*" \
		--tests "com.jabook.app.jabook.compose.data.repository.*" \
		--tests "com.jabook.app.jabook.migration.*" \
		--no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Storage-focused unit tests passed"; \
	else \
		echo "❌ Storage-focused unit tests failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: test-player
test-player: ## Run player feature unit tests (beta + prod)
	@echo "Running player-focused unit tests..."
	@(cd android && ./gradlew :app:testBetaDebugUnitTest :app:testProdDebugUnitTest \
		--tests "com.jabook.app.jabook.compose.feature.player.*" \
		--tests "com.jabook.app.jabook.compose.feature.player.*.*" \
		--tests "com.jabook.app.jabook.audio.*" \
		--no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Player-focused unit tests passed"; \
	else \
		echo "❌ Player-focused unit tests failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: test-coverage
test-coverage: ## Generate test coverage report (JaCoCo)
	@(cd android && ./gradlew :app:testBetaDebugUnitTest :app:testProdDebugUnitTest :app:jacocoTestReport --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Test coverage report generated at android/app/build/reports/jacoco/jacocoTestReport/html/index.html"; \
	else \
		echo "❌ Test coverage report generation failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: test-coverage-verify
test-coverage-verify: test-coverage ## Verify test coverage ≥ 85%
	@echo "Verifying test coverage meets minimum threshold of 85%..."
	@(cd android && ./gradlew :app:jacocoCoverageVerification --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Test coverage meets minimum threshold of 85%"; \
	else \
		echo "❌ Test coverage is below minimum threshold of 85%"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: warnings
warnings: ## Count compiler warnings
	@cd android && ./gradlew :app:compileDevDebugKotlin --no-daemon 2>&1 | grep "^w:" | wc -l | xargs -I{} echo "Found {} warnings"
