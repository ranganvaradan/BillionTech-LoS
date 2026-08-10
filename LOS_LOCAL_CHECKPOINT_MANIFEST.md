# LOS Local Checkpoint Manifest (Phase 1)

**Created:** 2026-08-10  
**Scope:** Local git commits only — no push, no GitHub, no deploy, no DB changes.

## Safety confirmations

- No push to `repo.billionloans.com`
- GitHub `BillionTech-LoS` untouched
- No deploy / no migrations run / no DB changes
- No borrower documents or `.env` secrets included in new commits
- `allow-canonical-authority: false` present in `application.yml` and `application-staging.yml`

---

## 1. los-core-service

| Field | Value |
|-------|--------|
| Path | `D:\CurrentAug032025\Platform\BillionTechLoS\los-core-service` |
| Branch | `main` |
| Origin | `https://repo.billionloans.com/LOS/los-core-service.git` |
| Old HEAD | `b4935df2087cadc7bbc7dc9e9cc1838c8d427438` |
| New HEAD | `b5e9d81b66611d8dd837bdfb4ac4edb56f14188d` |
| Ahead/behind vs origin | **2 / 0** (local only) |

### Commits

1. `1a57927dadd52621932ce7ea567e2488acae7617` — `feat(los): add credit intelligence decision policy foundation`
2. `b5e9d81b66611d8dd837bdfb4ac4edb56f14188d` — `fix(los): harden manual application kyc underwriting and cam flow`

### Tests run

- `mvn -DskipTests compile` — PASS
- Targeted suite (creditintelligence.** + CAM/KYC/document/bureau/loan/exception tests) — PASS (616 tests after one related assertion alignment in `AiUnderwriterA1Test` to `AiUnderwriterViewBuilder.BANNER`)

### Remaining dirty (expected)

| Category | Notes |
|----------|--------|
| J. PII / DOCUMENTS | One already-tracked modified borrower document under `data/documents/**` — **not staged** (path omitted) |
| History risk | Hundreds of already-tracked `data/documents/**` blobs remain in prior history; `.gitignore` now lists `/data/documents/` (does not untrack history) |

### .gitignore

Added/expanded: `target/`, `org/`, `data/documents/`, `.env*`, logs/crash dumps, IDE files.

---

## 2. ui-service

| Field | Value |
|-------|--------|
| Path | `D:\CurrentAug032025\Platform\BillionTechLoS\ui-service` |
| Branch | `main` |
| Origin | `https://repo.billionloans.com/LOS/ui-service.git` |
| Old HEAD | `d72f7059cba542f0c4ecfd91ad9c7ab42880c4e1` |
| New HEAD | `1864712c753bc79d5f6de894e62d6963ef821cd9` |
| Ahead/behind vs origin | **3 / 0** (local only) |

### Commits

1. `e8a0fd93e47fd03fc66b4f68dcdff64971b9cc62` — `feat(los): add credit intelligence UI and policy studio surfaces`
2. `4a5103780fcd02e319c2985f81adf600b17a9460` — `feat(los): improve operational and application workbench ux`
3. `1864712c753bc79d5f6de894e62d6963ef821cd9` — `fix(los): harden application document kyc and underwriting ui`

### Tests run

- `vitest run` — 198 passed, 1 failed (`intakeValidation.test.ts`) — **PRE-EXISTING** (file not part of dirty product changes)
- `tsc -b` — PASS
- `vite build` — PASS

### Remaining dirty

Clean working tree (dist tarballs / IDE metadata ignored).

### .gitignore

Added: `*.tar.gz`, `ui-dist*.tar.gz`, `.env*`, `hs_err_pid*`, `.project`, `.settings/`.

---

## 3. discovery-service

| Field | Value |
|-------|--------|
| Path | `D:\CurrentAug032025\Platform\BillionTechLoS\discovery-service` |
| Branch | `main` |
| Origin | `https://repo.billionloans.com/LOS/discovery-service.git` |
| HEAD (unchanged) | `b8f60c7910bd44355fcb5bfc8f739a07f0c32cec` |
| Ahead/behind | **0 / 0** |
| Commits | **None** — only IDE junk untracked |

---

## 4. notification-service

| Field | Value |
|-------|--------|
| Path | `D:\CurrentAug032025\Platform\BillionTechLoS\notification-service` |
| Branch | `main` |
| Origin | `https://repo.billionloans.com/LOS/notification-service.git` |
| HEAD (unchanged) | `db04b7ba6e6d3e59f1b86d6e0c4d5e341af828ce` |
| Ahead/behind | **0 / 0** |
| Commits | **None** — only IDE junk untracked |

---

## Unsafe categories excluded from new commits

- Borrower / KYC document uploads (`data/documents/**`)
- `.env` / `.env.dev` (outside service repos under `infra/los/`)
- `ui-dist*.tar.gz` build/deploy archives
- `org/**/*.class`, `hs_err_pid*`, IDE `.classpath`/`.project`/`.settings`
- Runtime secrets / credentials

## Root assets not in any service git repo

Still outside the four service repositories (not checkpointed here):

- `docs/`
- `infra/` (contains secret `.env` files — do not import blindly)
- Investigation markdown at workspace root

## Next phase readiness

Local product source for los-core + ui is committed.  
GitHub monorepo import remains a **separate** controlled phase (histories still disconnected).
