PYTHON ?= .venv/bin/python

.PHONY: install-dev format format-check lint check

install-dev:
	$(PYTHON) -m pip install -r requirements-dev.txt

format:
	$(PYTHON) -m black .
	$(PYTHON) -m ruff check --fix .

format-check:
	$(PYTHON) -m black --check .

lint:
	$(PYTHON) -m ruff check .

check: format-check lint
