# rutracker_parser

## Contents

- `__main__.py` / `cli.py` — CLI (`crawl`, `capabilities`).
- `settings.py` — all runtime parameters, limits, timeouts, `guest/auth/auto` modes, env config.
- `client.py` — HTTP session, login via `RUTRACKER_LOGIN`/`RUTRACKER_PASSWORD`, retries/backoff/cooldown, mirror fallback.
- `throttle.py` — rate limiter with jitter.
- `extractors.py` — extraction of forum structure/topics/users/torrent metadata/forms and link graph.
- `crawl_engine.py` — page graph traversal, scenarios, result saving.
- `storage.py` — JSONL storage + raw HTML in `gzip`.
- `mode_matrix.py` + `MODE_CAPABILITIES.json` — what works in `guest`, `auth` and both modes.
- `scenarios.py` — presets `full`, `structure`, `topics`, `auth_surface`.

## Usage

```bash
python3 -m pip install -r rutracker_parser/requirements.txt
```

```bash
python3 -m rutracker_parser crawl \
  --mode auto \
  --mirror https://rutracker.net \
  --fallback-mirror https://rutracker.me \
  --scenario audiobooks \
  --output-dir rutracker_parser/output
```

`--scenario` presets: `full`, `structure`, `topics`, `auth_surface`, `audiobooks`.

```bash
python3 -m rutracker_parser capabilities --mode both
```

## Output

Each run creates a directory:

- `rutracker_parser/output/run_<timestamp>/pages/pages.jsonl`
- `rutracker_parser/output/run_<timestamp>/entities/{forums,topics,users,torrents}.jsonl`
- `rutracker_parser/output/run_<timestamp>/graph/edges.jsonl`
- `rutracker_parser/output/run_<timestamp>/raw/*.html.gz`
- `rutracker_parser/output/run_<timestamp>/meta/{summary.json,mode_capabilities.json,errors.jsonl}`
