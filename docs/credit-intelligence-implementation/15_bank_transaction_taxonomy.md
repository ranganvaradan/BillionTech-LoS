# 15 — Bank Transaction Taxonomy

## Categories (`TxnCategory`)

| Category | Typical direction | Cash-flow class | Notes |
|----------|-------------------|-----------------|-------|
| CUSTOMER_RECEIPT | CREDIT | OPERATING | NEFT/UPI customer credits |
| SUPPLIER_PAYMENT | DEBIT | OPERATING | Vendor payments |
| EMI | DEBIT | FINANCING | NACH/ECS lender EMI |
| CASH_DEPOSIT | CREDIT | OPERATING | Cash deposit |
| SELF_TRANSFER | either | TRANSFER | Own A/C / self transfer |
| LOAN_DISBURSEMENT | CREDIT | FINANCING | Excluded from adjusted turnover |
| CAPITAL_INFUSION | CREDIT | FINANCING | Excluded from adjusted turnover |
| CHEQUE_RETURN | DEBIT | OPERATING | Bounce — not goods return |
| NACH_RETURN | either | FINANCING | NACH/ECS/ACH return |
| BANK_CHARGE | DEBIT | OPERATING | Service/SMS/AMC |
| INTEREST_CREDIT / INTEREST_DEBIT | — | FINANCING | Interest credits excluded from adjusted |
| GST_PAYMENT / TAX_PAYMENT | DEBIT | TAX | |
| REFUND | CREDIT | OPERATING | Excluded from adjusted |
| OTHER_OPERATING | — | OPERATING | Mode-assisted soft credit |
| UNKNOWN | — | UNKNOWN | Stays unknown |

## Classifier

- Method / version: `BANK_TXN_CLASSIFIER_V1`
- Deterministic narration regex only
- False-positive guard: `GOODS RETURN` / `SALES RETURN` must **not** become `CHEQUE_RETURN`
- Records method, version, confidence, evidence on `ci_bank_transaction` + `ci_bank_transaction_classification`

## Modes (`TxnMode`)

UPI, NEFT, RTGS, IMPS, CHEQUE, CASH, NACH, ECS, CARD, ACH, INTERNAL_TRANSFER, BANK_CHARGE, INTEREST, OTHER, UNKNOWN

## Account types

SAVINGS, CURRENT, OVERDRAFT, CASH_CREDIT, LOAN, ESCROW, WALLET, OTHER, UNKNOWN

Aggregation eligibility: CURRENT/SAVINGS (and UNKNOWN/OTHER) when ownership is MATCH / PROBABLE_MATCH / UNKNOWN — **not** MISMATCH. OD/CC excluded from ADB aggregation; used for utilisation only.
