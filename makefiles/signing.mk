# ============================================================================
# Signing Commands — key generation and Gradle signing configuration
# ============================================================================

## Signing

.PHONY: sign-android
sign-android: ## Generate signing keys and setup Android signing
	@if [ -f "$(SIGNING_SCRIPT)" ]; then \
		$(SIGNING_SCRIPT); \
		echo "Android signing configured successfully"; \
		echo "Patching Gradle configuration..."; \
		scripts/patch-gradle-signing.sh; \
		echo "Gradle configuration patched successfully"; \
	else \
		echo "Error: Signing script not found at $(SIGNING_SCRIPT)"; \
		echo "Please create .key-generate.conf from .key-generate.example.conf"; \
		exit 1; \
	fi

.PHONY: use-existing-android-cert
use-existing-android-cert: ## Use existing Android certificate for signing
	@if [ -f "$(SIGNING_SCRIPT)" ]; then \
		if [ -f ".signing/release.keystore" ]; then \
			echo "Existing certificate found, updating configuration..."; \
			scripts/signing.sh; \
			echo "Android signing configuration updated with existing certificate"; \
		else \
			echo "Error: No existing certificate found in .signing/release.keystore"; \
			echo "Run 'make sign-android' first to generate a certificate"; \
			exit 1; \
		fi \
	else \
		echo "Error: Signing script not found at $(SIGNING_SCRIPT)"; \
		exit 1; \
	fi

.PHONY: patch-gradle-signing
patch-gradle-signing: ## Patch Gradle signing configuration
	@if [ -f "scripts/patch-gradle-signing.sh" ]; then \
		scripts/patch-gradle-signing.sh; \
		echo "✅ Gradle configuration patched successfully"; \
	else \
		echo "Error: Patch script not found at scripts/patch-gradle-signing.sh"; \
		exit 1; \
	fi