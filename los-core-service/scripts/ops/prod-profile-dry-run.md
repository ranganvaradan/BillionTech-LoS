# Prod profile dry-run (no real provider calls)

```bash
export SPRING_PROFILES_ACTIVE=prod
export LOS_JWT_HMAC_SECRET='dry-run-hmac-secret-32chars-minimum!!'
export CREDIT_INTELLIGENCE_INTERNAL_TOKEN='dry-run-internal-token-not-for-prod'
# Provide dummy DB/Redis/Rabbit URLs pointing at local/non-prod only
java -jar los-core-service.jar
```

Expect startup success only when:

- demo / staging-demo / validation / gap defaults / demo scoring = false
- block-non-authoritative-defaults = true
- allow-header-impersonation = false
- jwt.required = true
- internal-token-required = true
- allowCanonicalAuthority = false

Missing JWT secret or blank internal token → fail startup.

Route smoke (against dry-run):

| Actor | Expect |
|-------|--------|
| anonymous /api/v1/applications | 401 |
| X-User-* only (no Bearer) | 401 HEADER_IMPERSONATION_DISABLED |
| Bearer CREDIT_MANAGER | 200/403 per role on admin |
| wrong internal token | 401/403 on /api/v1/internal/** |
