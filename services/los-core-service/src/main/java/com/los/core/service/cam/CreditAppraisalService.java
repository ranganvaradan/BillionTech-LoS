package com.los.core.service.cam;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.CamUpdateRequest;
import com.los.core.model.dto.response.CamResponse;
import com.los.core.model.entity.CreditAppraisalMemo;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.UnderwritingEvaluation;
import com.los.core.repository.CreditAppraisalMemoRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.UnderwritingEvaluationRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.credit.CreditControlKeys;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Credit Appraisal Memo — auto-builds structured sections from application, KYC, and underwriting data.
 */
@Service
@RequiredArgsConstructor
public class CreditAppraisalService {

    private final CreditAppraisalMemoRepository camRepository;
    private final LoanApplicationRepository applicationRepository;
    private final UnderwritingEvaluationRepository underwritingEvaluationRepository;
    private final UnderwritingScorecardRepository scorecardRepository;
    private final IKycOrchestrationService kycOrchestrationService;
    private final CreditControlService creditControlService;

    private static final DateTimeFormatter ZONED_TS =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z").withZone(ZoneId.systemDefault());

    public CamResponse getCam(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        CreditAppraisalMemo cam = camRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("CAM not generated yet for: " + applicationId));
        return toResponse(cam, app);
    }

    @Transactional
    public CamResponse updateCam(UUID applicationId, CamUpdateRequest req) {
        CreditAppraisalMemo cam = camRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("CAM not found: " + applicationId));
        String cst = cam.getCamStatus() != null ? cam.getCamStatus() : "DRAFT";
        if ("APPROVED".equalsIgnoreCase(cst)) {
            throw new BusinessRuleException(
                    "CAM is approved and locked for routine edits. Use an administrator workflow for overrides, or add remarks only in the system of record outside this field set.",
                    "CAM_LOCKED",
                    "OPEN_CAM",
                    Map.of("camStatus", cst));
        }
        if (req.getObservations() != null) {
            cam.setObservations(req.getObservations());
        }
        if (req.getRiskAssessment() != null) {
            cam.setRiskAssessment(req.getRiskAssessment());
        }
        if (req.getMitigants() != null) {
            cam.setMitigants(req.getMitigants());
        }
        if (req.getRecommendedDecision() != null) {
            cam.setRecommendedDecision(req.getRecommendedDecision().trim().toUpperCase(Locale.ROOT));
        }
        if (req.getRecommendedAmount() != null) {
            cam.setRecommendedAmount(req.getRecommendedAmount());
        }
        if (req.getRecommendedTenureMonths() != null) {
            cam.setRecommendedTenureMonths(req.getRecommendedTenureMonths());
        }
        if (req.getRecommendedRate() != null) {
            cam.setRecommendedRate(req.getRecommendedRate());
        }
        if (req.getConditionsPrecedent() != null) {
            cam.setConditionsPrecedentJson(new ArrayList<>(req.getConditionsPrecedent()));
        }
        if (req.getConditionsSubsequent() != null) {
            cam.setConditionsSubsequentJson(new ArrayList<>(req.getConditionsSubsequent()));
        }
        if (req.getCreditOfficerRemarks() != null) {
            cam.setCreditOfficerRemarks(req.getCreditOfficerRemarks());
        }
        if (req.getCreditManagerRemarks() != null) {
            cam.setCreditManagerRemarks(req.getCreditManagerRemarks());
        }
        if (req.getEditableSectionsPatch() != null && !req.getEditableSectionsPatch().isEmpty()) {
            Map<String, Object> m = cam.getEditableSectionsJson() != null
                    ? new LinkedHashMap<>(cam.getEditableSectionsJson()) : new LinkedHashMap<>();
            m.putAll(req.getEditableSectionsPatch());
            cam.setEditableSectionsJson(m);
        }
        if ("REJECTED".equalsIgnoreCase(cst)) {
            cam.setCamStatus("DRAFT");
        }
        cam = camRepository.save(cam);
        LoanApplication app = applicationRepository.findById(applicationId).orElseThrow();
        return toResponse(cam, app);
    }

    /**
     * Officer submits CAM for credit manager review (stays in CAM_READY; does not change application status).
     */
    @Transactional
    public CamResponse submitCam(UUID applicationId) {
        CreditAppraisalMemo cam = camRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("CAM not found: " + applicationId));
        String st = cam.getCamStatus() != null ? cam.getCamStatus() : "DRAFT";
        if (!"DRAFT".equals(st) && !"SENT_BACK".equals(st) && !"REJECTED".equals(st)) {
            throw new BusinessRuleException(
                    "Only DRAFT, SENT_BACK, or REJECTED CAM can be submitted for review. Current: " + st,
                    "CAM_SUBMIT_INVALID",
                    "OPEN_CAM",
                    Map.of("camStatus", st));
        }
        cam.setCamStatus("SUBMITTED");
        cam.setSubmittedAt(Instant.now());
        cam = camRepository.save(cam);
        LoanApplication app = applicationRepository.findById(applicationId).orElseThrow();
        return toResponse(cam, app);
    }

    /**
     * Credit manager sends the CAM back to the officer (SUBMITTED → SENT_BACK). Application status unchanged.
     */
    @Transactional
    public CamResponse sendBackCam(UUID applicationId) {
        CreditAppraisalMemo cam = camRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("CAM not found: " + applicationId));
        String st = cam.getCamStatus() != null ? cam.getCamStatus() : "DRAFT";
        if (!"SUBMITTED".equals(st)) {
            throw new BusinessRuleException(
                    "Send back is only for SUBMITTED CAM. Current: " + st,
                    "CAM_SENDBACK_INVALID",
                    "OPEN_CAM",
                    Map.of("camStatus", st));
        }
        cam.setCamStatus("SENT_BACK");
        cam = camRepository.save(cam);
        LoanApplication app = applicationRepository.findById(applicationId).orElseThrow();
        return toResponse(cam, app);
    }

    /**
     * Manager rejects the CAM draft (does not change application status; officer may re-edit and resubmit).
     */
    @Transactional
    public CamResponse rejectMemorandum(UUID applicationId) {
        CreditAppraisalMemo cam = camRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("CAM not found: " + applicationId));
        String st = cam.getCamStatus() != null ? cam.getCamStatus() : "DRAFT";
        if (!"SUBMITTED".equals(st)) {
            throw new BusinessRuleException(
                    "Manager reject applies to SUBMITTED CAM only. Current: " + st,
                    "CAM_REJECT_INVALID",
                    "OPEN_CAM",
                    Map.of("camStatus", st));
        }
        cam.setCamStatus("REJECTED");
        cam = camRepository.save(cam);
        LoanApplication app = applicationRepository.findById(applicationId).orElseThrow();
        return toResponse(cam, app);
    }

    @Transactional
    public CreditAppraisalMemo ensureCamForApplication(LoanApplication app) {
        Map<String, Object> built = buildCamJson(app);
        CreditAppraisalMemo cam = camRepository.findByApplicationId(app.getId())
                .orElseGet(() -> CreditAppraisalMemo.builder().applicationId(app.getId()).build());
        cam.setCamJson(built);
        if (cam.getCamVersion() == null) {
            cam.setCamVersion(1);
        }
        if (cam.getCamStatus() == null) {
            cam.setCamStatus("DRAFT");
        }
        if (cam.getRecommendedDecision() == null) {
            cam.setRecommendedDecision(suggestDecision(app));
        }
        return camRepository.save(cam);
    }

    @Transactional
    public void markReviewed(UUID applicationId, UUID approvedByUserId) {
        CreditAppraisalMemo cam = camRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("CAM not found: " + applicationId));
        String st = cam.getCamStatus() != null ? cam.getCamStatus() : "DRAFT";
        if ("APPROVED".equalsIgnoreCase(st)) {
            throw new BusinessRuleException(
                    "CAM is already approved", "CAM_ALREADY_APPROVED", "OPEN_CAM", Map.of("camStatus", st));
        }
        cam.setCamReviewed(true);
        cam.setCamStatus("APPROVED");
        cam.setApprovedAt(Instant.now());
        if (approvedByUserId != null) {
            cam.setApprovedByUserId(approvedByUserId);
        }
        camRepository.save(cam);
    }

    public byte[] renderCamPdf(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        CreditAppraisalMemo cam = camRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("CAM not found: " + applicationId));
        Optional<UnderwritingEvaluation> latestUw = underwritingEvaluationRepository
                .findTopByApplicationIdOrderByEvaluatedAtDesc(applicationId);
        return renderPdf(cam, app, latestUw.orElse(null));
    }

    private static String suggestDecision(LoanApplication app) {
        String d = app.getCreditDecision();
        if (d == null) {
            return "MANUAL_REVIEW";
        }
        if ("APPROVED".equalsIgnoreCase(d)) {
            return "APPROVE";
        }
        if ("REJECTED".equalsIgnoreCase(d) || "REJECT".equalsIgnoreCase(d)) {
            return "REJECT";
        }
        if ("MANUAL_REVIEW".equalsIgnoreCase(d)) {
            return "MANUAL_REVIEW";
        }
        return "MANUAL_REVIEW";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildCamJson(LoanApplication app) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", Instant.now().toString());
        root.put("applicationNumber", app.getApplicationNumber());

        Map<String, Object> s1 = new LinkedHashMap<>();
        Map<String, Object> pi = app.getPersonalInfo() != null ? app.getPersonalInfo() : Map.of();
        s1.put("name", firstNonBlank(str(pi.get("fullName")),
                joinName(pi.get("firstName"), pi.get("lastName")),
                str(pi.get("name"))));
        s1.put("borrowerType", app.getBorrowerType().name());
        s1.put("loanProduct", app.getLoanProduct());
        Map<String, Object> loan = new LinkedHashMap<>();
        loan.put("requestedAmount", app.getRequestedAmount() != null ? app.getRequestedAmount().toPlainString() : null);
        loan.put("tenureMonths", app.getTenureMonths());
        loan.put("interestRate", app.getInterestRate() != null ? app.getInterestRate().toPlainString() : null);
        s1.put("requestedLoanDetails", loan);
        root.put("section1_applicantSummary", s1);

        Map<String, Object> kycMap = new LinkedHashMap<>();
        try {
            Map<String, Object> outcome = kycOrchestrationService.computeKycOutcome(app.getId());
            kycMap.put("outcome", String.valueOf(outcome.getOrDefault("outcome", "—")));
            Object ex = outcome.get("exceptions");
            kycMap.put("exceptions", ex instanceof List ? ex : List.of());
        } catch (Exception e) {
            kycMap.put("outcome", "UNAVAILABLE");
            kycMap.put("exceptions", List.of("Could not load KYC outcome: " + e.getMessage()));
        }
        kycMap.put("panVerified", str(pi.get("panVerified")));
        kycMap.put("aadhaarVerified", str(pi.get("aadhaarVerified")));
        root.put("section2_kycSummary", kycMap);

        Map<String, Object> cr = new LinkedHashMap<>();
        int bureau = app.getManualBureauScore() != null && app.getManualBureauScore() > 0
                ? app.getManualBureauScore()
                : (app.getBureauScore() != null ? app.getBureauScore() : 0);
        cr.put("bureauScore", bureau > 0 ? bureau : null);
        cr.put("creditControlSummary", creditControlService.buildReadView(app));
        root.put("section3_creditSummary", cr);

        Map<String, Object> uw = new LinkedHashMap<>();
        uw.put("creditDecision", app.getCreditDecision());
        uw.put("riskScore", app.getCreditRiskScore());
        Optional<UnderwritingEvaluation> ev = underwritingEvaluationRepository.findTopByApplicationIdOrderByEvaluatedAtDesc(
                app.getId());
        if (ev.isPresent()) {
            UnderwritingEvaluation e = ev.get();
            uw.put("aggregateDecision", e.getAggregateDecision());
            uw.put("aggregateScore", e.getAggregateScore());
            uw.put("parameterResults", e.getParameterResultsJson() != null ? e.getParameterResultsJson() : List.of());
            uw.put("ruleResults", e.getRuleResultsJson() != null ? e.getRuleResultsJson() : List.of());
            uw.put("scorecardId", e.getScorecardId() != null ? e.getScorecardId().toString() : null);
        }
        Map<String, Object> fi = app.getFinancialInfo() != null ? app.getFinancialInfo() : Map.of();
        Object uwMeta = fi.get("underwritingMeta");
        uw.put("policySnapshot", uwMeta instanceof Map ? uwMeta : Map.of());
        root.put("section4_underwritingSummary", uw);

        root.put("section5_creditManagerInputs", Map.of(
                "observations", "",
                "riskAssessment", "",
                "mitigants", ""));
        root.put("section6_recommendation", Map.of("suggestedDecision", suggestDecision(app)));

        root.put("sectionExtended", buildSectionExtended(app, pi, kycMap, cr, uw, ev.orElse(null)));

        return root;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSectionExtended(
            LoanApplication app,
            Map<String, Object> pi,
            Map<String, Object> kycMap,
            Map<String, Object> s3,
            @SuppressWarnings("unused") Map<String, Object> uw,
            UnderwritingEvaluation ev) {
        Map<String, Object> out = new LinkedHashMap<>();
        String name = firstNonBlank(
                str(pi.get("fullName")),
                joinName(pi.get("firstName"), pi.get("lastName")),
                str(pi.get("name")));
        String rec = app.getCreditDecision() != null ? app.getCreditDecision() : "—";
        String riskG = "—";
        if (ev != null && ev.getAggregateDecision() != null) {
            riskG = ev.getAggregateDecision();
        } else if (app.getCreditRiskScore() != null) {
            riskG = "Model score: " + app.getCreditRiskScore();
        }
        Map<String, String> es = new LinkedHashMap<>();
        es.put("Borrower", name != null ? name : "—");
        es.put("Product", nullToEmpty(str(app.getLoanProduct())));
        es.put("Amount requested (INR)", app.getRequestedAmount() != null ? inrDisplay(app.getRequestedAmount()) : "—");
        es.put("System recommendation", rec);
        es.put("Risk / policy view", riskG);
        es.put("As-of timestamp", ZONED_TS.format(Instant.now()));
        out.put("executiveSummary", es);

        Map<String, Object> bp = new LinkedHashMap<>();
        bp.put("Borrower type", app.getBorrowerType() != null ? app.getBorrowerType().name() : "—");
        bp.put("Mobile", nullToEmpty(firstNonBlank(str(pi.get("mobile")), str(pi.get("phoneNumber")))));
        bp.put("Email", nullToEmpty(str(pi.get("email"))));
        bp.put("City", nullToEmpty(str(pi.get("city"))));
        bp.put("State", nullToEmpty(str(pi.get("state"))));
        bp.put("Employment / business", nullToEmpty(firstNonBlank(str(pi.get("employmentType")), str(pi.get("occupationIndustry")))));
        bp.put("Monthly income (declared in profile)", nullToEmpty(str(pi.get("monthlyNetIncome"))));
        if (app.getBusinessInfo() != null) {
            Map<String, Object> bi = app.getBusinessInfo();
            bp.put("GSTIN (on file)", nullToEmpty(str(bi.get("gstin"))));
            bp.put("Udyam (on file)", nullToEmpty(str(bi.get("udyamNumber"))));
        }
        out.put("borrowerProfile", bp);

        Map<String, String> lr = new LinkedHashMap<>();
        lr.put("Requested amount (INR)", app.getRequestedAmount() != null ? inrDisplay(app.getRequestedAmount()) : "—");
        lr.put("Tenure (months)", app.getTenureMonths() != null ? String.valueOf(app.getTenureMonths()) : "—");
        lr.put("Product / programme", nullToEmpty(str(app.getLoanProduct())));
        lr.put("Stated interest (if any)", app.getInterestRate() != null ? app.getInterestRate().toPlainString() + " % p.a." : "—");
        out.put("loanRequest", lr);

        List<String> ex = new ArrayList<>();
        Object rawEx = kycMap.get("exceptions");
        if (rawEx instanceof List<?> l) {
            for (Object o : l) {
                ex.add(String.valueOf(o));
            }
        }
        Map<String, String> kc = new LinkedHashMap<>();
        kc.put("KYC outcome", nullToEmpty(str(kycMap.get("outcome"))));
        kc.put("PAN (verified flag on file)", nullToEmpty(str(kycMap.get("panVerified"))));
        kc.put("Aadhaar (verified flag on file)", nullToEmpty(str(kycMap.get("aadhaarVerified"))));
        kc.put("Open items / messages", ex.isEmpty() ? "None listed" : String.join("; ", ex));
        out.put("kycCompliance", kc);

        Map<String, Object> be = new LinkedHashMap<>();
        int bScore = app.getManualBureauScore() != null && app.getManualBureauScore() > 0
                ? app.getManualBureauScore()
                : (app.getBureauScore() != null ? app.getBureauScore() : 0);
        be.put("Bureau score used in decisioning", bScore > 0 ? String.valueOf(bScore) : "—");
        be.put("Provider pull (automated) score on file", app.getBureauScore() != null && app.getBureauScore() > 0
                ? String.valueOf(app.getBureauScore()) : "—");
        be.put("Manual override (if any)", app.getManualBureauScore() != null && app.getManualBureauScore() > 0
                ? String.valueOf(app.getManualBureauScore()) : "—");
        if (s3.get("creditControlSummary") instanceof Map<?, ?> ccs) {
            Object em = ((Map<String, Object>) ccs).get("effective");
            if (em instanceof Map<?, ?> efm) {
                be.put("Bureau data source (effective layer)", str(efm.get("bureauScoreSource")));
            }
        }
        out.put("bureauCredit", be);

        Map<String, Object> inc = new LinkedHashMap<>();
        extractManualAndEffectiveIncome(app, inc);
        out.put("incomeCashflow", inc);

        if (requiresCollateralForProduct(str(app.getLoanProduct())) && app.getCollateralInfo() != null) {
            Object bint = app.getCollateralInfo().get("borrowerIntake");
            if (bint instanceof Map<?, ?> cim) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("Collateral type", str(cim.get("collateralType")).replace('_', ' '));
                c.put("Estimated value (INR, declared in intake)", str(cim.get("estimatedValue")));
                c.put("Intake notes", str(cim.get("notes")));
                out.put("collateral", c);
            }
        }

        if (ev != null) {
            String scn = "—";
            if (ev.getScorecardId() != null) {
                scn = scorecardRepository.findById(ev.getScorecardId())
                        .map(UnderwritingScorecard::getName)
                        .orElse("—");
            }
            Map<String, Object> se = new LinkedHashMap<>();
            se.put("Matched scorecard (name on engine)", scn);
            se.put("Aggregate score", ev.getAggregateScore() != null ? String.valueOf(ev.getAggregateScore()) : "—");
            se.put("Policy outcome (aggregate)", str(ev.getAggregateDecision()));
            se.put("Parameter breakdown (rows)", buildParameterTable(ev));
            se.put("Hard / negative policy rules", buildHardRulesSummary(ev));
            out.put("scorecardEvaluation", se);
        } else {
            out.put("scorecardEvaluation", Map.of("Note", "No scorecard/underwriting run is stored for this build."));
        }

        out.put("assignment", readAssignmentInfo(app));
        out.put("riskFlags", deriveRiskFlags(app, kycMap, ev, bScore));
        out.put("missingItems", listMissingDataPoints(app, ev, bScore, kycMap, name));
        out.put("completenessPercent", Integer.valueOf(computeCompleteness(name, bScore, kycMap, ev, app)));
        out.put("approvalsPlaceholder", new LinkedHashMap<>(Map.of(
                "Prepared by (credit processing)", "—",
                "Reviewed by (credit manager)", "—",
                "Approved by (signatory / delegate)", "—",
                "Notes", "Final signatures are recorded in the LOS approval trail; this PDF is a working summary.")));
        return out;
    }

    @SuppressWarnings("unchecked")
    private void extractManualAndEffectiveIncome(LoanApplication app, Map<String, Object> inc) {
        Map<String, Object> fi = app.getFinancialInfo() != null ? app.getFinancialInfo() : Map.of();
        Object cc = fi.get(CreditControlKeys.ROOT);
        if (cc instanceof Map<?, ?> ccm) {
            Object man = ((Map<String, Object>) ccm).get(CreditControlKeys.MANUAL);
            if (man instanceof Map<?, ?> m) {
                for (String k : new String[]{
                        "monthlyIncome", "gstIncome", "bankStatementIncome", "averageBankBalance", "obligationRatio"}) {
                    Object o = m.get(k);
                    if (o == null) {
                        continue;
                    }
                    if (o instanceof Map<?, ?> cell && cell.get("value") != null) {
                        inc.put(prettyKeyIncome(k), str(cell.get("value")));
                    }
                }
            }
        }
        if (app.getPersonalInfo() != null && app.getPersonalInfo().get("monthlyNetIncome") != null) {
            inc.putIfAbsent("Declared monthly income (intake profile)", str(app.getPersonalInfo().get("monthlyNetIncome")));
        }
    }

    private static String prettyKeyIncome(String k) {
        if (k == null) {
            return "Field";
        }
        return k.replace("monthlyIncome", "Monthly income (verified layer / manual input)")
                .replace("gstIncome", "GST-based income (verified layer / manual input)")
                .replace("bankStatementIncome", "Bank statement income (verified layer / manual input)")
                .replace("averageBankBalance", "Average bank balance (derived / manual input)")
                .replace("obligationRatio", "Obligation / FOIR (derived ratio)");
    }

    private List<Map<String, String>> buildParameterTable(UnderwritingEvaluation ev) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (ev.getParameterResultsJson() == null) {
            return rows;
        }
        for (Object o : ev.getParameterResultsJson()) {
            if (!(o instanceof Map<?, ?> p)) {
                continue;
            }
            Map<String, String> r = new LinkedHashMap<>();
            r.put("Parameter", str(p.get("parameter")));
            r.put("Value used", str(p.get("valueUsed")));
            r.put("Source", str(p.get("valueSource")));
            String pe = str(p.get("pointsEarned"));
            String max = str(p.get("maxScore"));
            r.put("Points", (pe + " / " + max).trim());
            r.put("Comment / attachment", str(p.get("attachment")));
            rows.add(r);
        }
        return rows;
    }

    private String buildHardRulesSummary(UnderwritingEvaluation ev) {
        if (ev.getRuleResultsJson() == null || ev.getRuleResultsJson().isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : ev.getRuleResultsJson()) {
            if (!(o instanceof Map<?, ?> p)) {
                continue;
            }
            String pd = str(p.get("policyDecision"));
            String name = str(p.get("ruleName"));
            if (name == null) {
                name = "Rule";
            }
            boolean hard = false;
            if (pd != null) {
                String u = pd.toUpperCase(Locale.ROOT);
                hard = u.contains("FAIL") || u.contains("HARD");
            }
            if (!hard) {
                String cr = str(p.get("creditDecision"));
                if (cr != null) {
                    String u = cr.toUpperCase(Locale.ROOT);
                    hard = u.contains("REJECT");
                }
            }
            if (hard) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(name);
            }
        }
        return sb.isEmpty() ? "No hard negative rule hits in this engine run (see rule detail in LOS if needed)."
                : sb.toString();
    }

    private Map<String, String> readAssignmentInfo(LoanApplication app) {
        Map<String, Object> fi = app.getFinancialInfo() != null ? app.getFinancialInfo() : Map.of();
        Object a = fi.get("assignmentInfo");
        if (a instanceof Map<?, ?> m) {
            Map<String, String> r = new LinkedHashMap<>();
            for (var e : m.entrySet()) {
                r.put(humanizeLabel(String.valueOf(e.getKey())), e.getValue() != null ? e.getValue().toString() : "—");
            }
            return r;
        }
        return Map.of("Status", "No assignment block on this application (rules may not have fired).");
    }

    private static String humanizeLabel(String k) {
        if (k == null) {
            return "Field";
        }
        if ("assignedUserId".equals(k)) {
            return "Assigned user (id)";
        }
        if ("assignedAt".equals(k)) {
            return "Assigned at";
        }
        if ("assignedRole".equals(k)) {
            return "Assigned role";
        }
        if ("ruleName".equals(k)) {
            return "Rule name";
        }
        return k;
    }

    private List<String> listMissingDataPoints(
            LoanApplication app, UnderwritingEvaluation ev, int bureauScore, Map<String, Object> kycMap, String name) {
        List<String> m = new ArrayList<>();
        if (name == null || name.isBlank()) {
            m.add("Primary borrower / business name (complete in profile).");
        }
        if (bureauScore <= 0) {
            m.add("Bureau score on file (pull or allowed manual).");
        }
        String kycOut = str(kycMap.get("outcome"));
        if (kycOut == null || "UNAVAILABLE".equalsIgnoreCase(kycOut) || "INCOMPLETE".equalsIgnoreCase(kycOut)) {
            m.add("KYC pass outcome and supporting checks (current outcome: " + (kycOut != null ? kycOut : "—")
                    + ").");
        }
        if (ev != null && ev.getParameterResultsJson() != null) {
            for (Object o : ev.getParameterResultsJson()) {
                if (o instanceof Map<?, ?> p) {
                    if (Boolean.FALSE.equals(p.get("matched")) && p.get("valueUsed") == null) {
                        String par = str(p.get("parameter"));
                        if (par != null) {
                            m.add("Input for score parameter: " + par);
                        }
                    }
                }
            }
        }
        if (requiresCollateralForProduct(str(app.getLoanProduct())) && app.getCollateralInfo() != null) {
            Object b = app.getCollateralInfo().get("borrowerIntake");
            if (!(b instanceof Map<?, ?>) || str(((Map<?, ?>) b).get("estimatedValue")) == null) {
                m.add("Collateral intake / declared value (secured product).");
            }
        }
        return m;
    }

    private int computeCompleteness(
            String name, int bureauScore, Map<String, Object> kycMap, UnderwritingEvaluation ev, LoanApplication app) {
        int max = 8;
        int n = 0;
        if (name != null && !name.isBlank()) {
            n++;
        }
        if (bureauScore > 0) {
            n++;
        }
        String ko = str(kycMap.get("outcome"));
        if (ko != null && !ko.isBlank() && !"UNAVAILABLE".equalsIgnoreCase(ko)) {
            n++;
        }
        if (str(kycMap.get("panVerified")) != null) {
            n++;
        }
        if (app.getRequestedAmount() != null && app.getRequestedAmount().compareTo(BigDecimal.ZERO) > 0) {
            n++;
        }
        if (app.getPersonalInfo() != null && str(app.getPersonalInfo().get("monthlyNetIncome")) != null) {
            n++;
        }
        if (ev != null) {
            n++;
        }
        if (ev != null && ev.getParameterResultsJson() != null && !ev.getParameterResultsJson().isEmpty()) {
            n++;
        }
        return (int) Math.round(100.0 * n / max);
    }

    private List<Map<String, String>> deriveRiskFlags(
            LoanApplication app, Map<String, Object> kycMap, UnderwritingEvaluation ev, int bureauScore) {
        List<Map<String, String>> flags = new ArrayList<>();
        if (bureauScore > 0 && bureauScore < 600) {
            addFlag(flags, "LOW_BUREAU", "Bureau score is below a typical cut-off band for prime pricing — review with policy.", "MEDIUM");
        }
        String ko = str(kycMap.get("outcome"));
        if (ko != null && !"PASS".equalsIgnoreCase(ko) && !"UNAVAILABLE".equalsIgnoreCase(ko)) {
            addFlag(flags, "KYC", "KYC is not a clean pass at snapshot time — " + ko + ".", "HIGH");
        }
        if (ev != null && ev.getParameterResultsJson() != null) {
            for (Object o : ev.getParameterResultsJson()) {
                if (o instanceof Map<?, ?> p
                        && Boolean.FALSE.equals(p.get("matched"))
                        && p.get("valueUsed") == null) {
                    addFlag(
                            flags,
                            "MISSING_SCORE_PARAM",
                            "Scorecard could not use value for: " + str(p.get("parameter")) + " — capture source input.",
                            "MEDIUM");
                }
            }
        }
        if (app.getPersonalInfo() != null && str(app.getPersonalInfo().get("monthlyNetIncome")) == null) {
            addFlag(flags, "NO_DECL_INCOME", "No declared income on the intake profile (verify via GST/bank or manual).", "LOW");
        }
        if (flags.isEmpty()) {
            addFlag(
                    flags, "CLEAN", "No automated blockers flagged at this snapshot (still subject to final credit judgement).", "LOW");
        }
        return flags;
    }

    private void addFlag(List<Map<String, String>> list, String code, String label, String severity) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("code", code);
        f.put("label", label);
        f.put("severity", severity);
        list.add(f);
    }

    private static boolean requiresCollateralForProduct(String product) {
        if (product == null) {
            return false;
        }
        String n = product.trim().toLowerCase(Locale.ROOT);
        return n.contains("lap")
                || n.contains("loan against property")
                || n.equals("las")
                || n.contains("loan against shares")
                || n.equals("gold loan")
                || n.contains("gold");
    }

    @SuppressWarnings("unchecked")
    private CamResponse toResponse(CreditAppraisalMemo cam, LoanApplication app) {
        Map<String, Object> j = cam.getCamJson() != null ? cam.getCamJson() : Map.of();
        return CamResponse.builder()
                .applicationId(app.getId())
                .applicationStatus(app.getStatus().name())
                .section1ApplicantSummary(castMap(j.get("section1_applicantSummary")))
                .section2KycSummary(castMap(j.get("section2_kycSummary")))
                .section3CreditSummary(castMap(j.get("section3_creditSummary")))
                .section4UnderwritingSummary(castMap(j.get("section4_underwritingSummary")))
                .sectionExtended(castMap(j.get("sectionExtended")))
                .section5Observations(cam.getObservations())
                .section5RiskAssessment(cam.getRiskAssessment())
                .section5Mitigants(cam.getMitigants())
                .section6RecommendedDecision(
                        cam.getRecommendedDecision() != null ? cam.getRecommendedDecision() : suggestDecision(app))
                .camReviewed(cam.isCamReviewed())
                .camVersion(cam.getCamVersion() != null ? cam.getCamVersion() : 1)
                .camStatus(cam.getCamStatus() != null ? cam.getCamStatus() : "DRAFT")
                .recommendedAmount(cam.getRecommendedAmount())
                .recommendedTenureMonths(cam.getRecommendedTenureMonths())
                .recommendedRate(cam.getRecommendedRate())
                .conditionsPrecedent(cam.getConditionsPrecedentJson())
                .conditionsSubsequent(cam.getConditionsSubsequentJson())
                .creditOfficerRemarks(cam.getCreditOfficerRemarks())
                .creditManagerRemarks(cam.getCreditManagerRemarks())
                .submittedAt(cam.getSubmittedAt() != null ? cam.getSubmittedAt().toString() : null)
                .approvedAt(cam.getApprovedAt() != null ? cam.getApprovedAt().toString() : null)
                .updatedAt(cam.getUpdatedAt() != null ? cam.getUpdatedAt().toString() : null)
                .createdAt(cam.getCreatedAt() != null ? cam.getCreatedAt().toString() : null)
                .approvedByUserId(cam.getApprovedByUserId())
                .editableSections(cam.getEditableSectionsJson() != null
                        ? new LinkedHashMap<>(cam.getEditableSectionsJson())
                        : Map.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) o);
        }
        return Map.of();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().trim();
    }

    private static String firstNonBlank(String... s) {
        if (s == null) {
            return null;
        }
        for (String x : s) {
            if (x != null && !x.isBlank()) {
                return x;
            }
        }
        return null;
    }

    private static String joinName(Object a, Object b) {
        String f = a != null ? a.toString().trim() : "";
        String l = b != null ? b.toString().trim() : "";
        String j = (f + " " + l).trim();
        return j.isEmpty() ? null : j;
    }

    @SuppressWarnings("unchecked")
    private byte[] renderPdf(CreditAppraisalMemo cam, LoanApplication app, UnderwritingEvaluation latestUw) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Font title = new Font(Font.HELVETICA, 15, Font.BOLD, new Color(0, 33, 71));
            Font h = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0, 33, 71));
            Font body = new Font(Font.HELVETICA, 8.5f, Font.NORMAL);
            Font small = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color.DARK_GRAY);
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new CamPdfPageEvent(small, "Billionloans Financial Services Pvt Ltd – Credit Appraisal Memo"));
            document.open();

            document.add(new Paragraph("Billionloans Financial Services Pvt Ltd", new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0, 33, 71))));
            document.add(new Paragraph("Credit Appraisal Memo", title));
            document.add(new Paragraph("Application: " + app.getApplicationNumber() + "  ·  Prepared on: " + ZONED_TS.format(Instant.now()), body));
            document.add(new Paragraph("Application status: " + app.getStatus().name() + "  ·  CAM: " + nullToEmpty(cam.getCamStatus()) + "  ·  Version: " + (cam.getCamVersion() != null ? cam.getCamVersion() : 1), body));
            document.add(Chunk.NEWLINE);

            Map<String, Object> j = cam.getCamJson() != null ? cam.getCamJson() : Map.of();
            Map<String, Object> ext = castMap(j.get("sectionExtended"));
            Map<String, Object> ovr = cam.getEditableSectionsJson() != null
                    ? new LinkedHashMap<>(cam.getEditableSectionsJson()) : new LinkedHashMap<>();
            for (String key : new String[]{
                    "executiveSummary", "borrowerProfile", "loanRequest", "kycCompliance", "bureauCredit",
                    "incomeCashflow", "collateral", "scorecardEvaluation", "assignment", "riskFlags", "approvalsPlaceholder"}) {
                if (!ext.containsKey(key) && !ovr.containsKey(key) && !ovr.containsKey(key + "Narrative")) {
                    continue;
                }
                String title1 = mapTitle(key);
                document.add(pdfSectionHeading(h, title1));
                Object raw = ovr.get(key) != null ? ovr.get(key) : (ovr.get(key + "Narrative") != null
                        ? ovr.get(key + "Narrative")
                        : ext.get(key));
                if (raw == null) {
                    continue;
                }
                if (raw instanceof String s) {
                    document.add(pdfBlockParagraphs(s, body));
                } else if (raw instanceof Map) {
                    if ("scorecardEvaluation".equals(key)) {
                        Map<String, Object> m = (Map<String, Object>) raw;
                        Map<String, Object> copy = new LinkedHashMap<>(m);
                        Object br = copy.remove("Parameter breakdown (rows)");
                        if (!copy.isEmpty()) {
                            document.add(pdfKeyValueTable(copy, small, body));
                        }
                        if (br instanceof List<?> rows && !rows.isEmpty()) {
                            document.add(pdfSectionHeading(new Font(Font.HELVETICA, 9, Font.BOLD, new Color(0, 33, 71)), "Parameter breakdown"));
                            document.add(pdfParameterDetailTable((List<?>) br, h, small, body));
                        }
                    } else {
                        document.add(pdfKeyValueTable((Map<String, Object>) raw, small, body));
                    }
                } else if (raw instanceof List && "riskFlags".equals(key)) {
                    document.add(pdfFlagsTable((List<?>) raw, h, small, body));
                }
                document.add(Chunk.NEWLINE);
            }
            if (latestUw != null) {
                document.add(pdfSectionHeading(h, "Engine / rule run (summary)"));
                StringBuilder sb = new StringBuilder();
                if (latestUw.getScorecardId() != null) {
                    sb.append("Scorecard id on run: ");
                    scorecardRepository.findById(latestUw.getScorecardId()).ifPresent(
                            s -> sb.append(s.getName()).append(" · "));
                }
                if (latestUw.getAggregateScore() != null) {
                    sb.append("Aggregate score: ").append(latestUw.getAggregateScore());
                }
                if (sb.length() > 0) {
                    document.add(new Paragraph(sb.toString(), body));
                }
            }

            document.add(pdfSectionHeading(h, "Verified manual and supporting values"));
            document.add(new Paragraph(pdfManualInputs(app), body));
            document.add(Chunk.NEWLINE);
            document.add(pdfSectionHeading(h, "Credit team narrative"));
            document.add(pdfKeyValueTable(new LinkedHashMap<>(Map.of(
                    "Observations", nullToEmpty(cam.getObservations()),
                    "Risk assessment", nullToEmpty(cam.getRiskAssessment()),
                    "Mitigants", nullToEmpty(cam.getMitigants()))), small, body));
            document.add(Chunk.NEWLINE);
            document.add(pdfSectionHeading(h, "Recommendation to sanctioning authority"));
            Map<String, String> rec = new LinkedHashMap<>();
            rec.put("Recommended decision (CAM)", nullToEmpty(cam.getRecommendedDecision()));
            rec.put("Proposed amount (INR)", cam.getRecommendedAmount() != null ? inrDisplay(cam.getRecommendedAmount()) : "—");
            rec.put("Proposed tenure (months)", cam.getRecommendedTenureMonths() != null ? String.valueOf(cam.getRecommendedTenureMonths()) : "—");
            rec.put("Proposed interest rate (% p.a.)", cam.getRecommendedRate() != null ? cam.getRecommendedRate().toPlainString() : "—");
            rec.put("Conditions precedent", listToReadable(cam.getConditionsPrecedentJson()));
            rec.put("Conditions subsequent", listToReadable(cam.getConditionsSubsequentJson()));
            rec.put("Credit officer remarks", nullToEmpty(cam.getCreditOfficerRemarks()));
            rec.put("Credit manager remarks", nullToEmpty(cam.getCreditManagerRemarks()));
            rec.put("Manager approval status", cam.isCamReviewed() && "APPROVED".equalsIgnoreCase(str(cam.getCamStatus()))
                    ? "Recorded as approved" : "Not yet approved in system");
            if (cam.getApprovedAt() != null) {
                rec.put("Approved at (system)", ZONED_TS.format(cam.getApprovedAt()));
            }
            if (cam.getApprovedByUserId() != null) {
                rec.put("Approved by (user id)", shortId(cam.getApprovedByUserId().toString()));
            }
            document.add(pdfStringTable(rec, small, body));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph(
                    "This memorandum is generated from the loan operating system. Figures reflect what was on file at generation time. For regulatory and internal record, retain evidence uploaded against the case.",
                    small));
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException(
                    "CAM PDF generation failed: " + e.getMessage(), "CAM_PDF_FAILED", "RETRY", null);
        }
    }

    private String mapTitle(String key) {
        if (key == null) {
            return "Section";
        }
        return key.replace("executiveSummary", "Executive summary")
                .replace("borrowerProfile", "Borrower profile")
                .replace("loanRequest", "Loan request")
                .replace("kycCompliance", "KYC and compliance")
                .replace("bureauCredit", "Bureau and credit data")
                .replace("incomeCashflow", "Income and cashflow")
                .replace("collateral", "Collateral")
                .replace("scorecardEvaluation", "Policy / scorecard evaluation")
                .replace("assignment", "Case assignment")
                .replace("riskFlags", "Risk flags")
                .replace("approvalsPlaceholder", "Approvals and sign-off");
    }

    private static Paragraph pdfSectionHeading(Font f, String t) {
        return new Paragraph(t, f);
    }

    private static Paragraph pdfBlockParagraphs(String s, Font body) {
        String t = s.replace("\r", "").trim();
        if (t.length() > 4000) {
            t = t.substring(0, 4000) + " …";
        }
        return new Paragraph(t, body);
    }

    private static PdfPTable pdfKeyValueTable(Map<String, Object> m, Font labelFont, Font valFont) throws Exception {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[] {0.32f, 0.68f });
        t.setSpacingAfter(3f);
        for (var e : m.entrySet()) {
            t.addCell(pdfNoBorder(labelFont, e.getKey() != null ? e.getKey() : ""));
            t.addCell(pdfNoBorder(valFont, formatPdfCell(e.getValue())));
        }
        return t;
    }

    private static String formatPdfCell(Object o) {
        if (o == null) {
            return "—";
        }
        if (o instanceof List<?> l) {
            if (l.isEmpty()) {
                return "—";
            }
            return listToReadableLines(l);
        }
        if (o instanceof Map<?, ?> mm) {
            return mm.entrySet().stream()
                    .map(e1 -> (e1.getKey() != null ? e1.getKey() : "") + ": " + formatScalarPdf(e1.getValue()))
                    .reduce((a, b) -> a + " · " + b)
                    .orElse("—");
        }
        return String.valueOf(o);
    }

    private static String formatScalarPdf(Object o) {
        if (o == null) {
            return "—";
        }
        return o.toString().replace('\n', ' ').replace('{', ' ').replace('}', ' ').trim();
    }

    private static PdfPTable pdfStringTable(Map<String, String> m, Font labelFont, Font valFont) throws Exception {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[] {0.32f, 0.68f });
        t.setSpacingAfter(3f);
        for (var e : m.entrySet()) {
            t.addCell(pdfNoBorder(labelFont, e.getKey()));
            t.addCell(pdfNoBorder(valFont, e.getValue() != null ? e.getValue() : "—"));
        }
        return t;
    }

    private static PdfPTable pdfFlagsTable(List<?> rows, Font h, Font sm, Font body) throws Exception {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingAfter(3f);
        t.addCell(headerCell(h, "Risk note"));
        t.addCell(headerCell(h, "Severity"));
        for (Object o : rows) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            t.addCell(pdfNoBorder(body, str(m.get("label"))));
            t.addCell(pdfNoBorder(sm, str(m.get("severity"))));
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    private static PdfPTable pdfParameterDetailTable(List<?> rows, Font h, Font sm, Font body) throws Exception {
        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setSpacingAfter(3f);
        t.setWidths(new float[] {0.2f, 0.2f, 0.2f, 0.2f, 0.2f });
        t.addCell(headerCell(h, "Parameter"));
        t.addCell(headerCell(h, "Value"));
        t.addCell(headerCell(h, "Source"));
        t.addCell(headerCell(h, "Points"));
        t.addCell(headerCell(h, "Comment"));
        for (Object o : rows) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            t.addCell(pdfNoBorder(body, str(m.get("Parameter"))));
            t.addCell(pdfNoBorder(sm, shortText(str(m.get("Value used")), 80)));
            t.addCell(pdfNoBorder(sm, shortText(str(m.get("Source")), 50)));
            t.addCell(pdfNoBorder(sm, str(m.get("Points"))));
            t.addCell(pdfNoBorder(sm, shortText(str(m.get("Comment / attachment")), 80)));
        }
        return t;
    }

    private static String shortText(String s, int max) {
        if (s == null) {
            return "—";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    private static String listToReadableLines(List<?> l) {
        StringBuilder sb = new StringBuilder();
        for (Object o : l) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            if (o instanceof Map<?, ?> mm) {
                String lab = str(mm.get("label"));
                if (lab != null) {
                    sb.append(lab);
                } else {
                    sb.append(String.valueOf(o).replace('{', ' ').replace('}', ' '));
                }
            } else {
                sb.append(String.valueOf(o));
            }
        }
        return sb.isEmpty() ? "—" : sb.toString();
    }

    private static PdfPCell headerCell(Font f, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(new Color(240, 244, 250));
        c.setPadding(3f);
        return c;
    }

    private static PdfPCell pdfNoBorder(Font f, String text) {
        String s = text != null ? text : "—";
        if (s.length() > 2000) {
            s = s.substring(0, 2000) + "…";
        }
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setBorder(0);
        c.setPadding(2f);
        return c;
    }

    private static String listToReadable(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "—";
        }
        return String.join(" · ", list);
    }

    private static String shortId(String u) {
        if (u == null || u.length() < 12) {
            return u != null ? u : "—";
        }
        return u.substring(0, 8) + "…" + u.substring(u.length() - 4);
    }

    @SuppressWarnings("unchecked")
    private String pdfManualInputs(LoanApplication app) {
        if (app.getFinancialInfo() == null) {
            return "No manual credit layer on file.";
        }
        Map<String, Object> fi = app.getFinancialInfo();
        Object cc = fi.get("creditControl");
        if (!(cc instanceof Map)) {
            return "No manual credit layer on file.";
        }
        Map<String, Object> ccm = (Map<String, Object>) cc;
        Object manual = ccm.get("manual");
        if (!(manual instanceof Map)) {
            return "No manual values saved.";
        }
        Map<String, Object> m = (Map<String, Object>) manual;
        List<String> lines = new ArrayList<>();
        for (String key : new String[]{
                "monthlyIncome", "gstIncome", "bankStatementIncome", "propertyValue", "state", "city"}) {
            if (m.get(key) != null) {
                lines.add(prettyManualField(key, m.get(key)));
            }
        }
        Object sdi = m.get("supportingDocumentIds");
        if (sdi != null) {
            lines.add("Linked document ids: " + String.valueOf(sdi).replace('\n', ' '));
        }
        if (lines.isEmpty()) {
            return "No populated manual fields.";
        }
        return String.join("\n", lines);
    }

    private static String prettyManualField(String key, Object cell) {
        if (cell instanceof Map<?, ?> mm && mm.get("value") != null) {
            return humanKey(key) + ": " + String.valueOf(mm.get("value"));
        }
        return humanKey(key) + ": " + String.valueOf(cell);
    }

    private static String humanKey(String k) {
        return k.replace("monthlyIncome", "Monthly income (manual)")
                .replace("gstIncome", "GST income (manual)")
                .replace("bankStatementIncome", "Bank income (manual)")
                .replace("propertyValue", "Property value (manual)")
                .replace("state", "State (manual)")
                .replace("city", "City (manual)");
    }

    private String inrDisplay(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(v);
    }

    private String inrNumber(String maybeDecimal) {
        try {
            return inrDisplay(new BigDecimal(maybeDecimal));
        } catch (Exception e) {
            return maybeDecimal;
        }
    }

    private static String lines(String... lines) {
        return String.join("\n", lines);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "—";
    }

    private static final class CamPdfPageEvent extends PdfPageEventHelper {
        private final Font footerFont;
        private final String left;

        private CamPdfPageEvent(Font footerFont, String left) {
            this.footerFont = footerFont;
            this.left = left;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String shortLeft = left.length() > 90 ? left.substring(0, 90) + "…" : left;
            Phrase a = new Phrase(shortLeft, footerFont);
            Phrase p = new Phrase("Page " + writer.getPageNumber(), footerFont);
            float leftX = document.left() + 4f;
            float y = 22f;
            float rightX = document.getPageSize().getWidth() - document.right() - 4f;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, a, leftX, y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, p, rightX, y, 0);
        }
    }
}
