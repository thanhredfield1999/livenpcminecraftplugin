# Workstream: Citizens/Paper Research

## Owner

Research agent.

## Scope

GitHub issues/PR, Citizens API/source/Javadocs, Paper 1.21.11 docs/Javadocs. Chỉ kết luận điều source chứng minh.

## Key conclusions

- Player NPC dùng Citizens Navigator, không Paper Mob Pathfinder.
- `setTarget(Location)` một lần mỗi leg; target mới thay navigation cũ.
- `setTarget(Iterable<Vector>)` không pathfind.
- Citizens `range`, `stationaryTicks`, `fallDistance`, `distanceMargin`, `pathDistanceMargin` khác nhau.
- Fence gate cần policy LivingNPC riêng.
- Chunk/collision/stuck cần telemetry riêng.

## Full report

`..\..\research-paper-citizens-pathfinding-vi.md`

## Sources

- https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/Navigator.html
- https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/NavigatorParameters.html
- https://wiki.citizensnpcs.co/API
- https://docs.papermc.io/paper/dev/entity-pathfinder
- https://github.com/CitizensDev/Citizens2/issues/1173
- https://github.com/CitizensDev/Citizens2/issues/979
- https://github.com/CitizensDev/Citizens2/issues/2353
- https://github.com/PaperMC/Paper/issues/12043
- https://github.com/PaperMC/Paper/issues/12335

## Version caveat

Public Citizens Javadocs là `2.0.43-SNAPSHOT`; production target `2.0.42-SNAPSHOT build 4173`. Trước compile API mới phải đối chiếu artifact target.

## Status

`ACTIVE REFERENCE`.

## Entry log

### 2026-08-18 — Initial research

- Result: `PASS` cho research scope.
- Runtime conclusions: `NOT VERIFIED` cho tới controlled Paper smoke.

