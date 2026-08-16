# Architecture regression — Wave 0 golden baseline

Durable CURRENT-behaviour baselines for platform convergence Waves 1–11.

## Rules

- Records **current reality**, including defects (`KNOWN_GAP`).
- Does **not** mutate GACAT, Vikasam policy `4543e643-c3a0-4a57-a92c-370dff8b2fa9`, or production DB.
- Does **not** intentionally change business behaviour.
- Uses deterministic fixtures + fixed POLICY_TEST `asOf=2026-08-01`.

## Re-run

```bash
cd los-core-service
mvn -q -Dtest=Wave0ArchitectureRegressionTest,Wave0CapabilitySnapshotTest,Wave0VikasamBaselineTest test
```

Regenerate committed capability snapshot (maintainers only):

```bash
mvn -q -Dtest=Wave0CapabilitySnapshotTest#generateOrAssertSnapshot -Dwave0.generate=true test
```

## Layout

| Path | Purpose |
|------|---------|
| `WAVE0_FREEZE.json` | SHA / deploy freeze |
| `datasets/` | Golden source-shaped fixtures |
| `baselines/` | Captured capability / execution outputs |
| `clocks/` | asOf / wall-clock inventory |
| `legacy/` | Parallel authority inventory |
| `classifications/` | MUST_PRESERVE / KNOWN_GAP / EXPECTED_TO_CHANGE |

## Assertion classes

See `classifications/assertion-classes.json`.
