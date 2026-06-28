# Collateral & Account Aggregator — Build Spec
## Branch: `feature/collateral-aa`
## Repo: `ranganvaradan/BillionTech-LoS`

---

## CONTEXT

This spec describes what needs to be built for the **Collateral** and **Account Aggregator (AA)** modules. The backend foundations already exist. The work is primarily:

1. UI components and pages
2. Frontend API layer
3. Minor backend enhancements

All new UI code goes in: `ui-service/src/`
All new backend code goes in: `services/los-core-service/src/main/java/com/los/core/`

---

## PART 1: COLLATERAL MODULE

### 1.1 What Already Exists

**Backend (DO NOT MODIFY — already working):**
- Entity: `model/entity/CollateralValuation.java`
  - Fields: id, applicationId, collateralType, description, address, marketValue, forcedSaleValue, valuationAmount, valuerId, valuerName, valuationDate, valuationExpiry, status, details
  - Status values: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `EXPIRED`
  - Types: `PROPERTY`, `VEHICLE`, `GOLD`, `FIXED_DEPOSIT`, `SHARES`, `MACHINERY`
- Repository: `repository/CollateralValuationRepository.java`
- Service: `service/collateral/CollateralValuationService.java`
  - `createValuation(CollateralValuation)`
  - `completeValuation(UUID valuationId, BigDecimal marketValue, BigDecimal forcedSaleValue, String valuerId, String valuerName)`
  - `getValuationsByApplication(UUID applicationId)`
  - `calculateLtv(UUID applicationId, BigDecimal loanAmount)`
- Controller: `controller/CollateralController.java`
  - `POST /api/v1/collateral` — create valuation
  - `POST /api/v1/collateral/{valuationId}/complete` — complete with values
  - `GET /api/v1/collateral/application/{applicationId}` — list by application
  - `GET /api/v1/collateral/ltv/{applicationId}?loanAmount=X` — LTV calc
- DB: Table `collateral_valuations` in V4 migration

**Frontend (partial — intake only):**
- `components/intake/CollateralIntakeFields.tsx` — intake form for PROPERTY, SHARES, GOLD
- `lib/intake/securedProducts.ts` — collateral type definitions
- `lib/intake/collateralIntakePayload.ts` — payload builder

### 1.2 What Needs to Be Built

#### A. Frontend API Layer
**File: `ui-service/src/api/collateral.ts`** (NEW)

```typescript
// Create using the existing http client pattern from other api files
import { http } from './http'

export interface CollateralValuation {
  id: string
  applicationId: string
  collateralType: 'PROPERTY' | 'VEHICLE' | 'GOLD' | 'FIXED_DEPOSIT' | 'SHARES' | 'MACHINERY'
  description?: string
  address?: string
  marketValue?: number
  forcedSaleValue?: number
  valuationAmount?: number
  valuerId?: string
  valuerName?: string
  valuationDate?: string
  valuationExpiry?: string
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED'
  details?: Record<string, unknown>
  createdAt: string
}

export interface LtvResult {
  applicationId: string
  loanAmount: number
  totalCollateralValue: number
  ltvRatio: number
  ltvAcceptable: boolean
  maxAllowedLtv: number
  valuationCount: number
}

export const collateralApi = {
  list: (applicationId: string) =>
    http.get<CollateralValuation[]>(`/api/v1/collateral/application/${applicationId}`),

  create: (data: Partial<CollateralValuation>) =>
    http.post<CollateralValuation>('/api/v1/collateral', data),

  complete: (
    valuationId: string,
    params: { marketValue: number; forcedSaleValue?: number; valuerId: string; valuerName: string }
  ) =>
    http.post<CollateralValuation>(
      `/api/v1/collateral/${valuationId}/complete`,
      null,
      { params }
    ),

  calculateLtv: (applicationId: string, loanAmount: number) =>
    http.get<LtvResult>(`/api/v1/collateral/ltv/${applicationId}`, {
      params: { loanAmount },
    }),
}
```

#### B. Collateral Management Panel Component
**File: `ui-service/src/components/CollateralPanel.tsx`** (NEW)

This is a panel that appears in the `ApplicationDetailPage` under a "Collateral" tab or section.

**UI Requirements:**
- Show a table of all collateral valuations for the application
- Each row shows: Type, Description, Status badge, Market Value, FSV, Accepted Value, Valuer, Expiry
- Status badges: PENDING (amber), IN_PROGRESS (blue), COMPLETED (green), EXPIRED (red)
- "Add Collateral" button — opens inline form to create new valuation
- "Complete Valuation" button on PENDING/IN_PROGRESS rows — opens modal with fields:
  - Market Value (INR) *
  - Forced Sale Value (INR) — optional
  - Valuer ID *
  - Valuer Name *
- LTV Summary card at top showing:
  - Total Collateral Value
  - LTV Ratio (%)
  - LTV Acceptable (green tick / red cross)
  - Max Allowed LTV: 80%

**Add Collateral Form fields:**
- Collateral Type (dropdown: PROPERTY, VEHICLE, GOLD, FIXED_DEPOSIT, SHARES, MACHINERY)
- Description (text)
- Address (textarea — only shown for PROPERTY, VEHICLE)
- Estimated Market Value (number)

**Access control:** Only show "Add" and "Complete" buttons to users with roles: CREDIT_MANAGER, ADMIN, UNDERWRITER

#### C. Backend Enhancement — Add VEHICLE, MACHINERY, FIXED_DEPOSIT intake fields
**File: `ui-service/src/components/intake/CollateralIntakeFields.tsx`** (MODIFY — add to existing)

Currently only PROPERTY, SHARES, GOLD are handled. Add:

**VEHICLE fields:**
- Vehicle type (dropdown: TWO_WHEELER, FOUR_WHEELER, COMMERCIAL)
- Make / Model
- Year of manufacture
- Registration number
- Estimated market value (INR)
- Existing loan on vehicle? (yes/no radio)

**FIXED_DEPOSIT fields:**
- Bank name
- FD account number
- FD amount (INR)
- Maturity date
- FD receipt number

**MACHINERY fields:**
- Machinery type / description
- Make / Model
- Year of purchase
- Estimated current value (INR)
- Location / address

#### D. LTV Display in CAM/Underwriting Section
**File: `ui-service/src/components/CamSection.tsx`** (MODIFY — add LTV block)

In the Credit Appraisal Memo section, add a "Collateral & LTV" subsection that:
- Calls `collateralApi.calculateLtv(applicationId, loanAmount)`
- Displays LTV ratio with color coding (green ≤ 60%, amber 60-80%, red > 80%)
- Lists collateral items with their accepted values

---

## PART 2: ACCOUNT AGGREGATOR MODULE

### 2.1 What Already Exists

**Backend (DO NOT MODIFY — already working):**
- Entity: `model/entity/AaConsent.java`
  - Fields: id, applicationId, customerId, consentHandle, consentId, status, fiTypes, consentStartDate, consentExpiryDate, fetchFrequency, consentMode, purposeInfo, aaName, approvedAt, revokedAt, revokeReason, fetchedDataSummary, dataFetchedAt
  - Status: `PENDING` → `APPROVED` → `DATA_FETCHED` → `REVOKED`
- Repository: `repository/AaConsentRepository.java`
- Service: `service/aa/AccountAggregatorService.java`
  - `createConsentRequest(applicationId, customerId, fiTypes, aaName, purpose)`
  - `approveConsent(consentHandle, consentId)`
  - `fetchData(consentHandle)` — currently simulated
  - `revokeConsent(consentId, reason)`
  - `getConsents(applicationId)`
  - `hasActiveConsent(applicationId)`
- Controller: `controller/AccountAggregatorController.java`
  - `POST /api/v1/aa/consent` — create consent
  - `POST /api/v1/aa/consent/{handle}/approve` — approve
  - `POST /api/v1/aa/consent/{handle}/fetch` — fetch data
  - `POST /api/v1/aa/consent/{consentId}/revoke` — revoke
  - `GET /api/v1/aa/consent/application/{applicationId}` — list
  - `GET /api/v1/aa/consent/{handle}` — get by handle
  - `GET /api/v1/aa/consent/application/{applicationId}/active` — check active
- DB: Table `aa_consents` in V3 migration

**Frontend (minimal — only a checkbox in intake):**
- Checkbox `consentAccountAggregator` in `ApplicationIntakeWizard.tsx`
- No panel, no data display, no consent management

### 2.2 What Needs to Be Built

#### A. Frontend API Layer
**File: `ui-service/src/api/accountAggregator.ts`** (NEW)

```typescript
import { http } from './http'

export interface AaConsent {
  id: string
  applicationId: string
  customerId: string
  consentHandle: string
  consentId?: string
  status: 'PENDING' | 'APPROVED' | 'DATA_FETCHED' | 'REVOKED'
  fiTypes: string[]
  consentStartDate: string
  consentExpiryDate: string
  fetchFrequency: string
  consentMode: string
  purposeInfo: Record<string, unknown>
  aaName: string
  approvedAt?: string
  revokedAt?: string
  revokeReason?: string
  fetchedDataSummary?: AaFetchedData
  dataFetchedAt?: string
  createdAt: string
}

export interface AaFetchedData {
  fetchTimestamp: string
  accountCount: number
  accounts: Array<{
    type: string
    bank: string
    balance: number
    avgMonthlyBalance: number
    txnCount6Months: number
  }>
  totalBalance: number
  avgMonthlyInflow: number
  avgMonthlyOutflow: number
  regularEmiOutflows: number
  bounceCount6Months: number
}

export const aaApi = {
  list: (applicationId: string) =>
    http.get<AaConsent[]>(`/api/v1/aa/consent/application/${applicationId}`),

  create: (params: {
    applicationId: string
    customerId: string
    fiTypes?: string[]
    aaName?: string
  }) =>
    http.post<AaConsent>('/api/v1/aa/consent', null, { params }),

  approve: (consentHandle: string, consentId: string) =>
    http.post<AaConsent>(`/api/v1/aa/consent/${consentHandle}/approve`, null, {
      params: { consentId },
    }),

  fetchData: (consentHandle: string) =>
    http.post<AaConsent>(`/api/v1/aa/consent/${consentHandle}/fetch`),

  revoke: (consentId: string, reason: string) =>
    http.post<AaConsent>(`/api/v1/aa/consent/${consentId}/revoke`, null, {
      params: { reason },
    }),

  hasActive: (applicationId: string) =>
    http.get<{ hasActiveConsent: boolean }>(
      `/api/v1/aa/consent/application/${applicationId}/active`
    ),
}
```

#### B. AA Consent Panel Component
**File: `ui-service/src/components/AaConsentPanel.tsx`** (NEW)

This panel appears in `ApplicationDetailPage` under "Bank Data / AA" tab.

**UI Layout:**

**Top section — Status Banner:**
- If no consents: show "No AA consent initiated" with "Initiate Consent" button
- If PENDING: amber banner "Waiting for borrower approval" with consent handle displayed
- If APPROVED: blue banner "Consent approved — ready to fetch data" with "Fetch Data" button
- If DATA_FETCHED: green banner "Bank data available" with data fetched timestamp
- If REVOKED: red banner "Consent revoked"

**Initiate Consent Form (shown when no active consent):**
- FI Types (multi-select checkboxes): DEPOSIT, TERM_DEPOSIT, RECURRING_DEPOSIT, SIP, CP, GOVT_SECURITIES, EQUITIES, BONDS, DEBENTURES, MF, ETF, IDR, CIS, AIF, INSURANCE_POLICIES, NPS, INVIT, REIT, OTHER
- Default selected: DEPOSIT, TERM_DEPOSIT
- AA Provider (text field, default "DEFAULT_AA")
- "Send Consent Request" button

**Consent History Table:**
- Columns: Handle, Status, AA Provider, FI Types, Created, Approved At, Data Fetched At, Action
- Action column: "Fetch Data" button for APPROVED status, "Revoke" button for PENDING/APPROVED

**Financial Data Display (shown when DATA_FETCHED):**
Show a summary card with:
```
Bank Accounts Summary
─────────────────────────────────────────
Total Balance          ₹11,35,000
Avg Monthly Inflow     ₹4,50,000
Avg Monthly Outflow    ₹3,20,000
Regular EMI Outflows   ₹45,000
Bounce Count (6M)      0

Accounts (2):
  SBI Savings    Balance: ₹2,45,000   Avg Balance: ₹1,80,000   Txns: 142
  HDFC Current   Balance: ₹8,90,000   Avg Balance: ₹6,20,000   Txns: 387
```

Display a FOIR indicator:
- FOIR = regularEmiOutflows / avgMonthlyInflow × 100
- Color: green ≤ 40%, amber 40-50%, red > 50%

**Access control:** Only CREDIT_MANAGER, ADMIN, UNDERWRITER can initiate/fetch. All can view.

#### C. AA Data in Underwriting Section
**File: `ui-service/src/components/UnderwritingSection.tsx`** (MODIFY)

Add a "AA Bank Data" subsection in underwriting that:
- Shows condensed version of the AA data (total balance, avg inflow, FOIR)
- Shows a green "AA Verified" badge if DATA_FETCHED, amber "AA Pending" if not

#### D. Backend Enhancement — Customer ID Resolution
**File: `services/los-core-service/src/main/java/com/los/core/controller/AccountAggregatorController.java`** (MINOR MODIFY)

The `createConsent` endpoint currently requires `customerId` as a separate param. Add a convenience endpoint:

```java
@PostMapping("/consent/application/{applicationId}/initiate")
@Operation(summary = "Initiate AA consent for application — resolves customer automatically")
public ResponseEntity<AaConsent> initiateForApplication(
        @PathVariable UUID applicationId,
        @RequestParam(required = false) List<String> fiTypes,
        @RequestParam(required = false) String aaName,
        @RequestBody(required = false) Map<String, Object> purpose) {
    // Fetch the application to get the primary borrower/customer UUID
    // Then call aaService.createConsentRequest(...)
}
```

This requires injecting `LoanApplicationRepository` or `LoanApplicationService` to resolve the customer ID from the application.

---

## PART 3: INTEGRATION INTO ApplicationDetailPage

**File: `ui-service/src/pages/ApplicationDetailPage.tsx`** (MODIFY)

Add two new tabs to the existing tab navigation:

```
[Summary] [KYC] [Documents] [Underwriting] [CAM] [Collateral] [Bank Data] [Audit]
                                                        ^new          ^new
```

**Collateral tab:** Renders `<CollateralPanel applicationId={applicationId} loanAmount={application.loanAmount} />`

**Bank Data tab:** Renders `<AaConsentPanel applicationId={applicationId} />`

Only show these tabs when the loan product is secured (for Collateral) or when AA is applicable (for Bank Data). Check `application.loanProduct` or `application.borrowerType`.

---

## PART 4: BACKEND — NEW MIGRATION

**File: `services/los-core-service/src/main/resources/db/migration/V20__collateral_aa_enhancements.sql`** (NEW)

```sql
-- Add collateral reference to loan_applications
ALTER TABLE loan_applications 
  ADD COLUMN IF NOT EXISTS collateral_ltv_ratio DECIMAL(5,2),
  ADD COLUMN IF NOT EXISTS collateral_total_value DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS aa_consent_status VARCHAR(30);

-- Add index for faster AA lookups
CREATE INDEX IF NOT EXISTS idx_aa_consent_customer ON aa_consents(customer_id);

-- Add AA data summary to underwriting results
ALTER TABLE underwriting_results
  ADD COLUMN IF NOT EXISTS aa_data_summary JSONB,
  ADD COLUMN IF NOT EXISTS foir_from_aa DECIMAL(5,2);
```

---

## PART 5: FILE STRUCTURE SUMMARY

### New Files to Create:
```
ui-service/src/api/collateral.ts
ui-service/src/api/accountAggregator.ts
ui-service/src/components/CollateralPanel.tsx
ui-service/src/components/AaConsentPanel.tsx
services/los-core-service/src/main/resources/db/migration/V20__collateral_aa_enhancements.sql
```

### Files to Modify:
```
ui-service/src/components/intake/CollateralIntakeFields.tsx  — add VEHICLE, FD, MACHINERY
ui-service/src/components/CamSection.tsx                     — add LTV block
ui-service/src/components/UnderwritingSection.tsx            — add AA data block
ui-service/src/pages/ApplicationDetailPage.tsx               — add 2 new tabs
services/los-core-service/src/main/java/com/los/core/controller/AccountAggregatorController.java — add initiate endpoint
```

---

## PART 6: TESTING CHECKLIST

### Collateral:
- [ ] Can create a PROPERTY collateral valuation for an application
- [ ] Can create a VEHICLE, GOLD, SHARES, FD, MACHINERY valuation
- [ ] Can complete a valuation with market value and FSV
- [ ] LTV calculates correctly (loan / total collateral × 100)
- [ ] LTV > 80% shows as unacceptable
- [ ] Collateral tab shows in ApplicationDetailPage
- [ ] Intake wizard captures collateral fields for all types

### Account Aggregator:
- [ ] Can initiate AA consent from ApplicationDetailPage
- [ ] Consent handle is generated and displayed
- [ ] Can approve consent (simulate)
- [ ] Can fetch data after approval
- [ ] Fetched data displays correctly (balances, FOIR)
- [ ] FOIR calculation is correct
- [ ] Can revoke consent
- [ ] AA data shows in underwriting section

---

## NOTES FOR CURSOR

1. **Use the existing HTTP client** — look at `ui-service/src/api/applications.ts` for the pattern
2. **Follow existing component style** — use Tailwind classes following the existing design system in the codebase
3. **Role-based access** — check how roles are checked in existing components (e.g., `UnderwritingSection.tsx`) and follow the same pattern
4. **Error handling** — follow the existing toast/error pattern used in other components
5. **The backend is already running** — do not change existing backend endpoints, only add new ones
6. **Branch**: Always work on `feature/collateral-aa`
