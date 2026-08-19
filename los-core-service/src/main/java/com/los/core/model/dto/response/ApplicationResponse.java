package com.los.core.model.dto.response;

import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeOwner;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.model.enums.VkycCompletionMode;
import com.los.core.model.enums.VkycStatus;
import com.los.plp.model.enums.PlpSyncStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ApplicationResponse {

    private UUID id;
    private String applicationNumber;
    private UUID customerId;
    private BorrowerType borrowerType;
    private String loanProduct;
    private IntakeSegment intakeSegment;
    private IntakeOwner intakeOwner;
    private Integer intakeCompletedStep;
    private String borrowerSentBackNotes;
    private BigDecimal requestedAmount;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private String lmsProductCode;
    private String lmsTenureUnit;
    /** Pinned canonical External Product Mapping (for Category-governed applications). */
    private UUID externalProductMappingId;
    /** Pinned canonical External Product Mapping version (for Category-governed applications). */
    private Integer externalProductMappingVersion;
    /** External system (from external_product_mapping row, if pinned). */
    private String externalSystem;
    /** External product code (from external_product_mapping row, if pinned). */
    private String externalProductCode;
    private UUID workflowId;
    private Integer workflowVersion;
    /** Exact pinned workflow name — from application.workflow_id lookup, never latest/active catalog. */
    private String workflowName;
    private String workflowResolutionSource;
    private UUID selectedCustomerCategoryId;
    private String selectedCustomerCategoryCode;
    private Integer selectedCustomerCategoryVersion;
    /** Customer-facing category label at pin time (resolved from selected category entity). */
    private String selectedCustomerCategoryDisplayName;
    private UUID selectedPolicyApplicabilityId;
    private UUID selectedPolicyDocumentId;
    private String categorySelectionState;
    /** RM_SELECTED / CUSTOMER_SELECTED / AUTO_SINGLE_ELIGIBLE — frozen at pin time. */
    private String categorySelectionSource;
    private ApplicationStatus status;
    private Map<String, Object> personalInfo;
    private Map<String, Object> businessInfo;
    private Map<String, Object> financialInfo;
    private Map<String, Object> collateralInfo;
    private String remarks;
    private UUID assignedTo;

    // Flow orchestration fields
    private BigDecimal sanctionedAmount;
    private BigDecimal approvedRate;
    private BigDecimal disbursedAmount;
    private Instant disbursedAt;
    private String lmsReferenceId;
    private String esignTransactionId;
    private Boolean vkycRequired;
    private VkycStatus vkycStatus;
    private Instant vkycCompletedAt;
    private UUID vkycAgentId;
    private UUID vkycAuditorId;
    private String vkycReferenceId;
    private String vkycUrl;
    private String vkycTransactionId;
    private Instant vkycUrlGeneratedAt;
    private Instant vkycUrlExpiryAt;
    private Instant vkycLastResentAt;
    private Integer vkycResendCount;
    private Boolean vkycEmailSent;
    private Instant vkycEmailSentAt;
    private UUID vkycGeneratedBy;
    private String vkycLastEvent;
    private String vkycEventPayload;
    private String vkycResultPayload;
    private String vkycAgentName;
    private Instant vkycAgentUpdatedOn;
    private Instant vkycCompletedOn;
    private String vkycVideoUrl;
    private String vkycPanImageUrl;
    private String vkycFaceImageUrl;

    private VkycCompletionMode vkycCompletionMode;
    private String pkycReason;
    private String pkycComments;
    private UUID pkycDocumentId;
    private UUID pkycVerifiedBy;
    private Instant pkycVerifiedAt;

    private Boolean amlHit;
    private Integer bureauScore;
    private Integer manualBureauScore;
    private String manualBureauRemarks;
    private UUID manualBureauDocumentId;
    private String creditDecision;
    private Integer creditRiskScore;

    private UUID subProgramId;
    private UUID plpBorrowerId;
    private UUID plpSubProgramBorrowerId;
    private UUID plpBorrowerProgramMappingId;
    private PlpSyncStatus plpProgramSyncStatus;
    private String plpProgramSyncError;
    private Instant plpProgramSyncedAt;
    private PlpSyncStatus plpBorrowerSyncStatus;
    private Instant plpBorrowerSyncedAt;
    private PlpSyncStatus plpLinkSyncStatus;
    private Instant plpLinkSyncedAt;
    private PlpSyncStatus plpMappingSyncStatus;
    private Instant plpMappingSyncedAt;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;

    /** Provider vs manual credit layer + effective values for underwriting. */
    private Map<String, Object> creditControlView;
    /** Latest row from {@code underwriting_evaluations}. */
    private Map<String, Object> latestUnderwritingEvaluation;
    /** Current CAM workflow state when a memo exists (e.g. DRAFT, SUBMITTED, SENT_BACK, APPROVED). */
    private String camStatus;

    /** True when KYC identity fields changed after the last successful verification run. */
    private Boolean kycInputsModifiedSinceVerify;
    private java.util.List<String> kycInputChangeSummary;
    /** True when intake/KYC fields changed after the latest staff send-back snapshot. */
    private Boolean intakeModifiedSinceSendBack;
    private java.util.List<String> intakeChangeSummary;
}
