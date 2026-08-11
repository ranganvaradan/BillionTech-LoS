# First live customer — configuration input checklist

Canonical cutover record: [`LOS-CUSTOMER-CUTOVER-1-RECORD.md`](./LOS-CUSTOMER-CUTOVER-1-RECORD.md)

Do **not** invent values. Leave blank until the customer supplies them.

| Field | Value (customer) | Notes |
|-------|------------------|-------|
| Tenant id / name | | |
| Borrower types | | e.g. COMPANY |
| Products | | e.g. TERM_LOAN |
| Segments | | BORROWER / ANCHOR |
| Amount ranges | | |
| Workflow IDs / versions | | must be ACTIVE |
| Live Rule Set IDs / versions | | production authority |
| Scorecard IDs / versions | | ACTIVE + governance complete |
| Policy Studio policy references | | governance/shadow only |
| Required data sources / providers | | bureau, KYC, GST, AA/BSA, … |
| Manual input stages | | |
| LMS product codes | | no UI invent |
| PLP mapping | | NOT_REQUIRED if unused |
| User / role list | | ADMIN, CM, checker, ops, borrower |
| Sanction flow | | roles + steps |
| eSign | | |
| Notification config | | SMTP/SMS |
| Provider credentials | | env secrets — never commit |
| Environment URLs | | staging vs prod |
| Cutover date | | |

Validator API (admin): `POST /api/v1/admin/live-readiness/customer-go-live`

Body example:

```json
{
  "borrowerType": "COMPANY",
  "loanProduct": "TERM_LOAN",
  "customerConfigSupplied": false
}
```

`customerConfigSupplied` must be `true` only after this checklist is filled by humans.
