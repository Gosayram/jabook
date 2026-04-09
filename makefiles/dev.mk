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
fmt-kotlin: ## Format Kotlin code (ktlint + detekt auto-correct)
	@echo "Formatting Kotlin code with ktlint + detekt..."
	@(cd android && ./gradlew :app:ktlintFormat :app:detekt --auto-correct --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Kotlin code formatted successfully (ktlint + detekt)"; \
	else \
		echo "❌ Kotlin formatting failed with exit code $$EXIT_CODE"; \
	fi; \
	exit $$EXIT_CODE

.PHONY: lint-kotlin
lint-kotlin: ## Lint Kotlin code (ktlint + detekt check)
	@echo "Linting Kotlin code with ktlint + detekt..."
	@(cd android && ./gradlew :app:ktlintCheck :app:detekt --no-daemon); \
	EXIT_CODE=$$?; \
	if [ $$EXIT_CODE -eq 0 ]; then \
		echo "✅ Kotlin linting passed (ktlint + detekt)"; \
	else \
		echo "❌ Kotlin linting failed with exit code $$EXIT_CODE"; \
		echo "Run 'make fmt-kotlin' to auto-fix issues"; \
	fi; \
	exit $$EXIT_CODE

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