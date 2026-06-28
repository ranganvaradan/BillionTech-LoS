# LOS v2.0 — Detailed Functional Requirements Document (FRD)

## Document Control

| Field | Value |
|-------|-------|
| **Version** | 2.0 |
| **Date** | April 2026 |
| **Classification** | CONFIDENTIAL — Lender Internal Use Only |
| **Companion Document** | 01_Business_Requirements_Document.md |

---

## 1. End-to-End Loan Origination Flow — Master Process

```
┌─────────────┐    ┌──────────┐    ┌─────────┐    ┌──────────────┐    ┌──────────────┐
│ Lead Capture │───▶│ Consent  │───▶│   KYC   │───▶│ Docs Upload  │───▶│  Data Fetch   │
│  & Register  │    │ & OTP    │    │ Engine  │    │ & Validation │    │ (AA/Bureau)   │
└─────────────┘    └──────────┘    └─────────┘    └──────────────┘    └──────────────┘
                                                                              │
       ┌──────────────────────────────────────────────────────────────────────┘
       ▼
┌──────────────┐    ┌──────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────────┐
│ Underwriting │───▶│ Decision │───▶│ Sanction │───▶│  eSign (KFS  │───▶│ Disbursement │
│  & Scoring   │    │ (Approve │    │  Letter  │    │ + Agreement) │    │              │
│              │    │ /Reject) │    │          │    │              │    │              │
└──────────────┘    └──────────┘    └──────────┘    └──────────────┘    └──────────────┘
```

### Application Status State Machine

```
                                    ┌──────────────┐
                         ┌─────────│   REJECTED    │
                         │         └──────────────┘
                         │ (reject)
┌────────┐  ┌─────────┐  ┌──────────┐  ┌──────────────┐  ┌───────────┐  ┌───────────┐
│ DRAFT  │─▶│CONSENT_ │─▶│ KYC_IN_  │─▶│ UNDERWRITING │─▶│ APPROVED  │─▶│ SANCTION_ │
│        │  │PENDING  │  │PROGRESS  │  │              │  │           │  │ ISSUED    │
└────────┘  └─────────┘  └──────────┘  └──────────────┘  └───────────┘  └───────────┘
                              │                                              │
                              │ (KYC fail)                                   │
                              ▼                                              ▼
                        ┌──────────┐                               ┌───────────────┐
                        │ KYC_     │                               │ ESIGN_PENDING │
                        │ FAILED   │                               └───────────────┘
                        └──────────┘                                       │
                                                                           ▼
                                                                  ┌───────────────┐
                                                                  │  DISBURSEMENT │
                                                                  │   _PENDING    │
                                                                  └───────────────┘
                                                                           │
                                                                           ▼
                                                                  ┌───────────────┐
                                                                  │  DISBURSED    │
                                                                  └───────────────┘
```

**Valid Transitions:**

| From | To | Trigger | Actor |
|------|----|---------|-------|
| — | DRAFT | Application created | Customer / RM |
| DRAFT | CONSENT_PENDING | Application submitted | System |
| CONSENT_PENDING | KYC_IN_PROGRESS | Consent captured | System |
| KYC_IN_PROGRESS | KYC_FAILED | All mandatory KYC steps failed | System |
| KYC_FAILED | KYC_IN_PROGRESS | KYC retry initiated | Customer / RM |
| KYC_IN_PROGRESS | UNDERWRITING | All mandatory KYC steps passed | System |
| UNDERWRITING | APPROVED | Credit decision: approve | Credit Officer / Auto |
| UNDERWRITING | REJECTED | Credit decision: reject | Credit Officer |
| APPROVED | SANCTION_ISSUED | Sanction letter generated | System |
| SANCTION_ISSUED | ESIGN_PENDING | KFS sent for signing | System |
| ESIGN_PENDING | DISBURSEMENT_PENDING | KFS + Agreement signed | System (webhook) |
| DISBURSEMENT_PENDING | DISBURSED | Funds transferred | Operations |
| Any active state | WITHDRAWN | Customer withdraws | Customer |
| Any active state | ON_HOLD | Manual hold | Credit Manager |
| ON_HOLD | (previous state) | Hold released | Credit Manager |

---

## 2. Module-Level Functional Specifications

### 2.1 Customer Registration & Onboarding

#### FR-REG-001: Customer Self-Registration Flow

```
Customer                    System                     SMS Gateway
   │                          │                            │
   │─── Enter Mobile No. ────▶│                            │
   │                          │─── Generate 6-digit OTP ──▶│
   │                          │    (Redis TTL: 5 min)      │─── Send SMS ──▶ Customer Phone
   │                          │                            │
   │◀── Show OTP Input ──────│                            │
   │─── Submit OTP ──────────▶│                            │
   │                          │─── Validate OTP (Redis) ──▶│
   │                          │                            │
   │                          │─── OTP Valid? ────────────▶│
   │                          │    Yes: Create customer    │
   │                          │    No: Return error        │
   │                          │                            │
   │◀── JWT Token + Profile ──│                            │
```

**Functional Details:**
- **OTP Generation**: 6-digit random, stored in Redis with 5-minute TTL
- **OTP Retry**: Maximum 3 attempts per session; lockout for 15 minutes after 3 failures
- **OTP Resend**: Maximum 3 resends per mobile number per hour
- **Customer Record**: On first successful OTP, create customer record with mobile number
- **JWT Issuance**: Issue access token (15 min) + refresh token (7 days, HttpOnly cookie)
- **Duplicate Check**: If mobile already registered, authenticate existing customer

#### FR-REG-002: RM-Initiated Registration

```
RM (Credit Officer)           System                  Customer
   │                            │                        │
   │─ Enter Customer Mobile ──▶│                        │
   │                            │─── Generate consent ──▶│ (SMS + WhatsApp link)
   │                            │    link (JWT, 24h)     │
   │                            │                        │─── Click consent link
   │                            │◀── Consent captured ───│    (timestamp, IP, device)
   │                            │                        │
   │◀── Customer ready ────────│                        │
   │─── Start application ────▶│                        │
```

**Functional Details:**
- RM enters customer mobile number and basic details
- System generates a signed consent URL (24-hour JWT expiry)
- URL sent via SMS + WhatsApp to customer
- Customer clicks link → consent page shows data usage terms → customer confirms via OTP
- Consent artefact stored: timestamp, IP address, device fingerprint, OTP session ID, consent version
- RM can proceed with application after consent is captured

#### FR-REG-003: Email Verification

- After mobile verification, customer optionally provides email
- System sends 6-digit OTP to email address
- Same OTP validation logic as mobile (Redis TTL 5 min, 3 attempts max)
- Email verification status stored on customer record
- Email verified flag shown in application detail view

---

### 2.2 Loan Application Wizard

#### FR-APP-001: Multi-Step Application Wizard

**Steps (conditional based on borrower type):**

| Step # | Step Name | Applicable To | Required Fields |
|--------|-----------|---------------|-----------------|
| 1 | Loan Type Selection | All | required_loan_type |
| 2 | Borrower Type | All | borrower_type (Individual/Proprietor/Partnership/Company) |
| 3 | Basic Information | All | name, DOB/incorporation_date, email, mobile, address |
| 4 | Identity & KYC | All | PAN (mandatory), Aadhaar or secondary ID, business IDs |
| 5 | Business Details | Proprietor, Partnership, Company | GSTIN, CIN, Udyam, partners/directors |
| 6 | Loan Details | All | amount, purpose, tenure, product type |
| 7 | Bank Verification | All | account number, IFSC, penny drop verification |
| 8 | Review & Submit | All | consent checkboxes, review summary, submit |

#### FR-APP-002: Borrower-Type-Specific Form Fields

**Individual:**
- First name, Last name, DOB, Gender
- PAN (mandatory, verified), Aadhaar (12 digits), Voter ID, DL
- Employment type (Salaried-Govt/Salaried/Self-employed/Professional)
- Employer name, Monthly income
- Selfie/liveness photo capture

**Proprietor (Individual + Business):**
- All Individual fields
- Firm name, Firm PAN, GSTIN, Udyam registration
- Business type, Incorporation date

**Partnership:**
- Firm name, Firm PAN, GSTIN, Udyam
- Partner details: array of {name, PAN, Aadhaar} — each PAN verified
- At least 2 partners required

**Company:**
- Company name, CIN (21-char MCA format), Firm PAN, GSTIN
- Director details: array of {name, DIN, PAN} — each PAN verified
- At least 1 director required
- Incorporation date from MCA records

#### FR-APP-003: Draft Auto-Save

- Application form auto-saves to backend every 30 seconds while user is editing
- Saved as DRAFT status
- Customer can resume from where they left off
- Auto-save does not trigger workflow or validations

#### FR-APP-004: Application Submission

On submit:
1. Validate all mandatory fields
2. Validate consent checkboxes (data processing, KYC, bureau check)
3. Create consent artefact
4. Transition status: DRAFT → CONSENT_PENDING → KYC_IN_PROGRESS
5. Trigger KYC workflow execution
6. Return application ID and redirect to status tracking page

---

### 2.3 KYC Orchestration Engine

#### FR-KYC-001: Workflow-Driven KYC Execution

```
Application Created          Workflow Engine               Integration Router
      │                           │                              │
      │── getActiveWorkflow() ──▶│                              │
      │   (borrowerType)         │                              │
      │                          │─── Load step config ────────▶│
      │                          │    from workflow_configs      │
      │                          │                              │
      │                          │    FOR EACH step:            │
      │                          │─── routeKycCheck() ─────────▶│
      │                          │    (stepType, payload)       │── Call Provider API
      │                          │                              │◀── Provider Response
      │                          │◀── KycCheckResult ──────────│
      │                          │                              │
      │                          │─── Store kyc_step_results ──▶│
      │                          │─── Evaluate pass/fail ──────▶│
      │                          │                              │
      │◀── KYC Complete/Failed ──│                              │
```

#### FR-KYC-002: KYC Step Types and Provider Mapping

| Step Type | Description | Supported Providers | Mandatory For |
|-----------|-------------|--------------------|---------------|
| MOBILE_OTP | Mobile number OTP verification | SMS_GATEWAY | All |
| EMAIL_OTP | Email OTP verification | SMTP | Optional |
| AADHAAR_OTP | Aadhaar eKYC via OTP | AUTHBRIDGE, PERFIOS | All (or secondary ID) |
| PAN_VERIFY | PAN verification and name match | AUTHBRIDGE, PERFIOS | All |
| GSTIN_VERIFY | GSTIN validation | AUTHBRIDGE | Proprietor, Partnership, Company |
| VOTER_ID_VERIFY | Voter ID verification | AUTHBRIDGE | Optional secondary ID |
| DL_VERIFY | Driving License verification | AUTHBRIDGE | Optional secondary ID |
| BANK_PENNY_DROP | Bank account verification | AUTHBRIDGE | All |
| FACE_MATCH | Selfie vs Aadhaar face match | HYPERVERGE | Individual, Proprietor |
| LIVENESS | Liveness detection | HYPERVERGE | Individual, Proprietor |
| VIDEO_KYC | Video Customer ID Process (V-CIP) | HYPERVERGE | On-demand |
| UDYAM_VERIFY | Udyam registration verification | AUTHBRIDGE | MSME loans |
| CIN_MCA21 | Company/director verification | AUTHBRIDGE | Company |
| AML_SCREENING | Anti-money laundering check | AUTHBRIDGE | All |
| CKYC_DOWNLOAD | Central KYC record download | CKYC_REGISTRY | All |
| CKYC_UPLOAD | Central KYC record upload | CKYC_REGISTRY | Post-KYC completion |
| BUREAU_PULL | Credit bureau report pull | EQUIFAX | All (post-KYC) |
| ESIGN_KFS | KFS digital signing | EMSIGNER, AUTHBRIDGE_ESIGN | All (post-approval) |
| ESIGN_AGREEMENT | Loan agreement signing | EMSIGNER, AUTHBRIDGE_ESIGN | All (post-KFS) |

#### FR-KYC-003: KYC Step Execution — PAN Verification Example

```
UI                       KYC Controller           KYC Orchestration          Integration Router        Authbridge API
│                            │                          │                          │                        │
│─ POST /kyc/verify ────────▶│                          │                          │                        │
│  {docType:PAN, pan:XYZ}   │─ executeStep(appId, PAN)▶│                          │                        │
│                            │                          │─ routeKycCheck(PAN, ..) ▶│                        │
│                            │                          │                          │─ Load aggregator_config │
│                            │                          │                          │  Decrypt credentials    │
│                            │                          │                          │─ POST /v2/pan/verify ──▶│
│                            │                          │                          │                        │─ Verify PAN
│                            │                          │                          │◀── {verified, name} ───│
│                            │                          │◀── KycCheckResult ───────│                        │
│                            │                          │                          │                        │
│                            │                          │─ Save kyc_step_results   │                        │
│                            │                          │  status=PASS/FAIL        │                        │
│                            │                          │  confidence_score=1.0     │                        │
│                            │                          │  parsed_data={name, ...}  │                        │
│                            │◀── KycStepResultDTO ─────│                          │                        │
│◀── {verified, name} ──────│                          │                          │                        │
```

#### FR-KYC-004: Aadhaar OTP eKYC Flow

```
Customer                  System                    Authbridge/Perfios        UIDAI
   │                         │                            │                     │
   │── Enter Aadhaar No. ──▶│                            │                     │
   │                         │── sendOtp(aadhaar) ───────▶│                     │
   │                         │                            │── OTP Request ─────▶│
   │                         │                            │◀── OTP Sent ────────│
   │                         │◀── Session ID ─────────────│                     │
   │◀── Show OTP input ─────│                            │                     │
   │                         │                            │                     │
   │── Submit OTP ──────────▶│                            │                     │
   │                         │── verifyOtp(session, otp)─▶│                     │
   │                         │                            │── Verify OTP ──────▶│
   │                         │                            │◀── eKYC Data ───────│
   │                         │                            │   (name, DOB, addr, │
   │                         │                            │    photo, gender)    │
   │                         │◀── KycCheckResult ────────│                     │
   │                         │                            │                     │
   │                         │── Store parsed_data        │                     │
   │                         │   (mask Aadhaar: XXXX-XXXX-1234)               │
   │◀── eKYC Complete ──────│                            │                     │
```

**Key Rules:**
- Aadhaar number stored as masked (last 4 digits only) per UIDAI guidelines
- Full eKYC response stored encrypted in `raw_response` JSONB
- Parsed data (name, DOB, address, gender) stored in `parsed_data` JSONB
- Photo from eKYC used for face match with selfie (if liveness step enabled)

#### FR-KYC-005: KYC Override Mechanism

- Credit managers (ROLE_CREDIT_MANAGER) can override any KYC step
- Override requires: justification text (minimum 20 characters)
- Override recorded: `override_by`, `override_reason`, timestamp
- Original result preserved — override creates new record with status=MANUAL_REVIEW→PASS
- Override audit trail visible in application timeline
- Maximum overrides per application configurable (default: 3)

---

### 2.4 Credit Bureau & Underwriting

#### FR-UW-001: Bureau Report Pull Flow

```
KYC Complete              LOS Core Service            Integration Router         Equifax API
     │                          │                            │                       │
     │── Status: UNDERWRITING ─▶│                            │                       │
     │                          │── pullBureauReport(appId) ▶│                       │
     │                          │                            │── routeBureauPull() ──▶│
     │                          │                            │                       │── Generate Report
     │                          │                            │◀── Credit Report ─────│
     │                          │                            │   (score, accounts,   │
     │                          │                            │    enquiries, DPD)    │
     │                          │◀── BureauReportDTO ────────│                       │
     │                          │                            │                       │
     │                          │── Parse & store:           │                       │
     │                          │   credit_score             │                       │
     │                          │   score_tier (A+/A/B/C/D)  │                       │
     │                          │   total_past_due           │                       │
     │                          │   DPD analysis             │                       │
     │                          │   account summaries        │                       │
     │                          │                            │                       │
     │                          │── Evaluate auto-approve    │                       │
     │                          │   threshold                │                       │
```

**Score Tier Mapping:**

| Score Range | Tier | Auto-Action |
|------------|------|-------------|
| 750+ | A+ | Auto-approve (if configured) |
| 700-749 | A | Auto-approve (if configured) |
| 650-699 | B | Manual review |
| 550-649 | C | Manual review with deviation |
| < 550 | D | Auto-reject (if configured) |

#### FR-UW-002: Credit Decision Engine

**Input Parameters for Decision:**
1. Credit score and tier
2. DPD history (max DPD in last 12/24 months)
3. FOIR calculation: (existing EMIs + proposed EMI) / monthly income
4. Total outstanding debt
5. Number of active loans
6. Recent enquiry count (last 6 months)
7. KYC confidence scores
8. Bank statement analysis (if AA data available)

**Decision Rules (Configurable):**

```json
{
  "auto_approve": {
    "min_credit_score": 750,
    "max_foir": 0.50,
    "max_dpd_12m": 0,
    "max_active_loans": 5,
    "max_recent_enquiries": 3
  },
  "auto_reject": {
    "max_credit_score": 500,
    "max_dpd_12m": 90,
    "settlement_in_last_24m": true
  },
  "deviation_matrix": {
    "credit_manager_max_deviation": "20%",
    "branch_head_max_deviation": "30%",
    "credit_head_max_deviation": "50%"
  }
}
```

#### FR-UW-003: FOIR Calculation

```
FOIR = (Sum of all existing EMIs + Proposed EMI) / Gross Monthly Income

If FOIR > 50%: Flag for manual review
If FOIR > 65%: Auto-reject (configurable threshold)
```

---

### 2.5 Account Aggregator Integration

#### FR-AA-001: Consent Flow

```
LOS System                 AA Client Library            Account Aggregator        FIP (Bank)
    │                            │                            │                       │
    │── Create consent ─────────▶│                            │                       │
    │   request (FI types,      │── POST /consent/request ──▶│                       │
    │    date range, purpose)   │                            │── Notify customer ───▶│
    │                            │◀── Consent handle ────────│   (via AA app)        │
    │                            │                            │                       │
    │  ... Customer approves     │                            │                       │
    │   on AA app ...            │                            │                       │
    │                            │                            │                       │
    │◀── Webhook: APPROVED ─────│◀── Consent status ────────│                       │
    │                            │                            │                       │
    │── Fetch FI data ──────────▶│── POST /fi/fetch ────────▶│                       │
    │                            │                            │── Fetch from FIP ────▶│
    │                            │                            │◀── Encrypted data ───│
    │                            │◀── FI data (encrypted) ───│                       │
    │                            │                            │                       │
    │── Decrypt & analyze ──────▶│                            │                       │
    │   (bank statements,       │                            │                       │
    │    GST returns, etc.)     │                            │                       │
```

**Supported FI Types:**
- Bank account statements (last 6/12 months)
- GST returns (GSTR-1, GSTR-3B)
- Income tax returns (ITR)
- Insurance policies
- Mutual fund holdings

---

### 2.6 eSign & Document Signing

#### FR-ESIGN-001: KFS Generation & Signing Flow

```
Credit Approved            LOS Core                    eSign Provider          Customer
     │                        │                             │                     │
     │── Approve loan ───────▶│                             │                     │
     │                        │── Generate KFS PDF          │                     │
     │                        │   (APR, total cost,         │                     │
     │                        │    cooling-off, grievance)  │                     │
     │                        │                             │                     │
     │                        │── routeESignRequest() ─────▶│                     │
     │                        │   {docType: KFS}            │── Upload PDF        │
     │                        │                             │── Create signing req │
     │                        │◀── {signingRequestId,      │                     │
     │                        │     signingUrl} ────────────│                     │
     │                        │                             │                     │
     │                        │── Send notification ────────│─────────────────────▶│
     │                        │   (SMS + WhatsApp)          │   signing link       │
     │                        │                             │                     │
     │                        │                             │    Customer opens    │
     │                        │                             │◀── link, views doc  │
     │                        │                             │    enters Aadhaar    │
     │                        │                             │    OTP              │
     │                        │                             │                     │
     │                        │◀── Webhook: SIGNED ────────│                     │
     │                        │   {signed_doc_url}          │                     │
     │                        │                             │                     │
     │                        │── Store signed PDF in S3    │                     │
     │                        │── Update esign_requests     │                     │
     │                        │── Start cooling-off timer   │                     │
     │                        │   (3 days per RBI)          │                     │
     │                        │                             │                     │
     │                        │  ... After 3 days ...       │                     │
     │                        │── Generate Loan Agreement   │                     │
     │                        │── Repeat eSign flow         │                     │
     │                        │   {docType: LOAN_AGREEMENT} │                     │
```

**KFS Contents (RBI Mandatory):**
1. Loan amount, tenure, interest rate
2. Annual Percentage Rate (APR) — inclusive of all fees
3. Total amount payable (principal + interest + fees)
4. EMI amount and schedule summary
5. Processing fee, prepayment charges, late payment charges
6. Cooling-off period: 3 business days from signing
7. Grievance redressal officer name, phone, email
8. Terms of early repayment/foreclosure

#### FR-ESIGN-002: Cooling-Off Period Implementation

- After KFS signing, system starts a 3-business-day cooling-off timer
- During cooling-off: customer can withdraw application (no charges)
- System blocks loan agreement generation until cooling-off expires
- If customer withdraws during cooling-off: application moves to WITHDRAWN status
- After cooling-off expires: system triggers loan agreement eSign

---

### 2.7 Disbursement & Transaction Management

#### FR-DISB-001: Disbursement Flow

```
Agreement Signed          LOS Core                   Bank/Payment Gateway
     │                       │                              │
     │── Status:             │                              │
     │   DISBURSEMENT_PENDING│                              │
     │                       │── Validate:                  │
     │                       │   - KFS signed               │
     │                       │   - Agreement signed          │
     │                       │   - Cooling-off expired       │
     │                       │   - Bank account verified     │
     │                       │   - NACH mandate (if req)     │
     │                       │                              │
     │                       │── Create disbursement record │
     │                       │── triggerDisbursement() ─────▶│
     │                       │   {borrower_account,         │── NEFT/RTGS/IMPS
     │                       │    amount, purpose}          │
     │                       │                              │
     │                       │◀── {UTR, status} ────────────│
     │                       │                              │
     │                       │── Create transaction record  │
     │                       │   txn_type=DISBURSEMENT      │
     │                       │   reference_no=UTR           │
     │                       │── Update status: DISBURSED   │
     │                       │── Notify customer            │
     │                       │── Create loan account in LMS │
```

**Pre-Disbursement Checklist (System-Enforced):**
1. KFS signed and cooling-off period expired
2. Loan agreement signed
3. Bank account verified (penny drop)
4. NACH mandate registered (for EMI auto-debit)
5. All mandatory documents uploaded
6. No pending KYC overrides without approval
7. Credit decision recorded (APPROVED)
8. Sanction letter generated

---

### 2.8 Notification Service

#### FR-NOTIF-001: Event-Driven Notification Architecture

```
LOS Core / Any Service          RabbitMQ                   Notification Service
       │                           │                              │
       │── Publish event ─────────▶│                              │
       │   {type: OTP_SENT,       │   Queue: notification.sms    │
       │    channel: SMS,          │──────────────────────────────▶│
       │    template: otp_verify,  │                              │── Load template
       │    vars: {otp, name}}     │                              │── Replace variables
       │                           │                              │── Send via SMS provider
       │                           │                              │
       │                           │   If failed:                 │
       │                           │   Queue: notification.dlq    │
       │                           │◀──────────────────────────────│
       │                           │   (retry after 5 min,        │
       │                           │    max 3 retries)            │
```

**Notification Channels:**

| Channel | Provider | Use Cases |
|---------|----------|-----------|
| SMS | Configurable (MSG91, Twilio, etc.) | OTP, status updates, payment reminders |
| Email | SMTP / SendGrid | Detailed notifications, documents, reports |
| WhatsApp | WhatsApp Business API | Customer communication, document links |
| In-App | WebSocket push | Dashboard notifications for credit officers |

**Template Variables:**
- `{{customer_name}}` — Borrower name
- `{{application_ref}}` — Application reference number
- `{{loan_amount}}` — Sanctioned amount
- `{{emi_amount}}` — Monthly EMI
- `{{otp}}` — One-time password
- `{{signing_url}}` — eSign link
- `{{status}}` — Application status
- `{{due_date}}` — Next payment due date

---

### 2.9 Lender Dashboard (Credit Officer Portal)

#### FR-DASH-001: Pipeline Kanban View

**Layout:** Horizontal scrollable board with columns for each stage

| Column | Color Scheme | Content |
|--------|-------------|---------|
| Lead Capture | Slate | New leads not yet submitted |
| Consent | Blue | Awaiting customer consent |
| KYC | Violet | KYC in progress |
| Docs Upload | Yellow | Document collection |
| Data Fetch | Orange | AA/Bureau data fetch |
| Underwriting | Cyan | Under credit evaluation |
| Decision | Pink | Pending credit decision |
| Sanction | Emerald | Approved, sanction issued |
| Disbursed | Green | Funds disbursed |

**Card Information:**
- Borrower type icon and badge
- Application ID (truncated)
- Loan amount (formatted: ₹5L, ₹50L, ₹1.2Cr)
- Loan purpose
- Time since creation (e.g., "2h ago", "3d ago")
- Tenure in months

**Summary Cards (Top Bar):**
- Total Applications count
- Count by borrower type (Individual, Proprietor, Partnership, Company)
- In Decision count
- Sanctioned count

#### FR-DASH-002: Application Detail View

**Sections:**
1. **Header**: Application ref, status badge, borrower type, loan amount, created date
2. **Timeline**: Chronological log of all actions (status changes, KYC results, comments)
3. **KYC Status Panel**: Each KYC step with status (PASS/FAIL/PENDING), provider, confidence score
4. **Documents Panel**: Uploaded documents with download links, missing document alerts
5. **Bureau Report Panel**: Credit score, tier, DPD summary, account details
6. **Financial Summary**: FOIR calculation, income vs obligations
7. **Decision Panel**: Approve/Reject buttons, remarks field, deviation request
8. **eSign Status**: KFS and Agreement signing status with timestamps
9. **Notes**: Internal notes and comments between credit officers

#### FR-DASH-003: Admin Panels

**Workflow Configuration Admin:**
- List all workflow configs
- Create/edit workflow with drag-drop step ordering
- Configure per-step: provider, mandatory/optional, pass threshold, on-fail action
- Activate/deactivate workflow versions
- Preview workflow for a borrower type

**Aggregator Configuration Admin:**
- List all aggregator configs
- Add/edit provider credentials (encrypted)
- Test connectivity with sandbox credentials
- Toggle environment (SANDBOX/PRODUCTION)
- View API call logs and success rates

**User Management:**
- CRUD users
- Assign roles (ADMIN, CREDIT_MANAGER, CREDIT_OFFICER, OPERATIONS, VIEWER)
- Configure TOTP 2FA
- View login history and active sessions

---

### 2.10 Customer Portal (Mobile-First)

#### FR-CUST-001: Customer Journey Flow

```
┌──────────────┐    ┌─────────────┐    ┌──────────────┐    ┌────────────┐
│  OTP Login   │───▶│  Dashboard  │───▶│ Apply for    │───▶│  Track     │
│  (Mobile)    │    │  (My Loans) │    │ New Loan     │    │  Status    │
└──────────────┘    └─────────────┘    └──────────────┘    └────────────┘
                                             │
                                    ┌────────┴────────┐
                                    ▼                 ▼
                            ┌──────────────┐  ┌──────────────┐
                            │ Guided KYC   │  │ Document     │
                            │ Steps        │  │ Upload       │
                            └──────────────┘  └──────────────┘
                                    │
                                    ▼
                            ┌──────────────┐    ┌──────────────┐
                            │ eSign (KFS + │───▶│ Post-Disburse│
                            │  Agreement)  │    │ (EMI, Repay) │
                            └──────────────┘    └──────────────┘
```

**Key Features:**
- Progressive web app (PWA) for mobile-first experience
- Step indicator showing current position in application journey
- Camera integration for selfie capture (liveness check)
- Document upload with camera capture option
- Real-time application status with push notifications
- eSign redirect: customer clicks signing link → provider UI → Aadhaar OTP → return to portal
- Post-disbursement: EMI schedule, repayment history, outstanding balance

---

### 2.11 API Gateway & Security

#### FR-GW-001: API Gateway Routing

| Route Pattern | Target Service | Auth Required |
|--------------|---------------|---------------|
| `/api/v1/auth/**` | IAM Service | No (login/register) |
| `/api/v1/enrollment/**` | Enrollment Service | Partial (consent links public) |
| `/api/v1/applications/**` | LOS Core Service | Yes (JWT) |
| `/api/v1/kyc/**` | LOS Core Service | Yes (JWT) |
| `/api/v1/workflow/**` | LOS Core Service | Yes (JWT + ADMIN role) |
| `/api/v1/transactions/**` | LOS Core Service | Yes (JWT) |
| `/api/v1/notifications/**` | Notification Service | Yes (JWT) |
| `/api/v1/lms/**` | LMS Adapter Service | Yes (JWT) |
| `/api/v1/admin/**` | Various | Yes (JWT + ADMIN role) |
| `/webhook/**` | LOS Core Service | Webhook signature validation |

#### FR-GW-002: Rate Limiting

| Endpoint Category | Rate Limit | Window |
|-------------------|-----------|--------|
| Public APIs (login, register) | 20 req/min per IP | Sliding window |
| Authenticated APIs | 100 req/min per user | Sliding window |
| Admin APIs | 200 req/min per user | Sliding window |
| Webhook endpoints | 500 req/min per provider | Sliding window |
| File upload | 10 req/min per user | Sliding window |

#### FR-GW-003: JWT Validation Flow

```
Client                    API Gateway                 IAM Service
  │                          │                            │
  │── Request + JWT ────────▶│                            │
  │                          │── Validate JWT locally     │
  │                          │   (signature, expiry)      │
  │                          │                            │
  │                          │── If valid: extract claims │
  │                          │   (userId, roles, tenantId)│
  │                          │── Forward to target service│
  │                          │   with X-User-Id,          │
  │                          │   X-User-Roles headers     │
  │                          │                            │
  │                          │── If expired: return 401   │
  │◀── Response ─────────────│                            │
```

---

### 2.12 Reporting & Analytics

#### FR-RPT-001: Standard Reports

| Report | Frequency | Format | Description |
|--------|-----------|--------|-------------|
| Application Pipeline | Daily | PDF/Excel | Count by stage, TAT per stage, bottleneck analysis |
| Disbursement Report | Daily | Excel | All disbursements with UTR, amounts, borrower details |
| KYC Success Rate | Weekly | PDF | Pass/fail rates by KYC step type and provider |
| Collection Report | Daily | Excel | Repayments received, overdue amounts, DPD aging |
| Portfolio Summary | Monthly | PDF | Total AUM, borrower count, NPA ratio, PAR30/60/90 |
| Regulatory Return | Monthly | Excel | RBI-prescribed format for digital lending returns |
| Channel Performance | Monthly | PDF | Applications and conversions by DSA/channel |
| User Activity | Weekly | Excel | Login frequency, applications processed per RM |

#### FR-RPT-002: Executive Dashboard

**Widgets:**
1. **Portfolio AUM**: Total outstanding across all active loans (line chart, monthly trend)
2. **Disbursement Trend**: Monthly disbursement volume and count (bar chart)
3. **Application Funnel**: Conversion rates at each stage (funnel chart)
4. **TAT Analysis**: Average processing time by stage (heatmap)
5. **Credit Score Distribution**: Pie chart of loan book by score tier
6. **NPA Tracker**: NPA ratio trend with early warning indicators
7. **Top RMs**: Leaderboard by applications processed / disbursed
8. **Product Mix**: Loan amount distribution by product type

---

## 3. Data Model Summary

### 3.1 Core Tables (from Design Doc + Extensions)

| Table | Purpose | Key Relationships |
|-------|---------|-------------------|
| `users` | System users (RMs, credit officers, admins) | roles, permissions |
| `customers` | Registered borrowers | loan_applications |
| `loan_applications` | Master application record | customers, workflow_configs |
| `kyc_step_results` | Per-step KYC verification results | loan_applications |
| `workflow_configs` | Configurable workflow definitions | loan_applications |
| `workflow_executions` | Runtime workflow state per application | loan_applications, workflow_configs |
| `esign_requests` | eSign/digital signature records | loan_applications |
| `aggregator_configs` | Provider API credentials (encrypted) | — |
| `transactions` | Disbursement & payment ledger | loan_applications |
| `documents` | Uploaded document metadata + S3 URLs | loan_applications |
| `notifications` | Notification log with delivery status | customers |
| `audit_logs` | Complete audit trail of all actions | users, loan_applications |
| `consent_artefacts` | Consent records per RBI guidelines | customers |
| `nach_mandates` | NACH auto-debit mandate records | loan_applications |
| `application_notes` | Internal notes/comments on applications | loan_applications, users |
| `aa_consent_requests` | Account Aggregator consent records | loan_applications |
| `credit_reports` | Bureau report data (parsed + raw) | loan_applications |
| `report_templates` | Configurable report definitions | — |

### 3.2 Key JSONB Usage

| Table | JSONB Column | Content |
|-------|-------------|---------|
| `loan_applications` | `extra_data` | Borrower-type-specific fields (partner details, director details, etc.) |
| `kyc_step_results` | `raw_response` | Full provider API response (immutable) |
| `kyc_step_results` | `parsed_data` | Extracted KYC data (name, DOB, address) |
| `workflow_configs` | `steps` | Ordered array of step configuration objects |
| `aggregator_configs` | `extra_config` | Provider-specific additional parameters |
| `transactions` | `meta` | Transaction-specific metadata |
| `credit_reports` | `report_data` | Parsed bureau report data |

---

## 4. Integration Points Summary

| Integration | Direction | Protocol | Auth Method |
|------------|-----------|----------|-------------|
| Authbridge KYC | Outbound | REST API | OAuth2 Client Credentials |
| Perfios KYC/AA | Outbound | REST API | API Key + HMAC |
| Equifax Bureau | Outbound | REST API | OAuth2 |
| Hyperverge Video KYC | Outbound | REST API | API Key |
| emsigner eSign | Outbound | REST API | API Key + HMAC |
| Authbridge eSign | Outbound | REST API | OAuth2 |
| Account Aggregator | Outbound | REST API | JWS (JSON Web Signature) |
| CKYC Registry | Outbound | REST API | Certificate-based |
| SMS Gateway | Outbound | REST API | API Key |
| WhatsApp Business | Outbound | REST API | Bearer Token |
| Email (SMTP) | Outbound | SMTP | Username/Password |
| LMS (Loan Management) | Bidirectional | REST API + Webhook | API Key |
| Bank (Disbursement) | Outbound | REST API / H2H File | Certificate-based |
| Payment Gateway | Outbound | REST API | API Key + Secret |
| All Webhook Receivers | Inbound | REST POST | Signature validation |

---

## 5. Error Handling & Resilience

### 5.1 API Error Response Format

```json
{
  "timestamp": "2026-04-13T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "LOS_APP_001",
  "message": "Invalid application status transition: DRAFT cannot transition to APPROVED",
  "path": "/api/v1/applications/{id}/status",
  "traceId": "abc123-def456"
}
```

### 5.2 Retry Strategy for External APIs

| Provider Category | Retry Count | Backoff | Timeout |
|-------------------|------------|---------|---------|
| KYC Providers | 3 | Exponential (1s, 2s, 4s) | 30s |
| Bureau APIs | 2 | Exponential (2s, 4s) | 45s |
| eSign Providers | 3 | Exponential (1s, 2s, 4s) | 30s |
| SMS/Email | 3 (via DLQ) | Fixed (5 min) | 10s |
| Bank/Payment | 1 (no auto-retry for financial txns) | — | 60s |

### 5.3 Circuit Breaker Configuration

- **Failure threshold**: 5 consecutive failures
- **Open duration**: 30 seconds
- **Half-open**: Allow 1 request to test recovery
- **Monitored per**: Provider + step type combination
- **Alert**: Notification to admin when circuit opens

---

## 6. Acceptance Criteria Summary

| Module | Key Acceptance Criteria |
|--------|----------------------|
| Registration | Customer can register via mobile OTP in < 60 seconds |
| Application | Full application wizard completes in < 10 minutes for Individual |
| KYC | All configured KYC steps execute automatically after submission |
| Bureau | Credit report pulled and scored within 30 seconds |
| Decision | Auto-approve/reject fires within 5 seconds of bureau completion |
| eSign | KFS sent for signing within 2 minutes of approval |
| Disbursement | Funds disbursed to borrower account same day (subject to bank processing) |
| Dashboard | Pipeline loads in < 2 seconds with 1000+ applications |
| Notifications | OTP delivered within 5 seconds; status notifications within 30 seconds |
| Reports | Standard reports generate in < 30 seconds |
