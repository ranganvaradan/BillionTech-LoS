# 12 — Security and Governance

## Controls

| Control | Design |
|---|---|
| Consent / purpose | Bound on Source ingest; purpose tags on AI requests |
| Minimization | Prefer refs over raw PII to AI-LOS |
| Access control | RBAC + ABAC on application; artifact vault roles |
| Segregation | Policy author ≠ publisher; underwriter ≠ sanctioner |
| Maker-checker | Policy publish; high-severity overrides; sanction |
| Tenant isolation | tenant_id on all SoR tables |
| Encryption | TLS in transit; envelope encryption at rest for artifacts |
| Secrets | Short-lived m2m JWT **with exp** (retire no-exp AI JWT) |
| Audit | Immutable audit_event + evaluation linkage |
| PII masking | Support views; no INFO payload dumps |
| Retention / deletion | Purpose-linked; cascade AI copies |
| Model governance | model_version + prompt_version on ai_output |
| Policy governance | Lifecycle + package freeze |
| Human oversight | AI and REFER queues |

## Sensitive classes

| Data | Rule |
|---|---|
| Raw bureau | Vault; never to browser; never full dump to AI without need |
| AA data | Consent window; no secondary use |
| PAN / Aadhaar | Tokenize Aadhaar; mask PAN in logs |
| AI prompts | Store hash + template version; minimize PII in prompt |
| Production support | Break-glass with reason + time-box |

## Regulatory posture

Evidence-linked decisions + reproducible snapshots support auditability; DATA_INSUFFICIENT prevents fabricated compliance.
