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
- `search_index.py` — high-performance indexing (`parallel`/`crawl`), incremental + force rebuild, topic/comments search indexes.

## Usage

```bash
python3 -m pip install -r rutracker_parser/requirements.txt
```

```bash
python3 -m pip install -r rutracker_parser/requirements-dev.txt
```

```bash
cd rutracker_parser
make check
```

`make format` — black + auto-fix imports/lint.
`make lint` — ruff checks.
`make check` — formatter check + lint.

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
  --indexer parallel \
  --workers 2 \
  --max-pages-per-forum 0 \
  --incremental \
  --incremental-pages-window 8 \
  --hard-max-pages-per-forum 450 \
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

```bash
python3 -m rutracker_parser search-find \
  --scope comments \
  --query "спасибо" \
  --output-dir rutracker_parser/output \
  --limit 20
```

## High-Performance Indexing

`search-index` supports two engines:

- `--indexer parallel` (default) — fast and safe tracker-page indexing with thread pool.
- `--indexer crawl` — legacy crawl engine (compatible fallback mode).

Recommended safe defaults:

- `--workers 2` for stable auth sessions.
- `--request-interval 2.2` and `--jitter 0.4` (or slower).
- `--max-retries 1..2`, `--cooldown >= 60`.
- Increase `--workers` only if anti-bot signals are clean.

Coverage controls:

- `--max-pages-per-forum N` — limit pages per forum.
- `--max-pages-per-forum 0` — full forum coverage (bounded by hard cap).
- `--hard-max-pages-per-forum` — absolute safety cap per forum.

Incremental/full rebuild:

- `--incremental` — merge with latest `index_full.json` from `--output-dir` (or `--base-index`).
- `--incremental-pages-window` — when `--max-pages-per-forum 0`, fetch only fresh window in incremental mode.
- `--force` — disable incremental merge and rebuild index from scratch.

Example: force full rebuild

```bash
python3 -m rutracker_parser search-index \
  --mode auth \
  --mirror https://rutracker.org \
  --query "аудиокнига" \
  --indexer parallel \
  --workers 2 \
  --max-pages-per-forum 0 \
  --hard-max-pages-per-forum 450 \
  --force \
  --output-dir rutracker_parser/output
```

Search scopes:

- `search-find --scope topics` — topic index (`index_compact.json`).
- `search-find --scope comments` — comments index (`comments_index.jsonl`).
- `search-find --scope all` — merged topics + comments response.

## Output

Each run creates a directory:

- `rutracker_parser/output/run_<timestamp>/pages/pages.jsonl`
- `rutracker_parser/output/run_<timestamp>/entities/{forums,topics,users,torrents,categories,posts,topic_meta,profiles}.jsonl`
- `rutracker_parser/output/run_<timestamp>/graph/edges.jsonl`
- `rutracker_parser/output/run_<timestamp>/raw/*.html.gz`
- `rutracker_parser/output/run_<timestamp>/meta/{summary.json,search_plan.json,mode_capabilities.json,errors.jsonl}`
- `rutracker_parser/output/run_<timestamp>/search/{index_full.json,index_compact.json,index_top50.json,index_stats.json,index_delta.json}`
- `rutracker_parser/output/run_<timestamp>/search/{comments_index.jsonl,comments_top50.json,comments_index_stats.json}`

Notes:

- `comments_index.jsonl` is populated only when run data contains parsed posts (`entities/posts.jsonl`).
- If no topic pages were parsed, comments scope returns an empty index with a hint.
