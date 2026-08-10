# LOS ↔ PLP staging integration (safe config)

## Enablement

Source default in `application-staging.yml`:

```yaml
los.plp.enabled: ${LOS_PLP_ENABLED:false}
```

Staging turns integration on via environment only:

- `LOS_PLP_ENABLED=true`
- `PLP_BASE_URL=http://plp-gateway:8080`
- `PLP_LENDER_ID=<uuid>`
- `PLP_INTEGRATION_EMAIL=<active PLP IAM user>`
- `PLP_INTEGRATION_PASSWORD=<secret>`

Do not commit secrets. See PLP `infra/deploy/staging/env.example`.

## Networking

LOS `los-core` container must be attached to:

- `billiontech-net` (LOS infra)
- `plp_nw` (reach `plp-gateway`)

## Master sync behaviour

Create/update of Anchor / Program / Sub-program publishes
`PlpMasterSyncRequestedEvent`, handled **after commit** by
`PlpMasterSyncAfterCommitListener`. Manual retry APIs remain idempotent.

## Safety invariants

- `credit-intelligence.cutover.allow-canonical-authority: false`
- Decision Policy shadow only
- No historical dump migration as part of enablement

## Encore product codes (finance blocker)

| Source | Typical value | Role |
|--------|---------------|------|
| `workflow_configs.lms_product_code` | `IPPOPAYM01` | LOS workflow / LMS default |
| `EncoreTemporaryOverrides` (LOS+PLP) | `IPPOPAYM01` | Client fallback |
| V45 `workflow_lms_product_mapping` seed | `EE_10106` | Heuristic mapping for invoice discounting |
| PLP `programs.encore_product_code` | optional | Used when `lms_entry_in=YES` |

Until product/ops confirms which Encore sandbox product is live for invoice
discounting, treat finance/disbursement E2E as blocked
(`ENCORE_MAPPING_UNRESOLVED`). Master sync does not require Encore calls.
