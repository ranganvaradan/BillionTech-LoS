# Backend RBAC matrix (LOS-PRODUCTION-HARDENING-1)

Identity: production = JWT claims → `X-User-*` overwrite. Staging may allow header impersonation.

| Area | Mutations | Required roles (backend) | Mechanism |
|------|-----------|--------------------------|-----------|
| Applications create/update | POST/PUT applications | RM / ADMIN / ADMINISTRATOR (create); CO/CM for UW path | StaffAccessGuard + service checks |
| Documents upload/delete | document APIs | Staff roles; borrower limited to own app docs | Document controllers + ownership |
| KYC | KYC run / PKYC | CREDIT_OFFICER, CREDIT_MANAGER, ADMIN, RISK_MANAGER, … | Service role checks |
| Underwriting | evaluate / decide | CREDIT_OFFICER, CREDIT_MANAGER, ADMIN, RISK_MANAGER | canRunUnderwriting aligned |
| Manual Review | queue actions | CREDIT_MANAGER / RISK / ADMIN | Service gates |
| CAM | maker/checker | CREDIT_OFFICER (maker); CREDIT_MANAGER/ADMIN (checker) | CAM services |
| Sanction | sanction decide | CREDIT_MANAGER / ADMIN / RISK_MANAGER | L2 sanction roles |
| LMS open | via sanction flow only | Same as sanction path | No public bypass |
| Policy Studio | draft/submit/approve | POLICY maker/checker roles | CI governance; **not** live authority |
| Scorecards | create/submit/approve/activate | maker ≠ checker | Scorecard governance |
| Product Configuration | compose/readiness | ADMIN / CREDIT_MANAGER | AdminApiAccessFilter |
| Data & Parameters | admin browse | ADMIN | AdminApiAccessFilter |
| Audit | read | ADMIN / RISK | Admin APIs |
| Security/decryption | decrypt/erasure | ADMINISTRATOR | Admin + audit |
| Admin | `/api/v1/admin/**` | ADMINISTRATOR (role gate) | AdminApiAccessFilter |
| Internal CI | `/api/v1/internal/**` | Internal token | CreditIntelligenceInternalTokenFilter |

Route families `/api/v1/**`:

| Family | Auth | Class |
|--------|------|-------|
| `/api/v1/auth/**` | OPEN (login) | OPEN |
| `/api/v1/admin/**` | JWT/header + admin role | PRODUCTION_READY (prod) / INTERIM (staging headers) |
| `/api/v1/applications/**` | JWT/header + staff/borrower | PRODUCTION_READY when jwt.required |
| `/api/v1/documents/**` | JWT/header + ownership | PRODUCTION_READY |
| `/api/v1/internal/**` | Internal token only | INTERNAL |
| `/api/v1/**/webhook/**`, payu, esign callbacks | Signature/token | INTERNAL |
| Actuator | infra | INTERNAL |

Classification note: staging `local-dev-permit-all=true` is **INTERIM**; production profile disables it.
