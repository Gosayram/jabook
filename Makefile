# ============================================================================
# Jabook Kotlin/Android Application — Master Makefile
# ============================================================================
# Modular Makefile: targets are split into makefiles/*.mk
# Help is auto-generated from ## comments — run `make help`
# ============================================================================

# ── Global Variables ────────────────────────────────────────────────────────

PROJECT_NAME    := jabook
FLAVORS          = dev stage beta prod
SIGNING_SCRIPT  := scripts/signing.sh
VERSION         := $(shell cat .release-version 2>/dev/null | cut -d+ -f1 || echo "0.0.1")
FULL_VERSION    := $(shell cat .release-version 2>/dev/null || echo "0.0.1+1")
APK_DEST_DIR    := $(HOME)/Downloads/Jabook

# Android SDK command-line tools
CMDLINE_TOOLS   := $(ANDROID_HOME)/cmdline-tools/latest/bin
APKANALYZER     := $(CMDLINE_TOOLS)/apkanalyzer
RETRACE         := $(CMDLINE_TOOLS)/retrace
R8              := $(CMDLINE_TOOLS)/r8

# Default APK path for analysis (prefers beta arm64)
DEFAULT_APK     ?= $(shell ls build/app/outputs/apk/beta/release/app-beta-arm64-v8a-release.apk 2>/dev/null || \
	ls build/app/outputs/apk/beta/release/app-beta-release.apk 2>/dev/null || \
	ls build/app/outputs/apk/prod/release/app-prod-arm64-v8a-release.apk 2>/dev/null || \
	ls build/app/outputs/apk/prod/release/app-prod-release.apk 2>/dev/null || \
	echo "")

# ── ANSI Colors ─────────────────────────────────────────────────────────────

RESET   := \033[0m
BOLD    := \033[1m
DIM     := \033[2m
CYAN    := \033[36m
GREEN   := \033[32m
YELLOW  := \033[33m
MAGENTA := \033[35m
WHITE   := \033[37m

# ── Auto-generated Help ─────────────────────────────────────────────────────
# Parses ## comments from all included makefiles to build the help screen.
# Comment format:
#   ## Category Name
#   target: ## Short description
#   target: ARGS=<val> ## Description with args
# ============================================================================

.PHONY: help
help: ## Show this help screen
	@echo ""
	@echo "$(BOLD)$(CYAN)  ╔══════════════════════════════════════════════════════════════╗$(RESET)"
	@echo "$(BOLD)$(CYAN)  ║  $(WHITE)JaBook — Kotlin/Android Build System$(CYAN)                        ║$(RESET)"
	@echo "$(BOLD)$(CYAN)  ╚══════════════════════════════════════════════════════════════╝$(RESET)"
	@echo ""
	@awk '/^## / { \
		section = substr($$0, 4); \
		if (section != prev_section) { \
			prev_section = section; \
			printf "\n  $(BOLD)$(MAGENTA)%s$(RESET)\n  $(DIM)-----------------------------------------------------------$(RESET)\n", section; \
		} \
		next; \
	} \
	/^[a-zA-Z0-9_][a-zA-Z0-9_.-]*:.*##/ { \
		n = split($$0, parts, "## "); \
		target = $$1; \
		sub(/:$$/, "", target); \
		sub(/:.*/, "", target); \
		desc = ""; \
		for (i = 2; i <= n; i++) { \
			if (desc != "") desc = desc " ## "; \
			desc = desc parts[i]; \
		} \
		printf "  $(GREEN)%-40s$(RESET) %s\n", target, desc; \
	} \
	' $(MAKEFILE_LIST)
	@echo ""
	@echo "$(DIM)  Tip: Run 'make <target>' to execute. Pass args like make apk-compare OLD_APK=a NEW_APK=b$(RESET)"
	@echo ""

# ── Include Modules ─────────────────────────────────────────────────────────

include makefiles/dev.mk
include makefiles/build.mk
include makefiles/signing.mk
include makefiles/release.mk
include makefiles/version.mk
include makefiles/git.mk
include makefiles/device.mk
include makefiles/utils.mk
include makefiles/analysis.mk
include makefiles/workflows.mk