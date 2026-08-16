# Diagnostics Repair ABI Rollback

Date: 2026-08-14

## Symptom

After deploying the isolated diagnostics repair candidate with SHA-256
`C627E021840EF32C5F64FD058137F91117F3575DF6E0232B199A5007C1CE6FE6`, Paper started and
LivingNPC enabled, but the central tick task repeatedly threw:

```text
java.lang.NoSuchMethodError: 'int vn.heomc.livingnpc.MiningRestorationStore.tick(long, int)'
```

The first recorded exception was immediately after startup. Repetition on the tick path made the
candidate unsafe to leave running.

## Root Cause

The candidate replaced `LivingNpcPlugin.class` from the current clean build but retained
`MiningRestorationStore.class` from the older live JAR. The replacement plugin class invokes
`MiningRestorationStore.tick(long, int)`, while the retained live class does not expose that method
descriptor.

The candidate audit verified archive entry deltas, internal class-reference existence, replacement
hashes, and retained nested-class ABI. It did not verify method and field descriptors used by the
replacement classes against every retained referenced class. Class existence therefore passed even
though the call-site ABI was incompatible.

## Mitigation

Paper was stopped cleanly through RCON. The original live JAR was restored from:

`F:\minecraftserver\villagedefense2026\backups\livingnpc-diagnostics-deploy-20260814-113150`

The restored JAR SHA-256 is
`8F603D1F1BF089CA1F00AEC4129464DD1BAEB0D1D2F0D7687F459B921CE897DB`.
Paper was restarted with the restored JAR.

## Regression Safeguard Required

Before another isolated class-entry repair is approved, its audit must validate referenced method
and field descriptors against the final candidate archive, not only referenced class names. A
disposable Paper startup test should also run the candidate long enough to exercise the central tick
path before production deployment.

No automated regression test for this packaging defect exists yet.

## Runtime Verification

- Rollback Paper startup reached `Done (30.164s)`.
- Game port `11619` and RCON port `36102` are listening.
- RCON responds.
- LivingNPC enabled normally.
- The first post-start health interval reported `NPC_HEALTH total=10 ok=0 waiting=10 errors=0`.
- The rollback startup log contains no `NoSuchMethodError` or other LivingNPC linkage error.

The diagnostics candidate remains preserved for forensic evidence but is invalid and must not be
deployed again.
