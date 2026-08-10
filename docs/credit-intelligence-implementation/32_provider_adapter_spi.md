# 34 — Provider SPI and Adapters

## SPI

`ProviderAdapter`: `supports` → `extract` → facts + optional non-authoritative observations.

Flags (default **false**): `providerSpi.enabled`, `providerObservations.enabled`.

Adapters are **not** wired as production underwriting authority in C5.1.

## Ten adapters

| Adapter | Source |
|---------|--------|
| EquifaxConsumerBureauAdapter | Consumer bureau XML |
| KarzaGstAdapter | Karza GST |
| KarzaItrAdapter | Karza ITR |
| SetuAaAdapter | Account Aggregator FI |
| SurePassBsaAdapter | Bank statement |
| SurePassGstAdapter | GST |
| SurePassItrAdapter | ITR (+ 26AS facts) |
| SurePassCibilAdapter | Consumer bureau |
| SurePassCommercialBureauAdapter | Commercial bureau |
| SurePassTisAdapter | TIS |

## Fixture origins (honest)

| Path | Origin |
|------|--------|
| `provider-fixtures/surepass/**` | **USER_SUPPLIED_SAMPLE** (sanitized) — see each `ORIGIN.md` |
| `provider-fixtures/equifax/**` | **SYNTHETIC** from unit-test XML |
| `provider-fixtures/karza/**` | **SYNTHETIC** from existing Karza tests |
| `provider-fixtures/setu/**` | **SYNTHETIC** AA shape |

These are **not** live production validation.

## Tests

`*AdapterFixtureTest` under `com.los.core.creditintelligence.provider`.
