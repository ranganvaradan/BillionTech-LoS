# LOS v2.0 — Detailed Business Requirements Document (BRD)

## Document Control

| Field | Value |
|-------|-------|
| **Version** | 2.0 |
| **Date** | April 2026 |
| **Classification** | CONFIDENTIAL — Lender Internal Use Only |
| **Based On** | LOS_Design_v2.docx, bl-core-backend analysis, existing-los-ui analysis, Latest-LOS-co-dashboard reference, industry research |

---

## 1. Executive Summary

### 1.1 Background
The current Loan Origination System (bl-core) is a monolithic Java application with 1,530+ source files, 216 domain entities, and an Angular-based onboarding UI. While functionally rich — covering borrower onboarding, KYC (Authbridge, Equifax, Hyperverge), eSign (emsigner), loan applications, disbursements, repayments, invoice discounting, and cash discounts — the system has accumulated significant technical debt and lacks modern UX, API-first architecture, and regulatory adaptability.

### 1.2 Vision
Build a **state-of-the-art, API-first Loan Origination System** that:
- Digitizes the end-to-end lending lifecycle from lead capture to disbursement
- Supports multiple borrower types (Individual, Proprietor, Partnership, Company)
- Is fully compliant with RBI Digital Lending Directions 2025, DPDP Act 2023, and Account Aggregator framework
- Provides a modern, intuitive dashboard (Kanban pipeline view) for credit officers and a mobile-first customer portal
- Is modular, configurable, and self-hosted on lender infrastructure

### 1.3 Scope
This BRD covers the complete modernization of the Loan Origination System including:
- All existing capabilities from bl-core-backend (retained and improved)
- New modules identified through industry research and regulatory analysis
- UI/UX modernization based on the reference dashboard design
- Infrastructure and DevOps modernization

---

## 2. Business Objectives

| # | Objective | Success Metric |
|---|-----------|---------------|
| BO-1 | **Reduce loan processing time** from application to disbursement | Target: < 48 hours for pre-approved segments; < 5 days for standard |
| BO-2 | **Achieve 100% digital KYC** for all borrower types | Zero manual document collection for standard flows |
| BO-3 | **Full RBI compliance** with Digital Lending Directions 2025 | Pass regulatory audit with zero non-conformances |
| BO-4 | **Improve customer experience** with mobile-first, guided journey | NPS > 70; application abandonment rate < 20% |
| BO-5 | **Enable configurable workflows** for different loan products and borrower types | Credit team can create new workflows without code changes |
| BO-6 | **Real-time pipeline visibility** for management | Dashboard with live Kanban view, TAT tracking, bottleneck alerts |
| BO-7 | **Self-hosted, air-gap ready** deployment | Full system runs on lender infrastructure with no external SaaS dependencies |
| BO-8 | **API-first architecture** for partner/channel integrations | All functionality accessible via documented REST APIs |
| BO-9 | **Comprehensive audit trail** for every action | 100% traceability for regulatory and internal audit |
| BO-10 | **Scalable to 500+ concurrent users** with horizontal scaling | < 2s API response time at peak load |

---

## 3. Stakeholder Analysis

| Stakeholder | Role | Key Needs |
|------------|------|-----------|
| **Credit Officers / RMs** | Process loan applications, manage pipeline | Fast application review, KYC results at a glance, one-click approvals, document management |
| **Credit Managers** | Approve/reject applications, configure policies | Override capabilities, policy configuration, portfolio analytics |
| **Compliance Team** | Ensure regulatory adherence | Audit trails, KFS generation, consent management, regulatory reports |
| **Operations Team** | Manage disbursements, repayments, collections | Transaction management, NACH setup, LMS integration |
| **IT/DevOps** | Deploy, maintain, monitor the system | Docker-based deployment, CI/CD pipelines, monitoring dashboards |
| **Customers (Borrowers)** | Apply for loans, complete KYC, sign documents | Simple guided journey, mobile-friendly, real-time status updates |
| **Channel Partners / DSAs** | Source leads, track conversions | API access, lead management portal, commission tracking |
| **Management / CXOs** | Strategic oversight | Executive dashboards, MIS reports, portfolio health metrics |

---

## 4. Business Requirements

### BR-1: Customer Onboarding & Registration

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-1.1 | Support customer registration via mobile OTP verification | Must Have | Existing (bl-core) |
| BR-1.2 | Capture consent for data processing, KYC, and bureau checks as per RBI guidelines | Must Have | RBI DLD 2025 |
| BR-1.3 | Generate and store consent artefacts with timestamp, IP, device ID, and OTP session ID | Must Have | RBI DLD 2025 |
| BR-1.4 | Support self-registration (customer portal) and assisted registration (RM-initiated) | Must Have | Existing |
| BR-1.5 | Email OTP verification for secondary contact validation | Should Have | Dashboard ref |
| BR-1.6 | Deduplicate customers across applications using PAN/Aadhaar/mobile | Must Have | New |
| BR-1.7 | Support DigiLocker integration for document pull | Should Have | Existing (bl-core) |

### BR-2: Loan Application Management

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-2.1 | Multi-step guided application wizard with borrower type selection | Must Have | Dashboard ref |
| BR-2.2 | Support 4 borrower types: Individual, Proprietor, Partnership, Company | Must Have | Design doc |
| BR-2.3 | Support loan products: Personal Loan, Term Loan, Business Loan, Invoice Discounting, MSME Loan, Working Capital, Home Loan, Vehicle Loan, Overdraft, Mortgage | Must Have | bl-core + Dashboard ref |
| BR-2.4 | Auto-generate unique application reference numbers (e.g., LOS-2026-00001) | Must Have | Design doc |
| BR-2.5 | Application state machine: DRAFT → PENDING_APPROVAL → KYC_IN_PROGRESS → UNDERWRITING → APPROVED/REJECTED → SANCTION_ISSUED → DISBURSED | Must Have | Design doc |
| BR-2.6 | Document upload against application with type classification (PAN card, Aadhaar, financials, etc.) | Must Have | Existing |
| BR-2.7 | Request additional documents from customer with notification | Must Have | Design doc |
| BR-2.8 | Application-level notes and comments for internal communication | Should Have | New |
| BR-2.9 | Application cloning/renewal for repeat borrowers | Should Have | Existing (bl-core) |
| BR-2.10 | Bulk application processing for anchor-led programs | Should Have | Existing (bl-core) |

### BR-3: KYC & Identity Verification

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-3.1 | Aadhaar OTP-based eKYC via configurable provider (Authbridge / Perfios) | Must Have | Existing + Design doc |
| BR-3.2 | PAN verification and name-matching | Must Have | Dashboard ref |
| BR-3.3 | GSTIN verification for business borrowers | Must Have | Existing |
| BR-3.4 | Voter ID verification | Should Have | Dashboard ref |
| BR-3.5 | Driving License verification | Should Have | Dashboard ref |
| BR-3.6 | Bank account verification via penny drop | Must Have | Dashboard ref |
| BR-3.7 | Video KYC (V-CIP) via Hyperverge for RBI-compliant remote verification | Should Have | Existing |
| BR-3.8 | Liveness detection with face match (selfie vs Aadhaar photo) | Should Have | Existing + Dashboard ref |
| BR-3.9 | CKYC (Central KYC) upload and download | Must Have | Existing (bl-core) |
| BR-3.10 | Udyam registration verification for MSMEs | Should Have | Dashboard ref |
| BR-3.11 | CIN/MCA21 verification for companies | Should Have | Existing |
| BR-3.12 | AML (Anti-Money Laundering) screening | Must Have | Existing (bl-core) |
| BR-3.13 | KYC override mechanism with credit manager approval and audit trail | Must Have | Design doc |
| BR-3.14 | Configurable KYC workflow per borrower type and loan product | Must Have | Design doc |

### BR-4: Credit Bureau & Underwriting

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-4.1 | Equifax credit report pull — consumer and commercial | Must Have | Existing + Design doc |
| BR-4.2 | Credit score extraction with tier classification (A+/A/B/C/D) | Must Have | Existing |
| BR-4.3 | DPD (Days Past Due) analysis from bureau data | Must Have | Existing |
| BR-4.4 | Auto-approve for applications meeting configurable score thresholds | Must Have | Design doc |
| BR-4.5 | Bank statement analysis for income verification (via Perfios / Account Aggregator) | Must Have | Existing |
| BR-4.6 | Configurable credit policy rules engine | Must Have | New |
| BR-4.7 | Deviation matrix — allow credit managers to approve deviations with limits | Should Have | New |
| BR-4.8 | FOIR (Fixed Obligation to Income Ratio) calculation | Must Have | New |
| BR-4.9 | Collateral valuation module for secured loans | Should Have | New |
| BR-4.10 | Multi-bureau support (Equifax, CIBIL, Experian, CRIF High Mark) | Could Have | New |

### BR-5: Account Aggregator (AA) Integration — NEW

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-5.1 | Integrate with RBI-licensed Account Aggregators for consent-based financial data fetch | Must Have | RBI AA Directions 2025 |
| BR-5.2 | Support consent request generation with configurable FI types (bank statements, GST returns, tax documents) | Must Have | New |
| BR-5.3 | Real-time data fetch upon customer consent approval | Must Have | New |
| BR-5.4 | Automated bank statement analysis from AA data | Should Have | New |
| BR-5.5 | Consent lifecycle management (create, revoke, expire) | Must Have | New |

### BR-6: Workflow Engine

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-6.1 | Configurable workflow per borrower type and loan product | Must Have | Design doc |
| BR-6.2 | Sequential and parallel step execution support | Must Have | Design doc |
| BR-6.3 | Configurable pass/fail actions per step (BLOCK, MANUAL_REVIEW, SKIP) | Must Have | Design doc |
| BR-6.4 | Admin UI for workflow creation and modification (drag-drop) | Must Have | Design doc |
| BR-6.5 | Workflow versioning — active/inactive workflow support | Should Have | Design doc |
| BR-6.6 | SLA/TAT tracking per workflow step with escalation alerts | Must Have | New |
| BR-6.7 | Conditional branching based on application data (e.g., skip GSTIN step for individuals) | Must Have | New |

### BR-7: eSign / Digital Signature

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-7.1 | Aadhaar OTP-based eSign for KFS (Key Fact Statement) | Must Have | Design doc + RBI |
| BR-7.2 | Aadhaar OTP-based eSign for Loan Agreement | Must Have | Design doc |
| BR-7.3 | Digital signature for Sanction Letter (lender side) | Must Have | Design doc |
| BR-7.4 | eSign for NACH Mandate Form | Should Have | Design doc |
| BR-7.5 | Pluggable eSign providers: emsigner (MCA empanelled) + Authbridge eSign | Must Have | Design doc |
| BR-7.6 | Signed document storage in S3 with download capability | Must Have | Design doc |
| BR-7.7 | eSign status tracking and webhook-based status updates | Must Have | Design doc |

### BR-8: Key Fact Statement (KFS) — RBI Mandatory

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-8.1 | Auto-generate KFS PDF from loan terms including APR, total cost, cooling-off period, grievance mechanism | Must Have | RBI DLD 2025 |
| BR-8.2 | Mandatory KFS acknowledgement before loan agreement signing | Must Have | RBI DLD 2025 |
| BR-8.3 | 3-day cooling-off period implementation post KFS signing | Must Have | RBI DLD 2025 |
| BR-8.4 | KFS template management and versioning | Must Have | New |

### BR-9: Disbursement & Transaction Management

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-9.1 | Disbursement trigger with ledger entry creation | Must Have | Design doc |
| BR-9.2 | Direct disbursal to borrower's bank account (RBI mandate — no pool account routing) | Must Have | RBI DLD 2025 |
| BR-9.3 | Repayment event recording from LMS webhook | Must Have | Design doc |
| BR-9.4 | NACH mandate creation and management | Must Have | Design doc |
| BR-9.5 | Transaction history with audit trail | Must Have | Design doc |
| BR-9.6 | Outstanding balance tracking with LMS sync | Must Have | Design doc |
| BR-9.7 | Multi-tranche disbursement for large loans | Should Have | Existing (bl-core) |
| BR-9.8 | Co-lending disbursement split management | Should Have | Existing (bl-core) |

### BR-10: Notification & Communication

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-10.1 | SMS notifications for OTP, status updates, reminders | Must Have | Design doc |
| BR-10.2 | Email notifications with branded templates | Must Have | Design doc |
| BR-10.3 | WhatsApp Business API integration for customer communication | Must Have | Design doc + Existing |
| BR-10.4 | In-app notifications for lender dashboard users | Should Have | New |
| BR-10.5 | Configurable notification templates with variable substitution | Must Have | Design doc |
| BR-10.6 | Delivery tracking and DLQ (Dead Letter Queue) retry | Must Have | Design doc |
| BR-10.7 | Bulk SMS for overdue reminders | Should Have | Existing (bl-core) |

### BR-11: LMS (Loan Management System) Integration

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-11.1 | Loan account creation in external LMS post-disbursement | Must Have | Design doc |
| BR-11.2 | EMI schedule fetch and display | Must Have | Design doc |
| BR-11.3 | Repayment status synchronization via webhook | Must Have | Design doc |
| BR-11.4 | NPA (Non-Performing Asset) flag handling | Must Have | Design doc |
| BR-11.5 | Prepayment and foreclosure processing | Should Have | Existing |

### BR-12: Lender Dashboard (Credit Officer Portal)

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-12.1 | Kanban-style pipeline view with all application stages | Must Have | Dashboard ref |
| BR-12.2 | Application detail view with KYC status, documents, timeline | Must Have | Dashboard ref |
| BR-12.3 | Real-time summary cards (total applications, by type, by stage) | Must Have | Dashboard ref |
| BR-12.4 | Application search and filtering (by status, RM, borrower type, date range) | Must Have | Design doc |
| BR-12.5 | Workflow configuration admin panel | Must Have | Design doc |
| BR-12.6 | Aggregator configuration admin panel (API credentials, provider selection) | Must Have | Design doc |
| BR-12.7 | User management with RBAC (roles, permissions) | Must Have | Design doc |
| BR-12.8 | Executive dashboard with portfolio analytics | Should Have | New |
| BR-12.9 | TAT (Turn Around Time) tracking and bottleneck identification | Must Have | New |
| BR-12.10 | Dark mode support | Could Have | New |

### BR-13: Customer Portal (Mobile-First)

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-13.1 | Mobile-first responsive design | Must Have | Design doc |
| BR-13.2 | OTP-based login (no password) | Must Have | Design doc |
| BR-13.3 | Guided KYC step-by-step journey with camera integration | Must Have | Dashboard ref |
| BR-13.4 | Document upload with camera capture | Must Have | Dashboard ref |
| BR-13.5 | eSign step with redirect to provider and return confirmation | Must Have | Design doc |
| BR-13.6 | Loan account view — EMI schedule, repayment history | Must Have | Design doc |
| BR-13.7 | Real-time application status tracking | Must Have | New |
| BR-13.8 | Push notification support | Should Have | New |

### BR-14: IAM (Identity & Access Management)

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-14.1 | JWT-based authentication with 15-min access tokens | Must Have | Design doc |
| BR-14.2 | Refresh token (7 days) with HttpOnly cookie | Must Have | Design doc |
| BR-14.3 | TOTP 2FA for admin and credit officer accounts | Must Have | Design doc |
| BR-14.4 | Role-Based Access Control (RBAC) with hierarchical permissions | Must Have | Design doc |
| BR-14.5 | Session management and concurrent login control | Should Have | New |
| BR-14.6 | Password policy enforcement (complexity, expiry, history) | Must Have | New |
| BR-14.7 | API key management for partner/channel integrations | Should Have | New |

### BR-15: Reporting & Analytics — NEW

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-15.1 | Loan application MIS reports (daily, weekly, monthly) | Must Have | New |
| BR-15.2 | KYC success/failure rate analytics | Should Have | New |
| BR-15.3 | Disbursement and collection reports | Must Have | New |
| BR-15.4 | Regulatory reports (RBI returns, CERSAI submissions) | Must Have | New |
| BR-15.5 | Configurable report builder with export (PDF, Excel, CSV) | Should Have | New |
| BR-15.6 | Portfolio health dashboard (PAR, NPA ratios) | Must Have | New |
| BR-15.7 | Channel/DSA performance reports | Should Have | New |

### BR-16: Document Management — NEW

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-16.1 | Centralized document repository with S3 storage | Must Have | Design doc |
| BR-16.2 | Document classification and tagging | Must Have | New |
| BR-16.3 | OCR-based auto-extraction from uploaded documents (PAN, Aadhaar, bank statements) | Should Have | New |
| BR-16.4 | Document versioning and audit trail | Must Have | New |
| BR-16.5 | Configurable document checklist per loan product and borrower type | Must Have | New |
| BR-16.6 | Automated document completeness check before stage transition | Must Have | New |

### BR-17: Co-Lending Module — NEW (from existing capability)

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-17.1 | Co-lending partner management and limit configuration | Should Have | Existing (bl-core) |
| BR-17.2 | Automatic apportionment calculation (lender share vs co-lender share) | Should Have | Existing (bl-core) |
| BR-17.3 | Co-lender disbursement orchestration | Should Have | Existing (bl-core) |
| BR-17.4 | Co-lending settlement and reconciliation | Should Have | Existing (bl-core) |
| BR-17.5 | Co-lending MIS and reporting | Should Have | Existing (bl-core) |

### BR-18: API Platform & Partner Integration — NEW

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-18.1 | OpenAPI 3.1 specification for all APIs | Must Have | New |
| BR-18.2 | API versioning (URL-based: /api/v1/, /api/v2/) | Must Have | New |
| BR-18.3 | Rate limiting per API key/partner | Must Have | Design doc |
| BR-18.4 | Webhook registration for partners (application status changes, disbursement events) | Should Have | New |
| BR-18.5 | API sandbox environment for partner onboarding | Should Have | New |
| BR-18.6 | DSA/Channel partner API for lead submission and tracking | Should Have | New |

### BR-19: Security & Data Protection

| Req ID | Requirement | Priority | Source |
|--------|------------|----------|--------|
| BR-19.1 | AES-256 encryption for all stored credentials (aggregator API keys, etc.) | Must Have | Design doc |
| BR-19.2 | TLS 1.3 for all API communication | Must Have | New |
| BR-19.3 | Aadhaar data masking in logs and UI (show last 4 digits only) | Must Have | RBI + UIDAI |
| BR-19.4 | PII data encryption at rest | Must Have | DPDP Act 2023 |
| BR-19.5 | Data retention and purging policies | Must Have | DPDP Act 2023 |
| BR-19.6 | OWASP Top 10 compliance | Must Have | Design doc |
| BR-19.7 | SQL injection, XSS, CSRF protection | Must Have | New |
| BR-19.8 | Comprehensive API audit logging | Must Have | New |
| BR-19.9 | IP whitelisting for admin APIs | Should Have | New |
| BR-19.10 | Data localization — all data stored within India | Must Have | DPDP Act 2023 |

---

## 5. Non-Functional Requirements

| Category | Requirement | Target |
|----------|------------|--------|
| **Performance** | API response time (95th percentile) | < 500ms |
| **Performance** | Dashboard page load time | < 2 seconds |
| **Performance** | Concurrent users | 500+ |
| **Availability** | System uptime | 99.5% |
| **Scalability** | Horizontal scaling for LOS Core Service | Auto-scale via Docker Compose / K8s |
| **Disaster Recovery** | RPO (Recovery Point Objective) | < 1 hour |
| **Disaster Recovery** | RTO (Recovery Time Objective) | < 4 hours |
| **Data Backup** | PostgreSQL daily backup to S3/NFS | Automated |
| **Security** | Penetration testing | Annual |
| **Compliance** | RBI IT Governance Master Direction | Full compliance |
| **Deployment** | Self-hosted, air-gap capable | Docker Compose on lender servers |
| **Monitoring** | Application and infrastructure monitoring | Prometheus + Grafana |
| **Logging** | Centralized structured logging | ELK Stack or Loki |

---

## 6. Regulatory Compliance Requirements

### 6.1 RBI Digital Lending Directions 2025
- **KFS Mandatory**: Generate and display Key Fact Statement before loan signing
- **Direct Disbursal**: All disbursals must go directly to borrower's bank account
- **Cooling-Off Period**: 3-day cooling-off period post KFS acknowledgement
- **Transparent Pricing**: APR display including all fees and charges
- **Grievance Redressal**: Built-in grievance mechanism with SLA tracking
- **LSP Disclosure**: Clear disclosure of all Lending Service Providers involved
- **Data Storage**: Loan data to be stored only with the Regulated Entity

### 6.2 DPDP Act 2023 (Digital Personal Data Protection)
- **Consent-based processing**: Explicit consent for all personal data collection
- **Purpose limitation**: Data collected only for stated purposes
- **Data minimization**: Collect only necessary data
- **Right to erasure**: Customer can request data deletion
- **Data localization**: All personal data stored within India
- **Breach notification**: Notify regulator and data principals within prescribed timelines

### 6.3 Account Aggregator Framework (RBI NBFC-AA Directions 2025)
- **Consent Architecture**: Standard consent artefact as per AA specification
- **Real-time Data Sharing**: FI data fetch upon consent approval
- **No Data Storage by AA**: System must fetch and process, not store AA-sourced raw data beyond processing window

### 6.4 Other Compliance
- **UIDAI Guidelines**: Aadhaar data handling, masking, and storage
- **CERSAI**: Registration of security interest for secured loans
- **PMLA/AML**: Anti-money laundering screening
- **IT Act 2000 Section 3A**: Legal validity of Aadhaar OTP-based eSign

---

## 7. Assumptions & Constraints

### 7.1 Assumptions
1. Lender has existing agreements with KYC aggregators (Authbridge, Perfios, Hyperverge)
2. Lender has existing eSign provider agreement (emsigner and/or Authbridge eSign)
3. Lender has or will set up an Account Aggregator partnership
4. Lender has an existing or planned LMS for post-disbursement servicing
5. Target deployment is on-premise or private cloud within India
6. Internet connectivity is available for aggregator API calls

### 7.2 Constraints
1. Must be deployable on lender-owned infrastructure (no mandatory public cloud dependency)
2. All data must reside within India (data localization)
3. Must support existing aggregator contracts — no vendor lock-in
4. Must maintain backward compatibility with existing data where migration is needed
5. Budget and timeline as estimated in implementation plan

---

## 8. Out of Scope (Phase 1)

| Item | Rationale |
|------|-----------|
| Mobile native app (iOS/Android) | Customer portal is mobile-first web; native app planned for Phase 2 |
| AI/ML-based credit scoring | Rule-based engine in Phase 1; ML models in Phase 2 |
| Chatbot / AI assistant | Phase 2 enhancement |
| Multi-language support | English only in Phase 1; Hindi and regional languages in Phase 2 |
| Multi-currency support | INR only |
| White-label / multi-tenant | Single-tenant deployment per lender |

---

## 9. Glossary

| Term | Definition |
|------|-----------|
| **LOS** | Loan Origination System |
| **LMS** | Loan Management System |
| **KYC** | Know Your Customer |
| **KFS** | Key Fact Statement |
| **AA** | Account Aggregator |
| **DLD** | Digital Lending Directions |
| **DPDP** | Digital Personal Data Protection |
| **eKYC** | Electronic KYC (Aadhaar-based) |
| **V-CIP** | Video Customer Identification Process |
| **CKYC** | Central KYC |
| **NACH** | National Automated Clearing House |
| **NPA** | Non-Performing Asset |
| **PAR** | Portfolio at Risk |
| **DPD** | Days Past Due |
| **FOIR** | Fixed Obligation to Income Ratio |
| **APR** | Annual Percentage Rate |
| **RBAC** | Role-Based Access Control |
| **CERSAI** | Central Registry of Securitisation Asset Reconstruction and Security Interest |
| **PMLA** | Prevention of Money Laundering Act |
| **AML** | Anti-Money Laundering |
| **DSA** | Direct Selling Agent |
| **LSP** | Lending Service Provider |
| **RE** | Regulated Entity |
| **MSME** | Micro, Small and Medium Enterprises |
