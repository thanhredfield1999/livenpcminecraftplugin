# Workstream: Paper Smoke

## Owner

Test/evidence review agent.

## Scope

Controlled Paper 1.21.11 verification only.

## Required matrix

- Flat route.
- 1-block rise.
- Multi-step staircase.
- Closed/open ordinary door.
- Closed fence gate.
- FIFO with 3 NPC.
- Cancel/timeout/retry.
- Chunk boundary.
- Collision/narrow corridor.
- Restart persistence only when relevant.

## Required evidence

- Artifact SHA-256.
- Backup path.
- Process ID.
- Startup timestamp.
- NPC UUID/name.
- Input intent.
- Position/target.
- Markers: `APPROACH`, `OPEN`, `CROSS`, `EXIT`, `RELEASED`.
- Failure marker and RCA.

## Status

`PENDING`. No production CORE PASS.

## Rules

Unit tests không chứng minh Paper/Citizens/runtime. Không restart/deploy production nếu chưa user approval.

## Entry log

### 2026-08-18 — Baseline

- Existing smoke: partial staircase success; gate/storage not verified.
- Result: `NOT VERIFIED`.

