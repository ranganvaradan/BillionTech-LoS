# Provider inventory & readiness (no secrets)

Classification: GREEN = ready for production use · AMBER = integration exists, credential/cert pending · RED = missing/unproven/unsafe.

Do **not** call production endpoints from this gate.

| Provider | Purpose | Env / base URL source | Auth | Fail mode | Readiness | Notes |
|----------|---------|------------------------|------|-----------|-----------|-------|
| Bureau (CRIF/etc.) | Credit score | `application-*.yml` / env | API key/token | Fail closed → typed missing/unavailable | AMBER | Staging uses manual/demo paths; prod creds pending |
| Karza / SurePass KYC | KYC | env | API key | Fail closed / manual review | AMBER | |
| Bank statement / BSA | Income | env | API key | Fail closed | AMBER | |
| Account Aggregator (Setu etc.) | AA | env | OAuth/client | Fail closed | AMBER | |
| GST | GST facts | env | API key | Fail closed | AMBER | |
| ITR | ITR facts | env | API key | Fail closed | AMBER | |
| PLP | Invoice discounting | `plp.*` env | IAM login token | Fail closed | NOT_REQUIRED for COMPANY/TERM_LOAN | Dedicated golden before PLP go-live |
| LMS / Encore | Open account | `lms.*` / Encore URL env | API credentials | Fail closed on mapping miss | AMBER | Mapping required; no IPPOPAYM01 bypass |
| SMTP | Email | env | user/pass | Soft for notify | AMBER | |
| SMS | OTP/notify | env | API key | Soft for notify | AMBER | |
| PayU | Payments | env | merchant keys | N/A until disbursement | RED for launch if disbursement excluded | Disbursement out of launch scope |
| MinIO / object storage | Documents | env | access/secret | Fail closed upload | AMBER | |
| Internal CI token | `/api/v1/internal/**` | `CREDIT_INTELLIGENCE_INTERNAL_TOKEN` | Shared secret header | Fail closed | GREEN when set in prod | Startup fails if blank in prod |

## Failure semantics (required providers)

- Timeout / 5xx / auth failure / malformed / missing required field → typed provider/data failure
- No fake approval substitution
- Manual review / hard reject per product rules when data missing
