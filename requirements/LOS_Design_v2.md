
LOAN ORIGINATION SYSTEM
LOS  �  v2.0
Consolidated Service Architecture � PostgreSQL-Only � Gitea + Jenkins CI/CD � eSign / emsigner Integration

Document Version
v2.0 � Revised Architecture
Changes From v1.0
Consolidated master service � PostgreSQL only (MongoDB removed) � Gitea+Jenkins (no monorepo) � emsigner / eSign added
Tech Stack
React � Java Spring Boot � PostgreSQL � Redis � RabbitMQ
CI/CD
Gitea (Source Control) + Jenkins (Pipeline)
Classification
CONFIDENTIAL � Lender Internal Use Only

TABLE OF CONTENTS
1. Change Summary from v1.0	4
2. Revised Microservice Architecture	5
2.1 Consolidated Service Map	5
3. LOS Core Service � Consolidated Master Service	6
3.1 Internal Module Architecture	6
3.2 Package Structure	6
3.3 Java Interface Definitions	7
ILoanApplicationService.java	7
IKycOrchestrationService.java	8
IWorkflowEngineService.java	8
IIntegrationRouterService.java	9
IESignProvider.java  �  New Interface for emsigner / Authbridge eSign	10
ITransactionService.java	10
IKycProvider.java  �  Pluggable KYC Aggregator Interface	11
4. Database Architecture � PostgreSQL Only	12
4.1 Why PostgreSQL Only (MongoDB Removed)	12
4.2 Core Table Definitions	12
loan_applications � Master Application Record	12
kyc_step_results � Per-Check KYC Results	12
workflow_configs � Lender-Configurable Workflow	13
esign_requests � eSign / Digital Signature Records	14
aggregator_configs � Provider API Credentials (Encrypted)	14
transactions � Disbursement & Payment Ledger	14
5. eSign / emsigner Integration � Digital Signature Module	16
5.1 Use Cases for eSign in LOS	16
5.2 Supported eSign Providers	16
5.3 eSign Workflow � Step by Step	16
5.4 EmsignerProvider.java � Implementation	17
5.5 Authbridge eSign Provider	18
5.6 IntegrationRouterService � eSign Routing Logic	18
6. CI/CD Pipeline � Gitea + Jenkins	20
6.1 Why Gitea + Jenkins	20
6.2 Repository Structure (No Monorepo)	20
6.3 Jenkinsfile � LOS Core Service Pipeline	20
6.4 Jenkins Shared Library � Reusable Steps	22
6.5 Gitea Webhook ? Jenkins Trigger Setup	22
7. Complete Revised Service & Technology Reference	24
7.1 Final Service Inventory	24
7.2 Aggregator & Provider Reference	24
8. Updated Implementation Plan	26
Phase 1 � Infrastructure & DevOps (Weeks 1�3)	26
Phase 2 � IAM + Enrollment Services (Weeks 2�5)	26
Phase 3 � LOS Core Service Build (Weeks 4�12)	27
Phase 4 � Notification, LMS Adapter, Frontend (Weeks 8�16)	28
Phase 5 � Testing, Security & Delivery (Weeks 15�18)	28


1. Change Summary from v1.0
This v2.0 document supersedes the original LOS design (v1.0) with the following architectural decisions applied:

Area
v1.0 Design
v2.0 Decision
Service Architecture
6 separate microservices for Integration, KYC, Loan App, Workflow, Transaction
Consolidated into 1 master service (LOS Core Service) with Java interface-based modules
Database
MongoDB (loan apps) + PostgreSQL (IAM, transactions)
PostgreSQL only � all data in one relational DB with JSONB for flexible fields
Source Control
Monorepo (single Git repo for all services)
Gitea � separate repos per service/component, no monorepo
CI/CD
GitHub Actions
Gitea + Jenkins (lender self-hosted pipeline)
eSign / emsigner
Not included
Added as aggregator module � emsigner + Authbridge eSign for KFS/Agreement digital signing

2. Revised Microservice Architecture
2.1 Consolidated Service Map
The following diagram describes the full service landscape. The key change is the consolidation of Integration Service, KYC Service, Loan Application Service, Workflow Engine, and Transaction Service into a single LOS Core Service with clearly separated Java interface-based internal modules.

Service
Port
Technology
Responsibility
API Gateway
8080
Spring Cloud Gateway
Routing, rate limiting, JWT validation, request/response logging
Discovery Service (Eureka)
8761
Netflix Eureka
Service registry, health checks, dynamic load balancing
IAM Service
8081
Spring Boot + JWT
Authentication, token issuance, refresh, RBAC enforcement, TOTP 2FA
Enrollment Service
8082
Spring Boot
Customer registration, OTP verification, consent link generation, consent artefact storage
? LOS Core Service
(Consolidated Master Service)
8083
Spring Boot + PostgreSQL
All loan application lifecycle, KYC orchestration, workflow execution, integrations, transaction management � see Section 3
Notification Service
8084
Spring Boot + RabbitMQ
SMS, Email, WhatsApp via event-driven queue; template management; delivery tracking
Encore LMS (remote API)
HTTPS (ENCORE_BASE_URL)
Remote HTTP
LOS Core calls remote Encore over HTTP (ENCORE_BASE_URL + Basic Auth) � EMI schedule, repayment status, loan account creation, NPA flags
UI Service (React + Nginx)
3000
React 18 + Nginx
Lender Dashboard + Customer Portal � two build targets from one React codebase
?  Total active services reduced from 12 (v1.0) to 8 (v2.0). The LOS Core Service handles all core loan processing concerns internally through Java interfaces and Spring service beans.


3. LOS Core Service � Consolidated Master Service
3.1 Internal Module Architecture
The LOS Core Service is a single Spring Boot application that internally separates concerns through Java interfaces, Spring service beans, and package-level boundaries. This gives the clarity of microservice separation without the network overhead, distributed transaction complexity, or deployment sprawl of separate services.

Why Consolidate These Services?
� Loan application, KYC, workflow, integrations, and transactions are tightly coupled � splitting them forces distributed transactions (saga pattern) with high complexity
� A single service with interface-based modules gives clean separation of concerns with simple method calls instead of HTTP/gRPC between services
� Single database transaction boundary � KYC result + application status update happen atomically
� Easier to deploy on lender infrastructure � one JAR / one Docker container for all core logic
� Interface contracts are enforced by the Java compiler, not by API schema management
3.2 Package Structure
com.los.core
??? api/                          # REST controllers (HTTP entry points)
?   ??? LoanApplicationController.java
?   ??? KycController.java
?   ??? WorkflowController.java
?   ??? IntegrationController.java
?   ??? TransactionController.java
??? service/                      # Spring @Service beans implementing interfaces
?   ??? loan/
?   ?   ??? LoanApplicationService.java      # implements ILoanApplicationService
?   ?   ??? LoanApplicationServiceImpl.java
?   ??? kyc/
?   ?   ??? KycOrchestrationService.java     # implements IKycOrchestrationService
?   ?   ??? KycOrchestrationServiceImpl.java
?   ??? workflow/
?   ?   ??? WorkflowEngineService.java        # implements IWorkflowEngineService
?   ?   ??? WorkflowEngineServiceImpl.java
?   ??? integration/
?   ?   ??? IntegrationRouterService.java     # implements IIntegrationRouterService
?   ?   ??? providers/                        # one class per aggregator
?   ?       ??? PerfiosProvider.java           # implements IKycProvider
?   ?       ??? AuthbridgeProvider.java         # implements IKycProvider
?   ?       ??? EquifaxProvider.java            # implements IBureauProvider
?   ?       ??? HypervergeProvider.java         # implements IKycProvider
?   ?       ??? EmsignerProvider.java           # implements IESignProvider
?   ?       ??? AuthbridgeESignProvider.java    # implements IESignProvider
?   ??? transaction/
?       ??? TransactionService.java            # implements ITransactionService
?       ??? TransactionServiceImpl.java
??? interfaces/                   # Java interface contracts (core of the design)
?   ??? ILoanApplicationService.java
?   ??? IKycOrchestrationService.java
?   ??? IWorkflowEngineService.java
?   ??? IIntegrationRouterService.java
?   ??? IKycProvider.java
?   ??? IBureauProvider.java
?   ??? IESignProvider.java        # NEW � eSign interface
?   ??? ITransactionService.java
??? model/                        # JPA entities + DTOs
??? repository/                   # Spring Data JPA repositories
??? config/                       # Spring config, security, DB, RabbitMQ
??? exception/                    # Custom exceptions + global handler
3.3 Java Interface Definitions
ILoanApplicationService.java
package com.los.core.interfaces;

import com.los.core.model.dto.*;
import java.util.List;
import java.util.UUID;

public interface ILoanApplicationService {

    /** Create a new loan application (draft state) */
    LoanApplicationDTO createApplication(CreateApplicationRequest request);

    /** Fetch single application by ID */
    LoanApplicationDTO getApplicationById(UUID applicationId);

    /** List applications with filters (status, RM, borrower type, date range) */
    PagedResult<LoanApplicationDTO> listApplications(ApplicationFilterRequest filter);

    /** Update application status � enforces state machine transitions */
    LoanApplicationDTO updateStatus(UUID applicationId, ApplicationStatus newStatus, String remarks);

    /** Upload document against an application */
    DocumentDTO attachDocument(UUID applicationId, DocumentUploadRequest request);

    /** Credit officer approve or reject */
    LoanApplicationDTO recordDecision(UUID applicationId, CreditDecisionRequest decision);

    /** Request additional documents from customer */
    void requestAdditionalDocuments(UUID applicationId, List<String> docTypes, String remarks);

    /** Generate sanction letter PDF and return download URL */
    String generateSanctionLetter(UUID applicationId);
}
IKycOrchestrationService.java
package com.los.core.interfaces;

import com.los.core.model.dto.*;
import java.util.UUID;

public interface IKycOrchestrationService {

    /** Trigger the KYC workflow for an application � reads workflow config and executes steps */
    KycSessionDTO initiateKyc(UUID applicationId);

    /** Execute a specific KYC step (called by workflow engine) */
    KycStepResultDTO executeStep(UUID applicationId, KycStepType stepType);

    /** Handle OTP submission for Aadhaar / mobile verification */
    KycStepResultDTO submitOtp(UUID sessionId, String otp);

    /** Get overall KYC status across all steps for an application */
    KycStatusDTO getKycStatus(UUID applicationId);

    /** Manually override a KYC step result (credit manager only) */
    void overrideKycStep(UUID applicationId, KycStepType stepType, String justification, String officerId);

    /** Pull Equifax bureau report � called post KYC completion */
    BureauReportDTO pullBureauReport(UUID applicationId);
}
IWorkflowEngineService.java
package com.los.core.interfaces;

import com.los.core.model.dto.*;
import com.los.core.model.entity.WorkflowConfig;
import java.util.List;
import java.util.UUID;

public interface IWorkflowEngineService {

    /** Load the active workflow config for a given borrower type */
    WorkflowConfig getActiveWorkflow(BorrowerType borrowerType);

    /** Start workflow execution for an application � persists step states */
    WorkflowExecutionDTO startExecution(UUID applicationId, UUID workflowConfigId);

    /** Advance to the next pending step */
    WorkflowStepDTO advanceToNextStep(UUID applicationId);

    /** Get current step and overall progress */
    WorkflowProgressDTO getProgress(UUID applicationId);

    /** Mark a specific step as complete, skipped, or failed */
    void recordStepOutcome(UUID applicationId, String stepType, StepOutcome outcome, String rawResponse);

    /** Admin: save or update a workflow configuration */
    WorkflowConfig saveWorkflowConfig(WorkflowConfigRequest request);

    /** Admin: list all workflow configurations */
    List<WorkflowConfig> listWorkflowConfigs();
}
IIntegrationRouterService.java
package com.los.core.interfaces;

import com.los.core.model.dto.*;
import java.util.Map;
import java.util.UUID;

public interface IIntegrationRouterService {

    /** Route a KYC check to the configured provider � reads aggregator config from DB */
    IntegrationResponseDTO routeKycCheck(UUID applicationId, KycStepType stepType, Map<String, Object> payload);

    /** Route bureau pull to Equifax or configured bureau provider */
    IntegrationResponseDTO routeBureauPull(UUID applicationId, BureauPullRequest request);

    /** Route eSign request to configured eSign provider (emsigner or Authbridge eSign) */
    ESignResponseDTO routeESignRequest(UUID applicationId, ESignRequest request);

    /** Handle incoming webhook callback from any provider */
    void handleWebhookCallback(String providerName, String rawPayload, Map<String, String> headers);

    /** Fetch aggregator config for a given step (decrypts creds) */
    AggregatorConfig getAggregatorConfig(KycStepType stepType);

    /** Test aggregator connectivity with sandbox credentials */
    AggregatorTestResult testAggregatorConnection(UUID configId);
}
IESignProvider.java  �  New Interface for emsigner / Authbridge eSign
package com.los.core.interfaces;

import com.los.core.model.dto.*;

/**
 * Common interface for all eSign / digital signature providers.
 * Implementations: EmsignerProvider, AuthbridgeESignProvider
 * Used for: KFS signing, Loan Agreement signing, Sanction Letter signing
 */
public interface IESignProvider {

    /** Upload document to provider and get a signing request ID */
    ESignInitResponse initiateSigningRequest(ESignInitRequest request);

    /** Generate the signing URL to redirect/redirect customer to */
    String getSigningUrl(String signingRequestId, String returnUrl);

    /** Check the status of a signing request */
    ESignStatusResponse getSigningStatus(String signingRequestId);

    /** Download the signed document from provider */
    byte[] downloadSignedDocument(String signingRequestId);

    /** Cancel an in-progress signing request */
    void cancelSigningRequest(String signingRequestId, String reason);

    /** Provider identifier � used for logging and routing */
    String getProviderName();
}
ITransactionService.java
package com.los.core.interfaces;

import com.los.core.model.dto.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ITransactionService {

    /** Record a disbursement trigger and create ledger entry */
    DisbursementDTO triggerDisbursement(UUID applicationId, DisbursementRequest request);

    /** Record a repayment event received from LMS webhook */
    void recordRepaymentEvent(UUID loanAccountId, RepaymentEventDTO event);

    /** Get all transactions for a loan account */
    List<TransactionDTO> getTransactionHistory(UUID loanAccountId);

    /** Get current outstanding balance from LMS (cached, 15-min TTL) */
    BigDecimal getOutstandingBalance(UUID loanAccountId);

    /** Create NACH mandate record */
    NachMandateDTO createNachMandate(UUID loanAccountId, NachMandateRequest request);
}
IKycProvider.java  �  Pluggable KYC Aggregator Interface
package com.los.core.interfaces;

import com.los.core.model.dto.*;
import java.util.Map;

/**
 * Pluggable interface for all KYC aggregator providers.
 * Each provider implements exactly the checks it supports.
 * Implementations: PerfiosProvider, AuthbridgeProvider, HypervergeProvider
 */
public interface IKycProvider {

    /** Perform a KYC check � payload is step-specific (PAN number, Aadhaar, etc.) */
    KycCheckResult performCheck(KycStepType stepType, Map<String, Object> payload, AggregatorConfig config);

    /** Send OTP for Aadhaar / mobile verification */
    OtpSendResult sendOtp(String identifier, AggregatorConfig config);

    /** Verify submitted OTP and return KYC data if valid */
    KycCheckResult verifyOtp(String sessionId, String otp, AggregatorConfig config);

    /** Returns which KYC step types this provider supports */
    java.util.Set<KycStepType> getSupportedStepTypes();

    /** Provider name for logging, config routing */
    String getProviderName();
}

4. Database Architecture � PostgreSQL Only
4.1 Why PostgreSQL Only (MongoDB Removed)
Rationale for Removing MongoDB
� PostgreSQL JSONB columns provide schema-flexible document storage identical to MongoDB � no second DB to operate
� A single DB engine simplifies lender deployment dramatically � one database to backup, secure, and maintain
� ACID transactions across loan application, KYC results, and financial data in one atomic boundary
� PostgreSQL full-text search, JSON path queries, and aggregation replace MongoDB's query capabilities
� Reduced infrastructure cost � one DB server instead of two, simpler connection pooling (HikariCP)
� Spring Data JPA + Flyway migrations provide complete schema lifecycle management
4.2 Core Table Definitions
loan_applications � Master Application Record
CREATE TABLE loan_applications (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_ref   VARCHAR(20) UNIQUE NOT NULL,   -- e.g. LOS-2025-00001
  borrower_type     VARCHAR(30) NOT NULL,           -- INDIVIDUAL | PROPRIETOR | PARTNERSHIP | COMPANY
  status            VARCHAR(40) NOT NULL,           -- DRAFT | PENDING_APPROVAL | KYC_IN_PROGRESS | ...
  customer_id       UUID REFERENCES customers(id),
  rm_user_id        UUID REFERENCES users(id),
  loan_product      VARCHAR(100),
  loan_amount       NUMERIC(14,2),
  tenure_months     INT,
  loan_purpose      TEXT,
  credit_score      INT,
  score_tier        VARCHAR(5),                     -- A+ | A | B | C | D
  workflow_id       UUID REFERENCES workflow_configs(id),
  decision_remarks  TEXT,
  decided_by        UUID REFERENCES users(id),
  decided_at        TIMESTAMPTZ,
  extra_data        JSONB DEFAULT '{}',             -- flexible fields, borrower-type-specific
  created_at        TIMESTAMPTZ DEFAULT NOW(),
  updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_loan_app_status  ON loan_applications(status);
CREATE INDEX idx_loan_app_customer ON loan_applications(customer_id);
CREATE INDEX idx_loan_extra_data   ON loan_applications USING GIN(extra_data);
kyc_step_results � Per-Check KYC Results
CREATE TABLE kyc_step_results (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id  UUID NOT NULL REFERENCES loan_applications(id),
  step_type       VARCHAR(40) NOT NULL,   -- AADHAAR_OTP | PAN | BANK_PENNY_DROP | GSTIN | ...
  provider_name   VARCHAR(40) NOT NULL,   -- PERFIOS | AUTHBRIDGE | EQUIFAX | HYPERVERGE | ...
  status          VARCHAR(20) NOT NULL,   -- PENDING | PASS | FAIL | SKIPPED | MANUAL_REVIEW
  confidence_score NUMERIC(5,4),          -- 0.0000 to 1.0000
  raw_response    JSONB,                  -- full provider API response (immutable once saved)
  parsed_data     JSONB,                  -- extracted KYC data (name, DOB, address, etc.)
  override_by     UUID REFERENCES users(id),
  override_reason TEXT,
  attempt_count   INT DEFAULT 1,
  executed_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_kyc_application ON kyc_step_results(application_id);
CREATE INDEX idx_kyc_step_type   ON kyc_step_results(application_id, step_type);
workflow_configs � Lender-Configurable Workflow
CREATE TABLE workflow_configs (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name            VARCHAR(200) NOT NULL,
  borrower_type   VARCHAR(30) NOT NULL,
  is_active       BOOLEAN DEFAULT TRUE,
  auto_approve_score INT,
  steps           JSONB NOT NULL,   -- ordered array of step config objects
  created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Example steps JSONB structure:
-- [{
--   "step_type": "MOBILE_OTP",
--   "sequence_order": 1,
--   "is_mandatory": true,
--   "provider": "SMS_GATEWAY",
--   "pass_threshold": 1.0,
--   "on_fail_action": "BLOCK"
-- }, {
--   "step_type": "PAN_VERIFY",
--   "sequence_order": 2,
--   "is_mandatory": true,
--   "provider": "AUTHBRIDGE",
--   "on_fail_action": "MANUAL_REVIEW"
-- }, {
--   "step_type": "ESIGN_KFS",
--   "sequence_order": 8,
--   "is_mandatory": true,
--   "provider": "EMSIGNER",
--   "on_fail_action": "BLOCK"
-- }]
esign_requests � eSign / Digital Signature Records
CREATE TABLE esign_requests (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id      UUID NOT NULL REFERENCES loan_applications(id),
  document_type       VARCHAR(50) NOT NULL,   -- KFS | LOAN_AGREEMENT | SANCTION_LETTER
  provider            VARCHAR(30) NOT NULL,   -- EMSIGNER | AUTHBRIDGE_ESIGN
  provider_request_id VARCHAR(200),           -- ID returned by the eSign provider
  signing_url         TEXT,                   -- URL sent to customer for signing
  status              VARCHAR(30) NOT NULL,   -- INITIATED | SENT | SIGNED | DECLINED | EXPIRED
  signer_name         VARCHAR(200),
  signer_aadhaar_last4 VARCHAR(4),
  signed_document_url TEXT,                   -- S3 URL of signed PDF
  signed_at           TIMESTAMPTZ,
  expires_at          TIMESTAMPTZ,
  raw_response        JSONB,
  created_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_esign_application ON esign_requests(application_id);
CREATE INDEX idx_esign_status       ON esign_requests(status);
aggregator_configs � Provider API Credentials (Encrypted)
CREATE TABLE aggregator_configs (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_name   VARCHAR(40) NOT NULL,         -- PERFIOS | AUTHBRIDGE | EMSIGNER | ...
  step_type       VARCHAR(40),                  -- null = applies to all steps for this provider
  base_url        TEXT NOT NULL,
  auth_type       VARCHAR(20) NOT NULL,         -- OAUTH2 | API_KEY | BASIC
  client_id_enc   TEXT,                         -- AES-256 encrypted
  client_secret_enc TEXT,                       -- AES-256 encrypted
  token_url       TEXT,
  extra_config    JSONB DEFAULT '{}',           -- provider-specific additional params
  timeout_ms      INT DEFAULT 30000,
  retry_count     INT DEFAULT 3,
  webhook_secret  TEXT,                         -- for webhook signature validation
  environment     VARCHAR(15) DEFAULT 'SANDBOX',
  is_active       BOOLEAN DEFAULT TRUE,
  updated_at      TIMESTAMPTZ DEFAULT NOW()
);
transactions � Disbursement & Payment Ledger
CREATE TABLE transactions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  loan_account_id UUID NOT NULL,               -- ID from LMS
  application_id  UUID REFERENCES loan_applications(id),
  txn_type        VARCHAR(30) NOT NULL,        -- DISBURSEMENT | REPAYMENT | PREPAYMENT | CHARGE | REVERSAL
  amount          NUMERIC(14,2) NOT NULL,
  currency        CHAR(3) DEFAULT 'INR',
  reference_no    VARCHAR(100),                -- bank UTR / payment gateway ref
  status          VARCHAR(20) NOT NULL,        -- PENDING | SUCCESS | FAILED | REVERSED
  meta            JSONB DEFAULT '{}',
  transacted_at   TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_txn_loan_account ON transactions(loan_account_id);
CREATE INDEX idx_txn_type_status   ON transactions(txn_type, status);

5. eSign / emsigner Integration � Digital Signature Module
5.1 Use Cases for eSign in LOS
Digital signing is required at multiple points in the loan lifecycle. The LOS supports eSign as a configurable workflow step, meaning lenders can enable or disable it and choose their preferred provider.

Document Type
When Signed
Signer
Legal Basis
KFS (Key Fact Statement)
Before loan agreement � after sanction, before customer accepts
Customer (Borrower)
RBI Fair Practices Code � mandatory for all retail loans
Loan Agreement
Post KFS signing � customer accepts loan terms formally
Customer + Authorised Signatory
Indian Contract Act 1872 � enforceable digital signature
Sanction Letter
After credit decision � lender countersigns approval
Credit Officer / Lender
Internal record + borrower acknowledgement
NACH Mandate Form
Post-disbursement � customer authorises auto-debit
Customer
NPCI NACH framework
5.2 Supported eSign Providers
Provider
Auth Method
Signing Method
Key Feature
emsigner
API Key + HMAC signature
Aadhaar OTP-based eSign (UIDAI compliant)
MCA empanelled DSC + eSign; widely used for legal docs
Authbridge eSign
OAuth2 Client Credentials
Aadhaar OTP eSign + Registered Device (RD) option
Same Authbridge ecosystem as KYC � single vendor for KYC + eSign
?  Both providers use Aadhaar OTP-based eSign under the Information Technology Act 2000 (Section 3A) � legally equivalent to wet ink signatures for financial agreements.

5.3 eSign Workflow � Step by Step
1. Credit officer approves loan ? system auto-generates KFS PDF from template + loan terms
2. LOS Core Service calls IIntegrationRouterService.routeESignRequest() with document type = KFS
3. Router reads workflow config to determine provider (EMSIGNER or AUTHBRIDGE_ESIGN)
4. EmsignerProvider or AuthbridgeESignProvider calls provider API to upload PDF and create signing request
5. Provider returns a signing_request_id and a signing URL
6. LOS sends customer a notification (SMS + WhatsApp) with the signing link
7. Customer opens link ? provider UI loads with document preview ? customer enters Aadhaar OTP
8. Provider validates Aadhaar OTP with UIDAI ? affixes eSign to document
9. Provider sends webhook callback to LOS: {status: SIGNED, signed_document_url}
10. LOS stores signed document URL in esign_requests table ? updates application status
11. Signed PDF stored in S3; download available to customer and lender in dashboard

5.4 EmsignerProvider.java � Implementation
package com.los.core.service.integration.providers;

import com.los.core.interfaces.IESignProvider;
import com.los.core.model.dto.*;
import org.springframework.stereotype.Service;

@Service("emsignerProvider")
public class EmsignerProvider implements IESignProvider {

    private static final String PROVIDER = "EMSIGNER";

    @Override
    public ESignInitResponse initiateSigningRequest(ESignInitRequest request) {
        // 1. Build emsigner API payload with document bytes (Base64), signer info
        // 2. Compute HMAC-SHA256 signature on payload using lender API key
        // 3. POST to emsigner /api/v2/sign/initiate
        // 4. Parse response: signingRequestId, expiryTime
        // 5. Persist esign_requests record with status = INITIATED
        return buildResponse(/* parsed response */);
    }

    @Override
    public String getSigningUrl(String signingRequestId, String returnUrl) {
        // Construct redirect URL: emsigner base URL + request ID + encoded returnUrl
        return emsignerBaseUrl + "/sign?id=" + signingRequestId + "&return=" + encode(returnUrl);
    }

    @Override
    public ESignStatusResponse getSigningStatus(String signingRequestId) {
        // GET /api/v2/sign/status/{signingRequestId}
        // Returns: PENDING | SIGNED | DECLINED | EXPIRED
        return callStatusApi(signingRequestId);
    }

    @Override
    public byte[] downloadSignedDocument(String signingRequestId) {
        // GET /api/v2/sign/download/{signingRequestId}
        // Returns signed PDF bytes
        return callDownloadApi(signingRequestId);
    }

    @Override
    public void cancelSigningRequest(String signingRequestId, String reason) {
        // POST /api/v2/sign/cancel with reason
    }

    @Override
    public String getProviderName() { return PROVIDER; }
}
5.5 Authbridge eSign Provider
@Service("authbridgeESignProvider")
public class AuthbridgeESignProvider implements IESignProvider {

    private static final String PROVIDER = "AUTHBRIDGE_ESIGN";

    @Override
    public ESignInitResponse initiateSigningRequest(ESignInitRequest request) {
        // 1. Get OAuth2 access token from Authbridge token endpoint
        // 2. POST document to Authbridge eSign /document/upload
        // 3. Create signing session: POST /session/create with signer mobile + Aadhaar
        // 4. Persist esign_requests record
        return buildResponse(/* ... */);
    }

    @Override
    public String getSigningUrl(String signingRequestId, String returnUrl) {
        return authbridgeESignPortalUrl + "?session=" + signingRequestId;
    }

    // ... other IESignProvider methods implemented similarly

    @Override
    public String getProviderName() { return PROVIDER; }
}
5.6 IntegrationRouterService � eSign Routing Logic
@Service
public class IntegrationRouterServiceImpl implements IIntegrationRouterService {

    private final Map<String, IESignProvider> eSignProviders;  // injected by Spring
    private final AggregatorConfigRepository configRepo;

    @Override
    public ESignResponseDTO routeESignRequest(UUID applicationId, ESignRequest request) {
        // 1. Read workflow config for this application to find eSign step
        // 2. Load aggregator_configs for provider (e.g. EMSIGNER)
        AggregatorConfig cfg = configRepo.findByProviderName(request.getProvider());

        // 3. Decrypt credentials
        String decryptedKey = encryptionService.decrypt(cfg.getClientIdEnc());

        // 4. Route to correct IESignProvider bean
        IESignProvider provider = eSignProviders.get(cfg.getProviderName().toLowerCase() + "Provider");

        // 5. Call provider and return standardised response
        ESignInitRequest initReq = buildInitRequest(applicationId, request, decryptedKey);
        ESignInitResponse initResp = provider.initiateSigningRequest(initReq);

        // 6. Persist esign_request record
        esignRepository.save(buildEsignRecord(applicationId, initResp, request));

        return ESignResponseDTO.from(initResp);
    }
}

6. CI/CD Pipeline � Gitea + Jenkins
6.1 Why Gitea + Jenkins
Rationale for Gitea + Jenkins (No Monorepo)
� Lenders self-host � Gitea is lightweight, open source, and runs on the same lender server as the application
� Jenkins is the most mature self-hosted CI/CD tool with excellent Spring Boot and Docker support
� No dependency on GitHub, GitLab, or any external SaaS platform � full air-gap deployment support
� Separate repos per service (no monorepo) � each service has its own Jenkinsfile, versioning, and release cycle
� Jenkins shared library allows common pipeline steps (test, build, Docker push) to be reused without code duplication
� Gitea webhooks trigger Jenkins builds automatically on push/PR merge
6.2 Repository Structure (No Monorepo)
Each service lives in its own Gitea repository under the los-platform organization. This enables independent versioning, independent deployment, and clear ownership.

Gitea Repository
Branch Strategy
Trigger
los-platform/api-gateway
main ? prod | develop ? staging
Push to develop or main
los-platform/discovery-service
main ? prod | develop ? staging
Push to develop or main
los-platform/iam-service
main ? prod | develop ? staging
Push to develop or main
los-platform/enrollment-service
main ? prod | develop ? staging
Push to develop or main
los-platform/notification-service
main ? prod | develop ? staging
Push to develop or main
los-platform/los-core-service
main ? prod | develop ? staging
Push to develop or main
los-platform/ui-service
main ? prod | develop ? staging
Push to develop or main
los-platform/jenkins-shared-library
main only
Manual update � shared pipeline steps
los-platform/infra-scripts
main only
Deployment scripts, Docker Compose, DB migrations
6.3 Jenkinsfile � LOS Core Service Pipeline
// los-core-service/Jenkinsfile
// Uses shared library for common steps
@Library('los-jenkins-shared') _

pipeline {
  agent any

  environment {
    SERVICE_NAME    = 'los-core-service'
    DOCKER_REGISTRY = 'registry.lender.local:5000'
    IMAGE_TAG       = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
  }

  stages {

    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Unit Tests') {
      steps {
        sh './mvnw test -Dspring.profiles.active=test'
        junit 'target/surefire-reports/*.xml'
        jacoco execPattern: 'target/jacoco.exec', minimumInstructionCoverage: '80'
      }
    }

    stage('Integration Tests') {
      steps {
        // Testcontainers spins up PostgreSQL + Redis for real integration tests
        sh './mvnw verify -P integration-tests'
      }
    }

    stage('Build JAR') {
      steps {
        sh './mvnw package -DskipTests'
        archiveArtifacts artifacts: 'target/*.jar'
      }
    }

    stage('Docker Build & Push') {
      steps {
        script {
          losDockerBuild(SERVICE_NAME, IMAGE_TAG, DOCKER_REGISTRY)  // shared library step
        }
      }
    }

    stage('Deploy to Staging') {
      when { branch 'develop' }
      steps {
        script {
          losDeployService(SERVICE_NAME, IMAGE_TAG, 'staging')       // shared library step
        }
      }
    }

    stage('Deploy to Production') {
      when { branch 'main' }
      input { message 'Deploy to Production?' }
      steps {
        script {
          losDeployService(SERVICE_NAME, IMAGE_TAG, 'production')    // shared library step
          losRunDbMigrations('production')                            // Flyway migrations
        }
      }
    }
  }

  post {
    failure  { losNotifyFailure(SERVICE_NAME, env.BRANCH_NAME) }
    success  { losNotifySuccess(SERVICE_NAME, IMAGE_TAG) }
  }
}
6.4 Jenkins Shared Library � Reusable Steps
// jenkins-shared-library/vars/losDockerBuild.groovy
def call(String serviceName, String imageTag, String registry) {
  docker.withRegistry("http://${registry}") {
    def image = docker.build("${registry}/${serviceName}:${imageTag}")
    image.push()
    image.push('latest')
  }
}

// jenkins-shared-library/vars/losDeployService.groovy
def call(String serviceName, String imageTag, String env) {
  sh """
    ssh deploy@lender-server \
      'cd /opt/los && \
       export IMAGE_TAG=${imageTag} && \
       docker compose pull ${serviceName} && \
       docker compose up -d --no-deps ${serviceName}'
  """
}
6.5 Gitea Webhook ? Jenkins Trigger Setup
12. Install Jenkins on lender server: docker run jenkins/jenkins:lts
13. Install Gitea on lender server: docker run gitea/gitea:latest
14. In Gitea repo settings ? Webhooks ? Add: http://jenkins:8080/gitea-webhook/post
15. In Jenkins: New Multibranch Pipeline ? Gitea Branch Source ? add repo URL
16. Jenkins scans Gitea repo, discovers branches, creates pipeline per branch
17. Each push/merge to develop or main automatically triggers the Jenkinsfile pipeline
18. Jenkins shared library registered in Jenkins Global ? Source Code Management ? Gitea repo URL


7. Complete Revised Service & Technology Reference
7.1 Final Service Inventory
Service
Port
Docker Image
DB / Queue
Notes
API Gateway
8080
los/api-gateway
Redis (rate limit)
Spring Cloud Gateway � JWT filter � CORS
Discovery (Eureka)
8761
los/discovery
�
Service registry � health dashboard
IAM Service
8081
los/iam
PostgreSQL, Redis
Auth � JWT � RBAC � TOTP 2FA
Enrollment Service
8082
los/enrollment
PostgreSQL, Redis
Customer reg � OTP � consent artefacts
? LOS Core Service
8083
los/los-core
PostgreSQL, Redis, S3
Loan apps � KYC � Workflow � Integrations � Transactions � eSign
Notification Service
8084
los/notification
PostgreSQL, RabbitMQ
SMS � Email � WhatsApp � In-App
Encore LMS (remote)
HTTPS
Remote API (ENCORE_BASE_URL)
Vendor-managed
EMI � repayment � NPA � NACH
UI Service
3000
los/ui
�
React 18 + Nginx � Lender + Customer portals
PostgreSQL
5432
postgres:16
Primary DB
All persistent data � SSD storage recommended
Redis
6379
redis:7.2-alpine
�
OTP/session cache � API rate limit counters
RabbitMQ
5672
rabbitmq:3.13
�
Notification event queue � DLQ for retries
Gitea
3001
gitea/gitea
PostgreSQL
Self-hosted source control � webhook triggers
Jenkins
8090
jenkins/jenkins:lts
�
CI/CD pipelines � shared library � Docker build
Docker Registry
5000
registry:2
�
Lender-hosted private Docker image registry
?  MongoDB is completely removed. All data is stored in PostgreSQL using JSONB for flexible document-style fields (workflow steps, KYC raw responses, aggregator config).

7.2 Aggregator & Provider Reference
Provider
Type
Interface Impl
Capabilities
Perfios
KYC + AA
PerfiosProvider
Aadhaar OTP, PAN, Bank statement (AA), GSTIN filing analysis
Authbridge
KYC
AuthbridgeProvider
Aadhaar OTP eKYC, PAN, VoterID, DL, GSTIN, Udyam, MCA21, AML
Equifax
Bureau
EquifaxProvider
Consumer credit report, Commercial report, score + DPD extraction
Hyperverge
Video KYC
HypervergeProvider
Face match (selfie vs Aadhaar), liveness detection, V-CIP (Video KYC)
? emsigner
eSign
EmsignerProvider
Aadhaar OTP-based digital signature; MCA empanelled DSC; KFS, Loan Agreement, Sanction Letter signing
? Authbridge eSign
eSign
AuthbridgeESignProvider
Aadhaar OTP eSign + RD option; same Authbridge OAuth2 as KYC provider � single vendor setup

8. Updated Implementation Plan
Phase 1 � Infrastructure & DevOps (Weeks 1�3)
#
Task
Details
Owner
1.1
Gitea Setup + Repo Creation
Deploy Gitea on lender server. Create los-platform org. Create one repo per service + shared-library + infra-scripts
DevOps
1.2
Jenkins Setup + Shared Library
Deploy Jenkins. Install Docker Pipeline, Gitea, JUnit plugins. Register jenkins-shared-library. Create Multibranch Pipeline per service repo
DevOps
1.3
Private Docker Registry
Deploy registry:2 on lender server. Configure TLS. Configure Jenkins and all servers to use it
DevOps
1.4
PostgreSQL Setup
PostgreSQL 16 with SSL. Create databases: los_iam, los_core, los_notification, los_enrollment. Flyway migration baseline. Daily backup to S3/NFS
DBA / DevOps
1.5
Redis + RabbitMQ
Redis with AUTH password. RabbitMQ with management UI. Define queues: notification.sms, notification.email, notification.whatsapp, notification.dlq
DevOps
1.6
Gitea Webhook ? Jenkins
Configure webhook per repo. Test end-to-end: push to develop ? Jenkins triggers ? build passes
DevOps
Phase 2 � IAM + Enrollment Services (Weeks 2�5)
#
Task
Details
Owner
2.1
IAM Service (los-platform/iam-service)
Spring Boot project init. PostgreSQL JPA entities: users, roles, permissions, refresh_tokens. JWT issuance (15 min). Refresh token (7 days, HttpOnly cookie). TOTP 2FA. RBAC filter
Backend Lead
2.2
Enrollment Service
Customer registration. Mobile OTP (Redis TTL 5 min). Signed consent link (24h JWT). Consent artefact: store timestamp, IP, OTP session ID in PostgreSQL
Backend
2.3
API Gateway Config
Spring Cloud Gateway routes for all services. JWT validation filter (calls IAM /validate). Rate limit: 100 req/min per IP. CORS for UI domains
Backend Lead
Phase 3 � LOS Core Service Build (Weeks 4�12)
#
Task
Details
Owner
3.1
Project Scaffold + All Interfaces
Spring Boot project. Define all 7 Java interfaces (see Section 3.3). Package structure. Flyway migrations for all 12 PostgreSQL tables
Backend Lead
3.2
Loan Application Module
Implement ILoanApplicationService. State machine: DRAFT ? PENDING_APPROVAL ? KYC ? UNDERWRITING ? APPROVED/REJECTED ? DISBURSED. Document upload (S3). Sanction letter PDF generation (iText)
Backend
3.3
Workflow Engine Module
Implement IWorkflowEngineService. Read workflow_configs JSONB. Step state machine. Parallel step support. Admin APIs for workflow CRUD
Backend Lead
3.4
KYC Orchestration Module
Implement IKycOrchestrationService. Delegates to IIntegrationRouterService per step. Stores results in kyc_step_results. Override mechanism with audit log. Bureau pull trigger
Backend
3.5
Integration Router + All Providers
Implement IIntegrationRouterService. Implement IKycProvider for: Perfios, Authbridge, Hyperverge. Implement IBureauProvider for Equifax. AES-256 credential decryption. Webhook receiver
Backend
3.6
eSign Module � IESignProvider
Implement EmsignerProvider + AuthbridgeESignProvider. Document upload, signing URL generation, status polling, webhook handler, signed doc download. ESIGN_KFS and ESIGN_AGREEMENT step types in workflow engine
Backend
3.7
Transaction Module
Implement ITransactionService. Disbursement trigger + ledger entry. Repayment event recording. NACH mandate. Outstanding balance (Redis cache, 15-min TTL)
Backend
Phase 4 � Notification, LMS Adapter, Frontend (Weeks 8�16)
#
Task
Details
Owner
4.1
Notification Service
RabbitMQ consumer. SMS/Email/WhatsApp providers (lender-configurable). Template engine (Thymeleaf). Delivery status tracking + DLQ retry
Backend
4.2
LMS Adapter Service
Define LMS API contract. Loan account creation, EMI schedule fetch (Redis cache). Repayment webhook. NPA event handler
Backend
4.3
React Frontend � Lender Dashboard
All dashboard modules. Workflow config drag-drop UI. eSign status tracker. Aggregator config admin panel (including emsigner/Authbridge eSign credential setup)
Frontend Lead
4.4
React Frontend � Customer Portal
Mobile-first. OTP login. Guided KYC steps. eSign step: show document ? redirect to provider signing URL ? return and confirm. Loan account + EMI + repayment
Frontend
Phase 5 � Testing, Security & Delivery (Weeks 15�18)
� JUnit 5 unit tests for all interface implementations (? 80% coverage)
� Testcontainers integration tests: PostgreSQL + Redis + RabbitMQ spun up per test suite
� Playwright E2E: full loan flow including eSign (mock provider in test env)
� Security audit: OWASP Top 10, JWT security, credential encryption validation, Aadhaar data handling
� Performance: 500 concurrent users load test; LOS Core Service horizontal scaling test
� Lender delivery package: Docker Compose, Jenkinsfile templates, DB migration scripts, env template, runbook

Phase
Work Items
Deliverables
Duration
Phase 1
Infrastructure & DevOps
Gitea, Jenkins, PostgreSQL, Redis, RabbitMQ, Docker Registry
Weeks 1�3
Phase 2
IAM + Enrollment + Gateway
Auth service, OTP, consent, routing
Weeks 2�5
Phase 3
LOS Core Service (all modules + eSign)
All interface implementations, KYC, workflow, emsigner/Authbridge eSign
Weeks 4�12
Phase 4
Notification, LMS, Frontend
Notification service, LMS adapter, Lender Dashboard + Customer Portal
Weeks 8�16
Phase 5
Testing, Security, Delivery
Full test suite, security audit, lender delivery package
Weeks 15�18
Total Estimated Duration
18 Weeks
Key Architectural Decisions � Summary
� LOS Core Service consolidates 5 services into 1 � interface-based separation inside a single Spring Boot app
� PostgreSQL only with JSONB � replaces MongoDB; single DB engine for all data
� Gitea + Jenkins � fully self-hosted CI/CD with no external SaaS dependency; separate repo per service
� IESignProvider interface � pluggable eSign; switch between emsigner and Authbridge eSign via workflow config
� Workflow engine config drives eSign step � ESIGN_KFS and ESIGN_AGREEMENT are configurable steps like any KYC step
� All aggregator credentials (emsigner API key, Authbridge OAuth2, Equifax, Perfios) encrypted AES-256 in PostgreSQL
END OF DOCUMENT  |  LOS v2.0 Architecture  |  CONFIDENTIAL
LOAN ORIGINATION SYSTEM (LOS)  |  Architecture & Implementation  |  v2.0  |  CONFIDENTIAL

CONFIDENTIAL  |  Lender Internal Use Only  |  Updated: 2025

