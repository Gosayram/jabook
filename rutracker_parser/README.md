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
- `search_index.py` — search harvesting for `tracker.php` (audiobooks), JSON index build, fast local lookup.

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

```bash
python3 -m rutracker_parser search-index \
  --mode auth \
  --mirror https://rutracker.org \
  --query "аудиокнига" \
  --max-pages-per-forum 2 \
  --output-dir rutracker_parser/output
```

```bash
python3 -m rutracker_parser search-find \
  --index rutracker_parser/output/<run_dir>/search/index_compact.json \
  --query "рейнольдс префект" \
  --limit 20
```

```bash
python3 -m rutracker_parser search-find \
  --query "аудиокнига ленин" \
  --output-dir rutracker_parser/output \
  --auto-index-if-missing \
  --auto-refresh-on-empty \
  --mode auth \
  --mirror https://rutracker.org
```

## Output

Each run creates a directory:

- `rutracker_parser/output/run_<timestamp>/pages/pages.jsonl`
- `rutracker_parser/output/run_<timestamp>/entities/{forums,topics,users,torrents,categories,posts,topic_meta,profiles}.jsonl`
- `rutracker_parser/output/run_<timestamp>/graph/edges.jsonl`
- `rutracker_parser/output/run_<timestamp>/raw/*.html.gz`
- `rutracker_parser/output/run_<timestamp>/meta/{summary.json,mode_capabilities.json,errors.jsonl}`
- `rutracker_parser/output/run_<timestamp>/search/{index_full.json,index_compact.json,index_top50.json,index_stats.json}`
