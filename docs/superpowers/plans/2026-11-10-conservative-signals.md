# Conservative Signal Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Separate confirmed structures from observations, prevent candidate points from receiving actionable conclusions, show truthful pivot dates and unvalidated momentum/volume evidence.

**Architecture:** Domain services retain raw signals and produce immutable latest-signal assessments. Ranking queries classify and sort through domain rules; commands persist versioned snapshots. API/Gateway boundaries map evidence, and React renders the classification without recomputing eligibility.

**Tech Stack:** Python/FastAPI, SQLite, React/Bun/ECharts.

**Spec:** `docs/superpowers/specs/2026-11-10-conservative-signal-quality-design.md` (first stage approved 2026-11-10).

## Global Constraints

- Work directly on main; do not commit or push without a new explicit request.
- Preserve pre-existing edits in market summary, market.js, MarketChart.jsx and their tests.
- Queries remain read-only; domain remains framework/IO independent.
- Do not change raw MACD/Chan signal definitions, enable unvalidated hard filters or invent detection/confirmation dates.
- Preserve published snapshots and checkpoints. Missing legacy evidence is unknown, not a pass.
- Reuse existing visual tokens and verify 390px/1440px layouts.

## Review Focus

- Legacy JSON and API DTOs with no quality fields must load safely and identify missing evidence.
- A candidate or opposite-side latest point must never fall back to an older confirmed opportunity.
- Invalidation belongs to the signal's own structure; equality does not count as a close through the boundary.
- Unknown detection/confirmation times must not be replaced with pivot or build timestamps.
- Same-date version upgrades and interrupted builds must preserve leases, old snapshots and resumability.

### Task 1: Signal references and evidence

**Files:** `backend/domain/models.py`, `backend/domain/services/chan_signals.py`, new `backend/domain/services/signal_quality.py`, `backend/application/queries/get_market_snapshot.py`, `backend/app/container.py`, `tests/test_signal_quality.py`, `tests/test_chan_signals.py`.

**Interfaces:** Add optional source-zone/invalidation references to `BuySellPoint`; immutable `SignalQuality` with confirmation state, current invalidation, MACD/volume measurements and unknown timing/history. `SignalQualityEvaluator.evaluate(candles, analysis, macd, signals)` assesses the latest point only; return None when absent. Inject evaluator into snapshot query from composition root (optional dependency for existing callers).

- [x] Write and run failing cases for candidate/confirmed distinction, three point-specific boundaries, equal boundary, insufficient/zero volume, frozen pre-event baseline and latest-point-only assessment.
- [x] Enrich points without changing their occurrence; implement deterministic assessment and integrate snapshot output.
- [x] Verify domain tests and existing raw-signal tests.

### Task 2: Ranking classification and persistence

**Files:** `backend/domain/rankings.py`, `backend/domain/models.py`, `backend/application/queries/get_rankings.py`, `backend/application/commands/build_daily_rankings.py`, `backend/infrastructure/persistence/sqlite_ranking_store.py`, `backend/app/ranking_worker.py`, API schemas, ranking/store/worker/API tests.

**Interfaces:** `RankingEntry.signal_quality` and `signal_group` (confirmed/observation); `RankingBoard.analysis_version`. Pure classification requires matching latest direction, confirmed structure and known unbroken reference. Add snapshot/checkpoint version identity to the writer/store; old records decode with version 1, new output uses version 2.

- [x] Write/run failing ranking tests: confirmed precedence, no older fallback, invalidation, unknown legacy evidence and limit ordering.
- [x] Write/run persistence tests: legacy read, quality roundtrip, one same-day version upgrade, checkpoint version isolation, lease exclusivity and read-only GET.
- [x] Implement classification, DTOs, versioned worker rebuild and compatible storage without deleting old published data.
- [x] Run backend suite.

### Task 3: Frontend semantics and grouping

**Files:** `frontend/src/domain/signals.js`, `frontend/src/domain/rankings.js`, tests, `frontend/src/infrastructure/api/marketGateway.js`, `RankingPage.jsx`, `SignalPanel.jsx`, `MarketChart.jsx`, `App.jsx`, glossary and styles.

**Interfaces:** Backend ranking `signal_group` is authoritative. Legacy absent group remains observation. `evaluateActionConclusion` checks confirmation and quality before opportunity language. Display `time` as structural pivot date; detection/confirmation times remain unknown unless explicitly supplied.

- [x] Write/run failing domain tests for candidate conclusions on both sides, no candidate-to-old-point fallback, safe legacy grouping and invalidated points.
- [x] Implement confirmed and observation sections with independent counts/search/pagination. Empty confirmed section never fills with candidates.
- [x] Render quality numbers as unvalidated evidence, explicit unknown time/history and original structural risk warnings.
- [x] Correct point dates/tooltips and terminology throughout market and rankings; preserve unrelated chart-tooltip edits.
- [x] Run frontend tests/build.

### Task 4: Integration and documentation

**Files:** dedicated browser regression script, AGENTS.md, FEATURES.md, CHANGELOG.md, this plan/spec.

- [x] Verify real HTTP search, stock/ETF four timeframes/signals, confluence, rankings, samples, guest/code authentication, CORS and error contracts in an isolated runtime.
- [x] Verify actual browser grouping, candidate labels, legacy snapshots, empty groups, searches and unknown dates at 390px and 1440px; capture screenshots.
- [x] Run all mandated checks; request one independent final code review and resolve material findings.
- [x] Update memory/spec status and report behavior, validation, limitations and an evidence-grounded execution template. Do not commit.

## Execution ledger

- Ruling: user explicitly approved implementation; execute inline without another execution-method approval, following higher-priority autonomy instructions.
- Ruling: grouping/evidence are stage one. Historical lifecycle, calibrated volume hard filters and performance claims remain out of scope.
- Pre-flight: task 2 serializes task 1's immutable quality; task 3 consumes task 2's classification/DTOs. Preserve optional fields for legacy reads and inject concrete dependencies only in composition root.
- Task 1: complete — signal references and immutable evidence tested; raw point occurrence unchanged.
- Task 2: complete — classification, compatible version migration, lease/resume and read-only GET tests pass; a real four-security sample rebuilt the same close day into version 2 with two confirmed structures.
- Task 3: complete — candidate/opposite/legacy behavior covered by domain and browser tests; market and ranking dates identify structural pivots, with unknown first-observed/confirmed times preserved.
- Task 4: complete — 113 backend and 31 frontend tests, production build, real HTTP market regression (registration exchange uses a local fixture), 390/1440 grouping and 320–1440 chart-layout checks pass. Independent review findings were reproduced with failing tests and fixed.
- Ruling: minute adapters retain closing timestamps; completion uses that timestamp, not timestamp plus another period. Intrabar touching is a separate fact, not close-based invalidation.
- Ruling: reload the user's current development service to make approved changes available, preserving its environment and backing up SQLite first. The normal background worker performs the full version-2 rebuild; do not delete snapshots or force GET writes. No new commit or push performed.

## Review corrections approved by user

- [x] Reproduce and correct distance sorting to use the signal's own reference, with no guidance fallback.
- [x] Add `bars_since_pivot` from full completed candles, verify index/time identity, exclude unfinished bars and preserve unknown legacy ages. Display age separately from confirmation time without a speculative expiry threshold.
- [x] Centralize current version 3 and legacy version 1. Require runtime version arguments and explicitly decode missing historical versions; preserve recorded version 2.
- [x] Verify 121 backend / 31 frontend tests and 390/1440 browser behavior; independent targeted review found no remaining important issues. Real HTTP and build verification recorded with completion report.
- Ruling: version 3 backfills pivot age in the normal background command, retaining the version-2 snapshot until publication. Sorting can be corrected immediately by the read-only query without writing the snapshot.

## Historical invalidation correction

- Reproduced the user's complete sample: version-3 11-09 snapshot, 175 confirmed structures, 20 historical reference crossings and no unknown pivot windows. The earlier snapshot-only evaluation missed an approved requirement; pivot age does not resolve it.
- Added failing-then-passing regression cases for buy/sell crossing and recovery, equality, intrabar-only touching, an unfinished last bar after a known earlier crossing, and missing pivot windows. Scan from the matched pivot through available completed bars, retain the first real price crossing and the checked-through time.
- Ranking eligibility requires `checked_from_pivot` and no known crossing record. Legacy unchecked records remain observations. Frontend shows checked/invalidated/unknown interval facts without inventing detection or confirmation times.
- Version 4 requests a normal background rebuild, preserving the old published snapshot. No new commit or push.
