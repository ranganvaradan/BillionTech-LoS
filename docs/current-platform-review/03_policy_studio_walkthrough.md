# 03 — Policy Studio Walkthrough (Banking + Bureau BRE)

**Mode:** Authoring only (`DRAFT_ONLY` packages). Never activates production.  
**Fixtures:** `policy-fixtures/banking-bre/`, `policy-fixtures/bureau-bre/`  
**Flags:** `credit-intelligence.policy-studio.enabled` (default false)

---

## Banking BRE

### Source → system

| Product / topic | Source intent | System rule ID(s) | Mapping status |
|-----------------|---------------|-------------------|----------------|
| **Starter** | ADB 3m ≥ EDI | `BANK_STARTER_ADB_GTE_EDI` | EDI → `PROPOSED_EDI` parameter unless vocabulary resolves |
| **DigiLeap** | ADB/5 ≥ EDI **and** avg monthly txn ≥ 20 | `BANK_DIGILEAP_*` | Period 3m; txn definition ambiguous |
| **Smart Switch** | Settlement/10 ≥ EDI **and** settlement count ≥ 20 | `BANK_SMART_SWITCH_*` | QR settlement often **DATA_INSUFFICIENT** if taxonomy weak |
| **Reboost** | Amount **>** 60,000 then ADB/5 ≥ EDI and txn ≥ 30 | `BANK_REBOOST_*` | Uses **GT** not GTE for 60k |
| **Inward returns** | Branch at 100 (%/% vs count) | `BANK_INWARD_RETURN_BRANCHED_100` | **Exactly-100** unresolved → review required |
| **Adjusted ADB** | Exclude loan disbursements, gaming, bulk deposits | `BANK_POLICY_ADJUSTED_ADB_3M` | Does **not** mutate global `banking.avg_daily_balance_3m`; bulk needs “average deposit” definition |

### Ambiguities (blocking until human resolve)

| Term | Issue | Typical action |
|------|-------|----------------|
| EDI | Unknown business term | `CREATE_POLICY_PARAMETER` → `PROPOSED_EDI` |
| Exactly 100 | Unspecified boundary | Reviewer chooses % / count / separate / ask customer |
| Average monthly transactions | Multiple canonical matches | Human select |
| Average depositions | Unclear denominator | Vocabulary / parameter |
| Settlement / QR | Missing or unreliable classification | DI — do not invent |

### Draft readiness (honest)

Before customer resolutions: **BLOCKED** / review required.  
After explicit resolve APIs in tests: can reach **READY_FOR_POLICY_BUILD**.  
Never faked as production-active.

### Example rule card — DigiLeap

```text
SOURCE CLAUSE
  DigiLeap: Adjusted ADB (3M) / 5 >= Proposed EDI
            AND average monthly transactions (3M) >= 20

SYSTEM INTERPRETATION
  Compound HARD rule; missing banking → DATA_INSUFFICIENT / REFER per DSL onMissing

CANONICAL MAPPING
  banking.avg_daily_balance_3m (or adjusted ADB candidate)
  POLICY_PARAMETER_REF: PROPOSED_EDI
  banking.avg_monthly_txn_count_3m (candidate; confirm definition)

EXECUTABLE RULE (shape)
  AND( GTE(DIVIDE(ADB,5), PROPOSED_EDI), GTE(TXN_AVG, 20) )

TEST CASES
  happy / fail / boundary / missing / DI  (generated; critical need APPROVED)

REVIEW STATUS
  Material ambiguities open until Credit Manager + checker
```

---

## Bureau BRE

| Topic | System behavior | Status |
|-------|-----------------|--------|
| Score / NTC | OR: score=-1 / NTC / score≥650 | NTC meaning customer-scoped |
| Write-off | Non-CC write-off count = 0 (CC exception separate) | Canonical statuses |
| Overdue + exception | Age>12m **AND** new credit after overdue **AND** clean≥6m **AND** amount&lt;1500 | Age/clean metrics were P0.1 additions; CLEAN undefined until resolved |
| CC overdue | ≤ 5000 | Boundary tests 5000/5001 |
| Max DPD 6m | ≤ 30 | Clock from EvaluationContext |
| Settled / restructured / legal / DBT/PWOS/LSS | Canonical status counts / worst status | Provider raw strings forbidden |
| Multiple PAN / inquiries / account sold | Count rules | Current-month inquiries use frozen clock |

### Compound overdue exception (exact intent preserved)

```text
overdue exists
AND exception allowed only if ALL:
  age > 12 months
  new credit exists after overdue
  new credit clean history >= 6 months
  overdue amount < 1500
```

CC overdue remains a **separate** rule.

---

## Could a non-technical Credit Head configure this without an engineer?

**Not today as a self-serve product.**

Backend/API contracts exist (session, ambiguity resolve, maker-checker, draft package, preview). Missing for Credit Head UX:

1. Guided Policy Studio UI for upload → clause cards → ambiguity wizard  
2. Plain-language preview always default (JSON hidden)  
3. Vocabulary library UI with “previously approved by Client X on date”  
4. One-click fixture vs live document distinction  
5. Test approval UI with human expected outcomes  
6. Checker workflow screens separate from author  
7. Publish-to-SHADOW confirmation without engineering APIs  

**Verdict:** Strong authoring **subsystem**; not yet a Credit Head product surface.
