import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canAccessAdminConfigNav, canCreateOrNotifyBorrowerIntake } from '@/auth/types'
import { createApplication, getApplication, updateApplication } from '@/api/applications'
import { listBorrowerApplications, type BorrowerAppSummary, getBorrowerItrReturnFormsStatus, submitBorrowerItrReturnForms, getBorrowerGstAnalysisStatus } from '@/api/borrowerPortal'
import { listDocuments, uploadDocument } from '@/api/documents'
import { listWorkflows } from '@/api/workflows'
import { submitApplicationForKyc } from '@/api/flow'
import { getKycResults } from '@/api/kyc'
import { IntakeFieldError } from '@/components/intake/IntakeFieldError'
import { GstAnalysisIntakePanel } from '@/components/intake/GstAnalysisIntakePanel'
import { ItrReturnFormsIntakePanel } from '@/components/intake/ItrReturnFormsIntakePanel'
import { useLongRunningAction } from '@/lib/hooks/useLongRunningAction'
import { ErrorState } from '@/components/ErrorState'
import { AnchorIntakeWizard } from '@/components/intake/AnchorIntakeWizard'
import { InvoiceOnboardingTypeCards } from '@/components/intake/InvoiceOnboardingTypeCards'
import { PageHeader } from '@/components/PageHeader'
import { consentHelper, pageDescription, pageTitle } from '@/lib/intake/intakeLabels'
import { AmountInputHint } from '@/components/ui/AmountInputHint'
import { formatMoneyWithScale } from '@/lib/format'
import { CollateralIntakeFields } from '@/components/intake/CollateralIntakeFields'
import { WorkflowCustomIntakeFields } from '@/components/intake/WorkflowCustomIntakeFields'
import { IndiaStateCityPincodeFields } from '@/components/intake/IndiaStateCityPincodeFields'
import { IntakeDocumentUploadList } from '@/components/intake/IntakeDocumentUploadList'
import { persistBorrowerIntakeCollateral } from '@/lib/intake/collateralPersist'
import { buildConsentUpdate, buildIntakeBorrowerUpdate, buildIntakeCreateRequest, buildKycUpdate } from '@/lib/intake/intakePayloads'
import { allDocumentSlotsForIntake } from '@/lib/intake/intakeDocumentSlots'
import { prefetchIntakeGeoForValidation } from '@/lib/intake/masterGeoClientCache'
import { BORROWER_TYPES, createEmptyIntakeFormState, isBusinessBorrowerType, type IntakeFormState, type IntakeMode } from '@/lib/intake/intakeTypes'
import { detectSecuredCollateralKind, requiresCollateral } from '@/lib/intake/securedProducts'
import {
  allConsentsChecked,
  collateralDocumentMissingWarning,
  missingIntakeDocumentTypes,
  productsForBorrowerType,
  validateBorrowerStep,
  validateCollateralIntakeStep,
  validateConsentStep,
  validateKycStep,
  validateNotifyBasics,
  validateProductStep,
} from '@/lib/intake/intakeValidation'
import { BORROWER_TYPE_LABELS } from '@/catalog/borrowerTypes'
import { isInvoiceDiscountingProduct } from '@/catalog/loanProducts'
import { ANCHOR_BORROWER_TYPE } from '@/lib/intake/anchorIntakeConstants'
import {
  DEFAULT_LMS_TENURE_UNIT,
  lmsTenureUnitLabel,
  tenureMagnitudeShortUnit,
} from '@/catalog/lmsTenureUnits'
import { LmsWorkflowConfigReadonly } from '@/components/intake/LmsWorkflowConfigReadonly'
import { linkApplicationToProgram } from '@/api/plp'
import { SelectAnchorProgramStep } from '@/components/intake/SelectAnchorProgramStep'
import { LinkedAnchorProgramReadonly } from '@/components/intake/LinkedAnchorProgramReadonly'
import { InvoiceDiscountingVintageFields } from '@/components/intake/InvoiceDiscountingVintageFields'
import { buildStaffStepLabels, staffIntakeStepIndices } from '@/lib/intake/staffIntakeSteps'
import {
  checkBorrowerIdentity,
  checkKycIdentity,
  intakeErrorMessage,
  intakeStepForDuplicateField,
  validateApplicationIdentity,
} from '@/lib/intake/checkIntakeIdentity'
import { duplicateFieldErrors, duplicateFieldFromError } from '@/lib/userFriendlyError'
import { notifyError, notifySuccess } from '@/lib/notify'
import {
  listApplicationParties,
  notifyBorrowerToComplete,
  submitApplicationParty,
  submitDelegatedBorrowerIntake,
  updateApplicationPartyPersonalInfo,
  upsertApplicationParties,
} from '@/api/workflow'
import {
  CoApplicantsSection,
  StaffCoApplicantDetailForm,
  type CoApplicantRow,
  type StaffMultiPartyPath,
} from '@/components/intake/CoApplicantsSection'
import { CoApplicantPortal } from '@/components/intake/CoApplicantPortal'
import { CategorySelectionPanel } from '@/components/category/CategorySelectionPanel'
import { activeCatalogHasSecuredProduct, matchingWorkflowsForProduct, uniqueActiveWorkflowLoanProducts, workflowLoanProductDisplayName } from '@/utils/workflowProducts'
import { hydrateIntakeFormFromApplication } from '@/lib/intake/hydrateIntakeFromApplication'
import {
  applyHydratedIntakeDefaults,
  inferFirstIncompleteIntakeStep,
  staffCanContinueIntake,
} from '@/lib/intake/intakeResume'
import {
  isBorrowerResumableIntakeStatus,
  isDelegatedBorrowerIntake,
} from '@/lib/borrowerApplicationDeletable'
import {
  workflowById,
  resolveCoApplicantConfig,
  resolveDocumentSlots,
  resolveWorkflowAllowedStates,
  shouldCollectLoanPurposeField,
  shouldCollectPersonalField,
  shouldShowKycIntakeField,
  validateWorkflowTenure,
  workflowRequiresMandatoryItr,
  workflowRequiresMandatoryGstAnalysis,
  workflowHasStep,
} from '@/lib/workflow/workflowIntakeRules'
import {
  labelForLoanPurpose,
  labelForOccupation,
  resolveLoanPurposeOptions,
  resolveOccupationOptions,
} from '@/lib/intake/intakeOptionCatalogs'
import { IntakeTenureField } from '@/components/intake/IntakeTenureField'
import type { WorkflowConfigResponse } from '@/types/workflow'
import type { BorrowerType } from '@/types/createApplication'
import type { ApplicationStatus } from '@/types/application'

function Stepper({ step, labels }: { step: number; labels: readonly string[] }) {
  return (
    <ol className="mb-8 flex flex-wrap items-center gap-2 border-b border-slate-200 pb-4 text-sm">
      {labels.map((label, i) => (
        <li key={label} className="flex items-center gap-2">
          <span
            className={[
              'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
              i < step
                ? 'bg-[var(--bt-green-bg)] text-[var(--bt-green)]'
                : i === step
                  ? 'bg-[var(--bt-orange)] text-white'
                  : 'bg-[var(--bt-gray-100)] text-[var(--bt-gray-500)]',
            ].join(' ')}
            aria-current={i === step ? 'step' : undefined}
          >
            {i + 1}
          </span>
          <span className={i === step ? 'font-medium text-slate-900' : 'text-slate-600'}>{label}</span>
          {i < labels.length - 1 ? <span className="hidden sm:inline text-slate-300">·</span> : null}
        </li>
      ))}
    </ol>
  )
}

export interface ApplicationIntakeWizardProps {
  mode: IntakeMode
  /** Lender / sales pages use the staff header + back link; borrower layout uses its own shell. */
  variant: 'borrower' | 'staff'
  /** Staff: resume intake on an existing editable application (`/applications/:id/intake`). */
  editApplicationId?: string
}

/** Statuses where RM is editing an already-submitted case — save changes, do not re-submit for KYC. */
function isStaffPostSubmitEditStatus(status: ApplicationStatus | null): boolean {
  return status === 'BORROWER_SUBMITTED' || status === 'SENT_BACK_TO_RM'
}

export function ApplicationIntakeWizard({ mode, variant, editApplicationId }: ApplicationIntakeWizardProps) {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { user } = useAuth()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState<IntakeFormState>(createEmptyIntakeFormState)
  const [applicationId, setApplicationId] = useState<string | null>(null)
  const [activeWorkflows, setActiveWorkflows] = useState<WorkflowConfigResponse[]>([])
  const [workflowsState, setWorkflowsState] = useState<'loading' | 'ok' | 'err'>('loading')
  const [workflowsError, setWorkflowsError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [docWarning, setDocWarning] = useState<string | null>(null)
  const [collateralDocWarn, setCollateralDocWarn] = useState<string | null>(null)
  /** Staff: after invoice discounting → Anchor, branch into embedded anchor wizard. */
  const [anchorBranch, setAnchorBranch] = useState<{
    requestedAmount: string
    tenureMonths: string
  } | null>(null)
  const [delegatedApp, setDelegatedApp] = useState(false)
  const [sentBackNotes, setSentBackNotes] = useState<string | null>(null)
  const [hydrating, setHydrating] = useState(false)
  const [resumeError, setResumeError] = useState<string | null>(null)
  const [incompleteServer, setIncompleteServer] = useState<BorrowerAppSummary[]>([])
  const [resumeLoaded, setResumeLoaded] = useState(false)
  const [resumedAppStatus, setResumedAppStatus] = useState<ApplicationStatus | null>(null)
  /** Staff intake only: co-applicant rows when the active workflow enables joint applications. */
  const [coApplicants, setCoApplicants] = useState<CoApplicantRow[]>([])
  /** Staff path when co-applicants are present: portal notify vs fill-all-in-wizard. */
  const [staffMultiPartyPath, setStaffMultiPartyPath] = useState<StaffMultiPartyPath | null>(null)
  /** When non-null, staff is filling co-applicant details one-by-one after primary review. */
  const [staffCoFillIndex, setStaffCoFillIndex] = useState<number | null>(null)
  const [itrUsername, setItrUsername] = useState('')
  const [itrPassword, setItrPassword] = useState('')
  const [itrConsent, setItrConsent] = useState(false)
  const [itrSuccess, setItrSuccess] = useState(false)
  const [itrStatusLabel, setItrStatusLabel] = useState<string | null>(null)
  const [itrError, setItrError] = useState<string | null>(null)
  const itrLongRun = useLongRunningAction()
  const [gstAnalysisGstin, setGstAnalysisGstin] = useState('')
  const [gstAnalysisConsent, setGstAnalysisConsent] = useState(false)
  const [gstAnalysisPrepared, setGstAnalysisPrepared] = useState(false)
  const [gstAnalysisStatusLabel, setGstAnalysisStatusLabel] = useState<string | null>(null)
  const [gstAnalysisError, setGstAnalysisError] = useState<string | null>(null)
  const [gstAnalysisBusy, setGstAnalysisBusy] = useState(false)
  const [gstAnalysisReportSuccess, setGstAnalysisReportSuccess] = useState(false)

  const resumeApplicationId =
    editApplicationId ?? (variant === 'borrower' ? searchParams.get('resume') : null)
  /** Co-applicant portal link: `/apply?resume={appId}&partyId={partyId}`. Never set for the primary borrower. */
  const coApplicantPartyId = variant === 'borrower' ? searchParams.get('partyId') : null

  function clearFieldError(key: string) {
    setFieldErrors((prev) => {
      if (!prev[key]) return prev
      const next = { ...prev }
      delete next[key]
      return next
    })
  }

  const needColl = useMemo(() => requiresCollateral(form.loanProduct), [form.loanProduct])
  const needPlpAnchorStep = useMemo(
    () =>
      variant === 'staff' &&
      isInvoiceDiscountingProduct(form.loanProduct) &&
      form.invoiceOnboardingChoice === 'BORROWER',
    [variant, form.loanProduct, form.invoiceOnboardingChoice],
  )
  const steps = useMemo(
    () => staffIntakeStepIndices(needColl, needPlpAnchorStep),
    [needColl, needPlpAnchorStep],
  )
  const stepLabels = useMemo(
    () => buildStaffStepLabels(needColl, needPlpAnchorStep),
    [needColl, needPlpAnchorStep],
  )
  const lastStep = steps.last

  const productsForType = productsForBorrowerType(activeWorkflows, form.borrowerType)
  const staffProductList = useMemo(() => uniqueActiveWorkflowLoanProducts(activeWorkflows), [activeWorkflows])
  const intakeSegmentForWorkflow: 'BORROWER' | 'ANCHOR' =
    isInvoiceDiscountingProduct(form.loanProduct) && form.invoiceOnboardingChoice === 'ANCHOR'
      ? 'ANCHOR'
      : 'BORROWER'
  // Anchor product step hides borrower-type picker; workflows are COMPANY + ANCHOR segment.
  const workflowBorrowerType =
    intakeSegmentForWorkflow === 'ANCHOR' ? ANCHOR_BORROWER_TYPE : form.borrowerType
  const workflowsForSelectedProduct = useMemo(
    () =>
      matchingWorkflowsForProduct(
        activeWorkflows,
        workflowBorrowerType,
        form.loanProduct,
        intakeSegmentForWorkflow,
      ),
    [activeWorkflows, workflowBorrowerType, form.loanProduct, intakeSegmentForWorkflow],
  )
  const selectedWorkflow = workflowById(activeWorkflows, form.workflowId)
  const requiresItr = useMemo(
    () => workflowRequiresMandatoryItr(selectedWorkflow) || workflowHasStep(selectedWorkflow, 'ITR_RETURN_FORMS'),
    [selectedWorkflow],
  )
  const itrMandatory = useMemo(() => workflowRequiresMandatoryItr(selectedWorkflow), [selectedWorkflow])
  const requiresGstAnalysis = useMemo(
    () =>
      workflowRequiresMandatoryGstAnalysis(selectedWorkflow) ||
      workflowHasStep(selectedWorkflow, 'GST_ANALYSIS'),
    [selectedWorkflow],
  )
  const gstAnalysisMandatory = useMemo(
    () => workflowRequiresMandatoryGstAnalysis(selectedWorkflow),
    [selectedWorkflow],
  )

  useEffect(() => {
    if (!applicationId || !requiresItr) return
    let cancelled = false
    void (async () => {
      try {
        if (variant === 'borrower') {
          const st = await getBorrowerItrReturnFormsStatus(applicationId)
          if (cancelled) return
          setItrSuccess(Boolean(st.success))
          setItrStatusLabel(st.status)
          if (st.errorMessage && !st.success) setItrError(st.errorMessage)
        } else {
          const rows = await getKycResults(applicationId)
          if (cancelled) return
          const itr = [...rows].reverse().find((r) => String(r.stepType).toUpperCase() === 'ITR_RETURN_FORMS')
          const ok = itr != null && (String(itr.outcome).toUpperCase() === 'SUCCESS' || itr.overridden)
          setItrSuccess(Boolean(ok))
          setItrStatusLabel(itr?.outcome != null ? String(itr.outcome) : 'NOT_STARTED')
          if (!ok && itr?.errorMessage) setItrError(String(itr.errorMessage))
        }
      } catch {
        /* status endpoint may 403 for staff — fall back quietly */
      }
    })()
    return () => {
      cancelled = true
    }
  }, [applicationId, requiresItr, variant])

  useEffect(() => {
    if (!applicationId || !requiresGstAnalysis) return
    let cancelled = false
    void (async () => {
      try {
        if (variant === 'borrower') {
          const st = await getBorrowerGstAnalysisStatus(applicationId)
          if (cancelled) return
          setGstAnalysisPrepared(Boolean(st.consent) && Boolean(st.gstin) && (st.documentCount ?? 0) > 0)
          setGstAnalysisStatusLabel(st.status ?? st.phase ?? null)
          setGstAnalysisReportSuccess(Boolean(st.reportSuccess))
          if (st.gstin) setGstAnalysisGstin(String(st.gstin))
          if (st.consent) setGstAnalysisConsent(true)
          if (st.errorMessage && !st.success && st.phase !== 'PREPARE') setGstAnalysisError(st.errorMessage)
        } else {
          const rows = await getKycResults(applicationId)
          if (cancelled) return
          const gst = [...rows].reverse().find((r) => String(r.stepType).toUpperCase() === 'GST_ANALYSIS')
          const pd = (gst?.parsedData ?? {}) as Record<string, unknown>
          const prepared =
            gst != null &&
            (Boolean(pd.consent) ||
              String(pd.phase).toUpperCase() === 'PREPARE' ||
              String(pd.phase).toUpperCase() === 'UPLOAD' ||
              String(pd.phase).toUpperCase() === 'REPORT')
          setGstAnalysisPrepared(Boolean(prepared) || String(gst?.outcome ?? '').toUpperCase() === 'SUCCESS')
          setGstAnalysisStatusLabel(gst?.outcome != null ? String(gst.outcome) : 'NOT_STARTED')
          setGstAnalysisReportSuccess(String(pd.phase).toUpperCase() === 'REPORT' && String(gst?.outcome).toUpperCase() === 'SUCCESS')
          if (pd.gstin) setGstAnalysisGstin(String(pd.gstin))
        }
      } catch {
        /* ignore */
      }
    })()
    return () => {
      cancelled = true
    }
  }, [applicationId, requiresGstAnalysis, variant])

  useEffect(() => {
    if (!itrUsername.trim() && form.panNumber.trim()) {
      setItrUsername(form.panNumber.trim().toUpperCase())
    }
  }, [form.panNumber, itrUsername])

  useEffect(() => {
    if (!gstAnalysisGstin.trim() && form.gstin.trim()) {
      setGstAnalysisGstin(form.gstin.trim().toUpperCase())
    }
  }, [form.gstin, gstAnalysisGstin])

  const workflowAllowedStates = useMemo(
    () => resolveWorkflowAllowedStates(selectedWorkflow),
    [selectedWorkflow],
  )
  const documentSlots = useMemo(
    () =>
      selectedWorkflow?.intakeConfig?.policy === 'WORKFLOW_DRIVEN'
        ? resolveDocumentSlots(selectedWorkflow, form.borrowerType)
        : allDocumentSlotsForIntake(form),
    [selectedWorkflow, form],
  )
  const productLocked = Boolean(applicationId)
  /** Invoice discounting must never show co-applicant UI; feature is staff-only (RM capture at create). */
  const coApplicantConfig =
    variant === 'staff' && !isInvoiceDiscountingProduct(form.loanProduct)
      ? resolveCoApplicantConfig(selectedWorkflow)
      : null
  const coApplicantMin = coApplicantConfig?.minCoApplicants ?? 0
  const coApplicantMax = coApplicantConfig?.maxCoApplicants ?? 3
  const staffCanCreateOrNotify =
    variant === 'staff' && canCreateOrNotifyBorrowerIntake(user?.role ?? '')
  const notifyBasicsOk =
    staffCanCreateOrNotify &&
    !validateNotifyBasics(form, mode, activeWorkflows, { needPlpProgram: needPlpAnchorStep })

  useEffect(() => {
    if (!selectedWorkflow || isInvoiceDiscountingProduct(form.loanProduct)) return
    // Derive LMS code from workflow config only — do not invent / hardcode Encore product codes.
    setForm((f) => ({
      ...f,
      lmsProductCode: selectedWorkflow.lmsProductCode?.trim() || '',
      lmsTenureUnit: selectedWorkflow.lmsTenureUnit?.trim() || DEFAULT_LMS_TENURE_UNIT,
    }))
  }, [selectedWorkflow?.id, selectedWorkflow?.lmsProductCode, selectedWorkflow?.lmsTenureUnit, form.loanProduct])

  const loadWorkflows = useCallback(async () => {
    setWorkflowsState('loading')
    setWorkflowsError(null)
    try {
      const all = await listWorkflows()
      setActiveWorkflows(all)
      setWorkflowsState('ok')
      setForm((f) => {
        const unique = uniqueActiveWorkflowLoanProducts(all)
        if (unique.length === 0) {
          return f.loanProduct
            ? { ...f, loanProduct: '', invoiceOnboardingChoice: '' }
            : f
        }
        let loanProduct = f.loanProduct
        if (!loanProduct || !unique.includes(loanProduct)) {
          loanProduct = unique[0]!
        }
        let invoiceOnboardingChoice = f.invoiceOnboardingChoice
        if (!isInvoiceDiscountingProduct(loanProduct)) {
          invoiceOnboardingChoice = ''
        }
        const segment =
          isInvoiceDiscountingProduct(loanProduct) && invoiceOnboardingChoice === 'ANCHOR'
            ? 'ANCHOR'
            : 'BORROWER'
        return {
          ...f,
          loanProduct,
          invoiceOnboardingChoice,
          ...(segment === 'ANCHOR' ? { borrowerType: ANCHOR_BORROWER_TYPE } : {}),
        }
      })
    } catch (e) {
      setActiveWorkflows([])
      setWorkflowsError(e instanceof Error ? e.message : 'Failed to load workflows')
      setWorkflowsState('err')
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async listWorkflows; state set inside loadWorkflows
    void loadWorkflows()
  }, [loadWorkflows])

  useEffect(() => {
    if (mode === 'SALES_ASSISTED' || mode === 'ADMIN_INTERNAL') {
      if (!user) return
      // eslint-disable-next-line react-hooks/set-state-in-effect -- optional staff field seed when session appears
      setForm((f) => {
        if (f.salesOfficerName.trim() && f.salesOfficerId.trim()) return f
        return { ...f, salesOfficerName: user.name, salesOfficerId: user.userId }
      })
    }
  }, [mode, user])

  const reloadIncomplete = useCallback(() => {
    if (variant !== 'borrower' || !user) return
    void listBorrowerApplications(0, 40)
      .then((p) => {
        setIncompleteServer(p.content.filter((a) => isBorrowerResumableIntakeStatus(a.status)))
      })
      .catch(() => setIncompleteServer([]))
  }, [variant, user])

  useEffect(() => {
    reloadIncomplete()
  }, [reloadIncomplete])

  useEffect(() => {
    if (variant !== 'borrower' || !user) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- seed name/email from session when empty
    setForm((f) => {
      if (f.email.trim() && f.fullName.trim()) return f
      return { ...f, fullName: f.fullName || user.name, email: f.email || user.email }
    })
  }, [variant, user])

  useEffect(() => {
    setResumeLoaded(false)
    setResumedAppStatus(null)
  }, [resumeApplicationId])

  useEffect(() => {
    // Co-applicant links are handled entirely by CoApplicantPortal — skip the primary-borrower hydration/ownership
    // checks below, since a co-applicant is never the application's `customerId`.
    if (!resumeApplicationId || resumeLoaded || workflowsState !== 'ok' || coApplicantPartyId) return
    let cancelled = false
    void (async () => {
      setResumeError(null)
      setHydrating(true)
      try {
        // Resume without partyId: if this user is a co-applicant on the app, route into CoApplicantPortal.
        if (variant === 'borrower' && user?.userId) {
          try {
            const listed = await listBorrowerApplications(0, 50)
            const mine = listed.content.find((a) => a.applicationId === resumeApplicationId)
            if (mine?.partyRole === 'CO_APPLICANT' && mine.partyId) {
              if (cancelled) return
              setSearchParams((prev) => {
                const n = new URLSearchParams(prev)
                n.set('resume', resumeApplicationId)
                n.set('partyId', mine.partyId!)
                return n
              })
              setHydrating(false)
              return
            }
          } catch {
            // fall through to primary ownership check
          }
        }
        const app = await getApplication(resumeApplicationId)
        if (cancelled) return
        if (variant === 'borrower') {
          if (!user || app.customerId !== user.userId) {
            setResumeError('You can only open your own application.')
            return
          }
          if (!isBorrowerResumableIntakeStatus(app.status)) {
            setResumeError('This application is no longer a draft. Open it from your applications list.')
            return
          }
        } else if (editApplicationId) {
          if (!staffCanContinueIntake(app, user?.role)) {
            setResumeError(
              'This application cannot be edited in Continue intake. Open it from the applications list, or wait until it is with the Relationship Manager again.',
            )
            return
          }
        }
        setResumedAppStatus(app.status)
        const h0 = hydrateIntakeFormFromApplication(app)
        let h = applyHydratedIntakeDefaults(h0, app, variant)
        try {
          const docs = await listDocuments(app.id)
          const uploaded = { ...h.documentUploaded }
          for (const d of docs) {
            uploaded[d.documentType] = true
          }
          h = { ...h, documentUploaded: uploaded }
        } catch {
          // optional — document flags improve resume step inference
        }
        setForm(h)
        setApplicationId(app.id)
        setDelegatedApp(isDelegatedBorrowerIntake(app))
        setSentBackNotes(app.borrowerSentBackNotes ?? null)
        const needPlpResume =
          variant === 'staff' &&
          isInvoiceDiscountingProduct(h.loanProduct) &&
          h.invoiceOnboardingChoice === 'BORROWER'
        const needCollResume = requiresCollateral(h.loanProduct)
        const stepMap = staffIntakeStepIndices(needCollResume, needPlpResume)
        setStep(
          inferFirstIncompleteIntakeStep(
            h,
            stepMap,
            mode,
            activeWorkflows,
            needPlpResume,
            needCollResume,
          ),
        )
        if (variant === 'borrower' && !editApplicationId) {
          setSearchParams(
            (prev) => {
              const next = new URLSearchParams(prev)
              next.delete('resume')
              return next
            },
            { replace: true },
          )
        }
      } catch (e) {
        if (!cancelled) setResumeError(intakeErrorMessage(e, 'Could not load application'))
      } finally {
        if (!cancelled) {
          setHydrating(false)
          setResumeLoaded(true)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [
    resumeApplicationId,
    resumeLoaded,
    workflowsState,
    variant,
    user,
    editApplicationId,
    setSearchParams,
    coApplicantPartyId,
  ])

  useEffect(() => {
    if (!applicationId || !coApplicantConfig) return
    let cancelled = false
    void listApplicationParties(applicationId)
      .then((parties) => {
        if (cancelled) return
        const co = parties.filter((p) => p.role === 'CO_APPLICANT')
        setCoApplicants(
          co.map((p) => ({
            id: p.id,
            fullName: p.displayName ?? String(p.personalInfo?.fullName ?? ''),
            mobile: p.mobile ?? String(p.personalInfo?.mobile ?? ''),
            email: p.email ?? String(p.personalInfo?.email ?? ''),
            relationship: String(p.personalInfo?.relationship ?? ''),
          })),
        )
      })
      .catch(() => {
        // optional — co-applicant rows can still be added fresh
      })
    return () => {
      cancelled = true
    }
    // Re-run only when the application or co-applicant enablement changes, not on every keystroke.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId, Boolean(coApplicantConfig)])

  function primaryEmailFromForm(): string {
    if (form.borrowerType === 'INDIVIDUAL') return form.email.trim().toLowerCase()
    return form.contactEmail.trim().toLowerCase()
  }

  function primaryMobileFromForm(): string {
    if (form.borrowerType === 'INDIVIDUAL') {
      const m = form.mobile.trim() || form.borrowerMobile.trim()
      return m.replace(/\D/g, '')
    }
    return form.contactMobile.replace(/\D/g, '')
  }

  function coApplicantValidationError(): string | null {
    if (!coApplicantConfig) return null
    if (coApplicants.length < coApplicantMin) {
      return `At least ${coApplicantMin} co-applicant(s) are required for this workflow.`
    }
    if (coApplicants.length > coApplicantMax) {
      return `At most ${coApplicantMax} co-applicant(s) are allowed for this workflow.`
    }
    const emails = new Set<string>()
    const mobiles = new Set<string>()
    const primaryEmail = primaryEmailFromForm()
    const primaryMobile = primaryMobileFromForm()
    if (primaryEmail) emails.add(primaryEmail)
    if (primaryMobile.length >= 10) mobiles.add(primaryMobile)
    for (const c of coApplicants) {
      if (!c.fullName.trim()) return 'Enter full name for each co-applicant.'
      const mobileDigits = c.mobile.replace(/\D/g, '')
      if (mobileDigits.length < 10) return 'Enter a valid mobile number for each co-applicant.'
      if (!c.email.trim()) return 'Enter email for each co-applicant.'
      const email = c.email.trim().toLowerCase()
      if (emails.has(email)) {
        return `Each applicant must use a different email. Duplicate: ${c.email.trim()}`
      }
      if (mobiles.has(mobileDigits)) {
        return 'Each applicant must use a different mobile number.'
      }
      emails.add(email)
      mobiles.add(mobileDigits)
    }
    if (coApplicants.length > 0 && !staffMultiPartyPath) {
      return 'Choose whether to notify applicants or fill all applicant details yourself.'
    }
    return null
  }

  async function checkCoApplicantIdentities(appId: string | null): Promise<string | null> {
    for (const c of coApplicants) {
      try {
        await validateApplicationIdentity({
          applicationId: appId ?? undefined,
          asCoApplicant: true,
          email: c.email.trim() || undefined,
          mobile: c.mobile.replace(/\D/g, '') || undefined,
          panNumber: c.panNumber?.trim() || undefined,
        })
      } catch (err) {
        const dup = duplicateFieldErrors(err)
        if (dup) {
          const first = Object.values(dup)[0]
          return `Co-applicant ${c.fullName.trim() || c.email}: ${first ?? 'identity already in use'}`
        }
        throw err
      }
    }
    return null
  }

  async function persistCoApplicants(appId: string): Promise<boolean> {
    if (!coApplicantConfig) return true
    try {
      const parties = await upsertApplicationParties(appId, {
        coApplicants: coApplicants.map((c) => ({
          id: c.id,
          personalInfo: {
            fullName: c.fullName.trim(),
            mobile: c.mobile.trim(),
            email: c.email.trim(),
            ...(c.relationship.trim() ? { relationship: c.relationship.trim() } : {}),
            ...(c.dateOfBirth?.trim() ? { dateOfBirth: c.dateOfBirth.trim() } : {}),
            ...(c.gender?.trim() ? { gender: c.gender.trim() } : {}),
            ...(c.occupation?.trim() ? { occupation: c.occupation.trim() } : {}),
            ...(c.panNumber?.trim() ? { panNumber: c.panNumber.trim().toUpperCase() } : {}),
          },
        })),
      })
      const co = parties.filter((p) => p.role === 'CO_APPLICANT')
      setCoApplicants((prev) =>
        prev.map((row, i) => ({
          ...row,
          id: co[i]?.id ?? row.id,
        })),
      )
      return true
    } catch (err) {
      setError(intakeErrorMessage(err, 'Could not save co-applicant details.'))
      notifyError(err, 'Could not save co-applicant details.')
      return false
    }
  }

  async function persistAndSubmitStaffCoApplicants(appId: string): Promise<boolean> {
    const ok = await persistCoApplicants(appId)
    if (!ok) return false
    const parties = await listApplicationParties(appId)
    const cos = parties.filter((p) => p.role === 'CO_APPLICANT')
    for (let i = 0; i < cos.length; i++) {
      const party = cos[i]!
      const row = coApplicants[i]
      if (!row) continue
      await updateApplicationPartyPersonalInfo(appId, party.id, {
        fullName: row.fullName.trim(),
        mobile: row.mobile.trim(),
        email: row.email.trim(),
        ...(row.relationship.trim() ? { relationship: row.relationship.trim() } : {}),
        ...(row.dateOfBirth?.trim() ? { dateOfBirth: row.dateOfBirth.trim() } : {}),
        ...(row.gender?.trim() ? { gender: row.gender.trim() } : {}),
        ...(row.occupation?.trim() ? { occupation: row.occupation.trim() } : {}),
        ...(row.panNumber?.trim() ? { panNumber: row.panNumber.trim().toUpperCase() } : {}),
      })
      await submitApplicationParty(appId, party.id)
    }
    return true
  }

  const syncDocumentsFromServer = useCallback(async (id: string) => {
    try {
      const docs = await listDocuments(id)
      setForm((f) => {
        const m = { ...f.documentUploaded }
        for (const d of docs) {
          m[d.documentType] = true
        }
        return { ...f, documentUploaded: m }
      })
    } catch {
      // optional — leave local flags
    }
  }, [])

  useEffect(() => {
    if (step === steps.documents && applicationId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- async listDocuments; updates upload flags
      void syncDocumentsFromServer(applicationId)
    }
  }, [step, applicationId, syncDocumentsFromServer, steps.documents])
  useEffect(() => {
    if (step === steps.collateral && needColl && applicationId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void syncDocumentsFromServer(applicationId)
    }
  }, [step, needColl, applicationId, syncDocumentsFromServer, steps.collateral])

  async function persistFromProductStep(): Promise<boolean> {
    if (!applicationId) return true
    setBusy(true)
    try {
      const req = buildIntakeBorrowerUpdate(form, mode, user)
      await updateApplication(applicationId, req)
      return true
    } catch (err) {
      setError(intakeErrorMessage(err, 'Could not save product details.'))
      return false
    } finally {
      setBusy(false)
    }
  }

  async function goNext() {
    setError(null)
    setFieldErrors({})
    setCollateralDocWarn(null)
    if (step === steps.product) {
      const v = validateProductStep(form, mode, activeWorkflows)
      if (v) {
        setError(v)
        return
      }
      if (
        variant === 'staff' &&
        isInvoiceDiscountingProduct(form.loanProduct) &&
        form.invoiceOnboardingChoice === 'ANCHOR'
      ) {
        setAnchorBranch({
          requestedAmount: form.requestedAmount,
          tenureMonths: form.tenureMonths,
        })
        return
      }
      if (needPlpAnchorStep) {
        setBusy(true)
        try {
          if (!applicationId) {
            const req = buildIntakeCreateRequest(form, mode, user)
            const res = await createApplication(req)
            setApplicationId(res.id)
          } else {
            const ok = await persistFromProductStep()
            if (!ok) return
          }
          setStep(steps.plp)
        } catch (err) {
          setError(intakeErrorMessage(err, 'Could not create application.'))
          notifyError(err, 'Could not create application.')
        } finally {
          setBusy(false)
        }
        return
      }
      if (applicationId) {
        const ok = await persistFromProductStep()
        if (!ok) return
        setForm((f) =>
          mode === 'SALES_ASSISTED' && f.borrowerType === 'INDIVIDUAL' && !f.mobile.trim() && f.borrowerMobile
            ? { ...f, mobile: f.borrowerMobile }
            : f,
        )
        setStep(steps.borrower)
        return
      }
      setForm((f) =>
        mode === 'SALES_ASSISTED' && f.borrowerType === 'INDIVIDUAL' && !f.mobile.trim() && f.borrowerMobile
          ? { ...f, mobile: f.borrowerMobile }
          : f,
      )
      setStep(steps.borrower)
      return
    }
    if (needPlpAnchorStep && step === steps.plp) {
      if (!form.selectedSubProgramId) {
        setError('Select an anchor program to continue.')
        return
      }
      if (!applicationId) {
        setError('Application not created yet.')
        return
      }
      setBusy(true)
      try {
        await linkApplicationToProgram(applicationId, form.selectedSubProgramId)
        setStep(steps.borrower)
      } catch (err) {
        setError(intakeErrorMessage(err, 'Could not link program.'))
        notifyError(err, 'Could not link program.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === steps.borrower) {
      try {
        await prefetchIntakeGeoForValidation(form)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Could not load location master data. Try again.')
        return
      }
      const v = validateBorrowerStep(form, mode, selectedWorkflow)
      if (v) {
        setError(v)
        return
      }
      const coErr = coApplicantValidationError()
      if (coErr) {
        setError(coErr)
        return
      }
      setBusy(true)
      try {
        const dup = await checkBorrowerIdentity(form, mode, applicationId)
        if (dup) {
          setFieldErrors(dup)
          return
        }
        let appId = applicationId
        if (!appId) {
          const req = buildIntakeCreateRequest(form, mode, user)
          const res = await createApplication(req)
          appId = res.id
          setApplicationId(res.id)
        } else {
          await updateApplication(appId, buildIntakeBorrowerUpdate(form, mode, user))
        }
        if (coApplicantConfig) {
          const ok = await persistCoApplicants(appId)
          if (!ok) return
        }
        if (coApplicantConfig && coApplicants.length > 0 && staffMultiPartyPath === 'notify') {
          // Stay on borrower step — use Save draft & notify rather than advancing the full wizard.
          setError(null)
          notifySuccess('Basics saved. Use “Save draft & notify” to invite applicants, or switch to staff fill.')
          return
        }
        setStep(steps.category)
      } catch (err) {
        const msg = intakeErrorMessage(err, 'Could not save application details.')
        setError(msg)
        notifyError(err, 'Could not save application details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === steps.category) {
      if (!form.workflowId.trim()) {
        setError('Select a Customer Category so this application can pin its Workflow Version.')
        return
      }
      const pinned = workflowById(activeWorkflows, form.workflowId)
      const tenureErr = validateWorkflowTenure(form, pinned)
      if (tenureErr) {
        setError(tenureErr)
        setStep(steps.product)
        return
      }
      const borrowerErr = validateBorrowerStep(form, mode, pinned)
      if (borrowerErr) {
        setError(borrowerErr)
        setStep(steps.borrower)
        return
      }
      setStep(needColl ? steps.collateral : steps.documents)
      return
    }
    if (step === steps.collateral && needColl) {
      const v = validateCollateralIntakeStep(form)
      if (v) {
        setError(v)
        return
      }
      setCollateralDocWarn(collateralDocumentMissingWarning(form))
      if (!applicationId) return
      setBusy(true)
      try {
        await persistBorrowerIntakeCollateral(applicationId, form, mode)
        setStep(steps.documents)
      } catch (err) {
        setError(intakeErrorMessage(err, 'Could not save collateral details.'))
        notifyError(err, 'Could not save collateral details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === steps.documents) {
      const miss = missingIntakeDocumentTypes(form, selectedWorkflow)
      if (miss.length && selectedWorkflow?.intakeConfig?.policy === 'WORKFLOW_DRIVEN') {
        setError(`Required documents missing: ${miss.join(', ')}`)
        return
      }
      if (miss.length) {
        setDocWarning(
          `For a complete package you may still add: ${miss.join(', ')}. You can continue to review, or go back to upload more.`,
        )
      } else {
        setDocWarning(null)
      }
      setStep(steps.consent)
      return
    }
    if (step === steps.consent) {
      const v3 = validateConsentStep(form)
      if (v3) {
        setError(v3)
        return
      }
      if (!applicationId) return
      setBusy(true)
      try {
        await updateApplication(applicationId, buildConsentUpdate(form, mode, user))
        setStep(steps.kyc)
      } catch (err) {
        setError(intakeErrorMessage(err, 'Could not save consents.'))
        notifyError(err, 'Could not save consents.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === steps.kyc) {
      const v = validateKycStep(form, selectedWorkflow)
      if (v) {
        setError(v)
        return
      }
      if (itrMandatory && !itrSuccess) {
        if (variant === 'borrower') {
          setError('Complete ITR portal login (username, password, and consent) before continuing.')
          return
        }
        // Staff may leave KYC step, but cannot final-submit without notify/borrower success.
      }
      if (gstAnalysisMandatory && !gstAnalysisPrepared) {
        if (variant === 'borrower') {
          setError('Complete GST analysis section (GST return PDFs, GSTIN, and consent) before continuing.')
          return
        }
      }
      if (!applicationId) return
      setBusy(true)
      try {
        const dup = await checkKycIdentity(form, applicationId)
        if (dup) {
          setFieldErrors(dup)
          return
        }
        await updateApplication(applicationId, buildKycUpdate(form))
        setStep(steps.review)
      } catch (err) {
        const msg = intakeErrorMessage(err, 'Could not save KYC details.')
        setError(msg)
        notifyError(err, 'Could not save KYC details.')
      } finally {
        setBusy(false)
      }
      return
    }
  }

  function goBack() {
    setError(null)
    if (staffCoFillIndex != null) {
      if (staffCoFillIndex > 0) {
        setStaffCoFillIndex(staffCoFillIndex - 1)
      } else {
        setStaffCoFillIndex(null)
      }
      return
    }
    if (step > 0) {
      if (applicationId && step === 0) {
        // cannot go back before step0 from elsewhere
        return
      }
      setStep((s) => s - 1)
    }
  }

  async function onNotifyBorrower() {
    if (anchorBranch) return
    if (!staffCanCreateOrNotify) {
      setError('Only relationship managers and administrators can notify the borrower.')
      return
    }
    const basicsErr = validateNotifyBasics(form, mode, activeWorkflows, {
      needPlpProgram: needPlpAnchorStep,
    })
    if (basicsErr) {
      setError(basicsErr)
      return
    }
    const coErr = coApplicantValidationError()
    if (coErr) {
      setError(coErr)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const dup = await checkBorrowerIdentity(form, mode, applicationId)
      if (dup) {
        setFieldErrors(dup)
        return
      }
      const coDup = await checkCoApplicantIdentities(applicationId)
      if (coDup) {
        setError(coDup)
        return
      }
      let appId = applicationId
      if (!appId) {
        const req = buildIntakeCreateRequest(form, mode, user)
        const res = await createApplication(req)
        appId = res.id
        setApplicationId(res.id)
      } else {
        await updateApplication(appId, buildIntakeBorrowerUpdate(form, mode, user))
      }
      if (coApplicantConfig) {
        const ok = await persistCoApplicants(appId)
        if (!ok) return
      }
      // Resume at Documents (first step after basics in the reordered wizard).
      await notifyBorrowerToComplete(appId, steps.documents)
      notifySuccess(
        coApplicantConfig && coApplicants.length > 0
          ? 'All applicants notified to complete the application in the portal.'
          : 'Borrower notified to complete the application in the portal.',
      )
      void navigate(`/applications/${appId}`, { replace: true })
    } catch (err) {
      const msg = intakeErrorMessage(err, 'Could not notify borrower.')
      setError(msg)
      notifyError(err, 'Could not notify borrower.')
    } finally {
      setBusy(false)
    }
  }

  async function onSubmitFinal() {
    if (!applicationId) return
    if (
      variant === 'staff' &&
      staffMultiPartyPath === 'staff_fill' &&
      coApplicants.length > 0 &&
      staffCoFillIndex == null
    ) {
      setStaffCoFillIndex(0)
      setError(null)
      return
    }
    if (variant === 'staff' && staffCoFillIndex != null) {
      const row = coApplicants[staffCoFillIndex]
      if (!row) return
      if (!row.fullName.trim() || row.mobile.replace(/\D/g, '').length < 10 || !row.email.trim()) {
        setError('Complete name, mobile, and email for this co-applicant.')
        return
      }
      if (coApplicantConfig?.personalFields?.dateOfBirth?.required && !row.dateOfBirth?.trim()) {
        setError('Date of birth is required for this co-applicant.')
        return
      }
      const identityErr = await (async () => {
        try {
          await validateApplicationIdentity({
            applicationId,
            asCoApplicant: true,
            email: row.email.trim(),
            mobile: row.mobile.replace(/\D/g, ''),
            panNumber: row.panNumber?.trim() || undefined,
          })
          return null
        } catch (err) {
          const dup = duplicateFieldErrors(err)
          if (dup) return Object.values(dup)[0] ?? 'Identity already in use'
          throw err
        }
      })()
      if (identityErr) {
        setError(identityErr)
        return
      }
      if (staffCoFillIndex < coApplicants.length - 1) {
        setStaffCoFillIndex(staffCoFillIndex + 1)
        setError(null)
        return
      }
      // Last co-applicant — fall through to submit after persisting.
      setStaffCoFillIndex(null)
    }
    setBusy(true)
    setError(null)
    try {
      // RM editing a case already with them — persist intake changes and return (do not re-run KYC submit).
      if (editApplicationId && isStaffPostSubmitEditStatus(resumedAppStatus)) {
        await updateApplication(applicationId, buildIntakeBorrowerUpdate(form, mode, user))
        if (needColl) {
          await persistBorrowerIntakeCollateral(applicationId, form, mode)
        }
        notifySuccess('Application details updated.')
        void navigate(`/applications/${applicationId}`, { replace: true })
        return
      }
      if (!allConsentsChecked(form)) {
        setError('All consents are required before submission.')
        return
      }
      if (itrMandatory && !itrSuccess) {
        if (variant === 'staff') {
          setError(
            'ITR login must be completed by the borrower on the portal. Use “Save draft & notify” so they can enter ITD username/password.',
          )
          return
        }
        setError('Complete ITR portal login before submitting.')
        return
      }
      if (gstAnalysisMandatory && !gstAnalysisPrepared) {
        if (variant === 'staff') {
          setError(
            'GST analysis must be completed by the borrower on the portal (GST return PDFs, GSTIN, and consent). Use “Save draft & notify”.',
          )
          return
        }
        setError('Complete GST analysis (PDFs, GSTIN, and consent) before submitting.')
        return
      }
      if (variant === 'staff' && staffMultiPartyPath === 'staff_fill' && coApplicants.length > 0) {
        const ok = await persistAndSubmitStaffCoApplicants(applicationId)
        if (!ok) return
      }
      let useDelegated = delegatedApp
      if (variant === 'borrower' && !useDelegated) {
        const app = await getApplication(applicationId)
        useDelegated = isDelegatedBorrowerIntake(app)
        if (useDelegated) setDelegatedApp(true)
      }
      if (useDelegated) {
        await submitDelegatedBorrowerIntake(applicationId)
        notifySuccess('Application submitted for lender review.')
        void navigate(`/borrower/applications/${applicationId}`, { replace: true })
      } else {
        await submitApplicationForKyc(applicationId)
        notifySuccess('Application submitted for verification.')
        if (mode === 'BORROWER_SELF_SERVICE') {
          void navigate(`/borrower/applications/${applicationId}`, { replace: true })
        } else {
          void navigate(`/applications/${applicationId}`, { replace: true })
        }
      }
      if (variant === 'borrower') reloadIncomplete()
    } catch (err) {
      const dupField = duplicateFieldFromError(err)
      if (dupField) {
        const dup = duplicateFieldErrors(err)
        if (dup) setFieldErrors(dup)
        const target = intakeStepForDuplicateField(dupField, steps)
        if (target != null) setStep(target)
        notifyError(err, 'Please fix the highlighted identity details before submitting.')
        return
      }
      const msg = intakeErrorMessage(err, 'Could not submit application for verification.')
      setError(msg)
      notifyError(err, 'Could not submit application for verification.')
    } finally {
      setBusy(false)
    }
  }

  async function onUploadFile(documentType: string, file: File | null) {
    if (!file || !applicationId) return
    setError(null)
    setBusy(true)
    try {
      await uploadDocument(applicationId, file, documentType)
      setForm((f) => ({ ...f, documentUploaded: { ...f.documentUploaded, [documentType]: true } }))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed.')
    } finally {
      setBusy(false)
    }
  }

  if (variant === 'borrower' && coApplicantPartyId && resumeApplicationId) {
    return <CoApplicantPortal applicationId={resumeApplicationId} partyId={coApplicantPartyId} />
  }

  if (anchorBranch && variant === 'staff') {
    return (
      <div>
        <PageHeader title={pageTitle(mode)} description={pageDescription(mode)} />
        <p className="mb-4 text-sm text-slate-600">
          <Link to="/applications" className="font-medium text-slate-800 underline">
            ← Applications
          </Link>
        </p>
        <AnchorIntakeWizard
          variant="embedded"
          staffIntakeMode={mode}
          initialRequest={anchorBranch}
          onExitEmbedded={() => {
            setAnchorBranch(null)
            setError(null)
          }}
        />
      </div>
    )
  }

  return (
    <div>
      {variant === 'staff' ? (
        <>
          <PageHeader
            title={editApplicationId ? 'Continue application intake' : pageTitle(mode)}
            description={
              editApplicationId
                ? isStaffPostSubmitEditStatus(resumedAppStatus)
                  ? 'Update borrower and KYC details while this application is with the Relationship Manager. Changes are saved to the existing application.'
                  : 'Resume filling this draft application. Fields follow the active workflow configuration.'
                : pageDescription(mode)
            }
          />
          <p className="mb-4 text-sm text-slate-600">
            <Link
              to={editApplicationId ? `/applications/${editApplicationId}` : '/applications'}
              className="font-medium text-slate-800 underline"
            >
              ← {editApplicationId ? 'Application details' : 'Applications'}
            </Link>
          </p>
        </>
      ) : (
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-slate-900">{pageTitle(mode)}</h1>
          <p className="mt-1 text-sm text-slate-600">{pageDescription(mode)}</p>
        </div>
      )}

      {hydrating || resumeError ? (
        <div className="mb-4 rounded border border-slate-200 bg-white p-3 text-sm text-slate-800 shadow-sm" role="status">
          {hydrating ? 'Loading your saved application…' : null}
          {resumeError ? <span className="text-rose-800">{resumeError}</span> : null}
        </div>
      ) : null}

      {variant === 'borrower' && sentBackNotes ? (
        <div className="mb-4 rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950">
          <p className="font-medium">Changes requested by the lender</p>
          <p className="mt-1 whitespace-pre-wrap">{sentBackNotes}</p>
        </div>
      ) : null}

      {variant === 'borrower' && incompleteServer.length > 0 && !applicationId ? (
        <div className="mb-4 rounded border border-indigo-200 bg-indigo-50/90 p-4 text-sm text-indigo-950">
          <p className="font-medium">Continue your application</p>
          <p className="mt-1 text-indigo-900">
            You have {incompleteServer.length === 1 ? 'an application' : `${incompleteServer.length} applications`}{' '}
            waiting to be completed. Pick up where you left off.
          </p>
          <ul className="mt-3 space-y-2">
            {incompleteServer.map((a) => (
              <li
                key={a.applicationId}
                className="flex flex-wrap items-center justify-between gap-2 border-b border-indigo-200/80 pb-2 last:border-0 last:pb-0"
              >
                <span>
                  <span className="font-medium">{a.applicationNumber}</span>
                  <span className="text-indigo-800"> — {a.friendlyStatus}</span>
                </span>
                <button
                  type="button"
                  className="rounded-md bg-indigo-900 px-3 py-1.5 text-xs font-medium text-white"
                  onClick={() => {
                    setSearchParams((prev) => {
                      const n = new URLSearchParams(prev)
                      n.set('resume', a.applicationId)
                      if (a.partyRole === 'CO_APPLICANT' && a.partyId) {
                        n.set('partyId', a.partyId)
                      } else {
                        n.delete('partyId')
                      }
                      return n
                    })
                    setResumeLoaded(false)
                  }}
                >
                  Continue filling
                  {a.partyRole === 'CO_APPLICANT' ? ' (co-applicant)' : ''}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {variant === 'borrower' && delegatedApp ? (
        <div className="mb-4 rounded border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800">
          Your lender started this application. Complete the remaining steps and submit for their review.
        </div>
      ) : null}

      {workflowsState === 'loading' ? <p className="mb-4 text-sm text-slate-600">Loading active workflows…</p> : null}
      {workflowsState === 'err' && workflowsError ? <ErrorState message={workflowsError} /> : null}
      {workflowsState === 'ok' && activeWorkflows.length === 0 ? (
        <p className="mb-4 bt-alert bt-alert-warning">
          There are no active workflows. Add and activate a workflow in Workflows before creating an application.
        </p>
      ) : null}
      {error ? <ErrorState message={error} /> : null}

      <Stepper step={step} labels={stepLabels} />

      {step === steps.product ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Product &amp; request</h2>
          {variant === 'borrower' &&
          isInvoiceDiscountingProduct(form.loanProduct) &&
          form.selectedSubProgramId ? (
            <LinkedAnchorProgramReadonly subProgramId={form.selectedSubProgramId} />
          ) : null}
          {mode === 'SALES_ASSISTED' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Sales officer name *</span>
                <input
                  className="bt-input w-full"
                  value={form.salesOfficerName}
                  onChange={(e) => setForm((f) => ({ ...f, salesOfficerName: e.target.value }))}
                  disabled={!!applicationId}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Sales / branch ID</span>
                <input
                  className="bt-input w-full"
                  value={form.salesOfficerId}
                  onChange={(e) => setForm((f) => ({ ...f, salesOfficerId: e.target.value }))}
                  disabled={!!applicationId}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Borrower mobile *</span>
                <input
                  type="tel"
                  className="bt-input w-full"
                  value={form.borrowerMobile}
                  onChange={(e) => setForm((f) => ({ ...f, borrowerMobile: e.target.value }))}
                />
              </label>
              <label className="flex items-start gap-2 text-sm text-slate-800 sm:col-span-2">
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={form.salesBorrowerAck}
                  onChange={(e) => setForm((f) => ({ ...f, salesBorrowerAck: e.target.checked }))}
                />
                <span>
                  I confirm the borrower has agreed to start this application and to share the details with the lender. *
                </span>
              </label>
            </div>
          ) : null}
          {variant === 'staff' ? (
            <>
              {mode === 'ADMIN_INTERNAL' ? (
                <p className="text-xs text-slate-500">
                  Internal: pick the loan product first. For invoice discounting, choose borrower or anchor onboarding;
                  all other products use the standard borrower flow unchanged.
                </p>
              ) : null}
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block text-sm text-slate-700 sm:col-span-2">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Loan product *</span>
                  <select
                    className="bt-input w-full text-slate-900 disabled:cursor-not-allowed disabled:bg-slate-50"
                    value={staffProductList.length === 0 ? '' : form.loanProduct}
                    onChange={(e) => {
                      const lp = e.target.value
                      setForm((f) => {
                        const choice = isInvoiceDiscountingProduct(lp) ? f.invoiceOnboardingChoice : ''
                        const segment = choice === 'ANCHOR' ? 'ANCHOR' : 'BORROWER'
                        const bt = segment === 'ANCHOR' ? ANCHOR_BORROWER_TYPE : f.borrowerType
                        return {
                          ...f,
                          loanProduct: lp,
                          invoiceOnboardingChoice: choice,
                          ...(segment === 'ANCHOR' ? { borrowerType: ANCHOR_BORROWER_TYPE } : {}),
                          workflowId: f.workflowId,
                        }
                      })
                    }}
                    disabled={workflowsState !== 'ok' || !staffProductList.length || productLocked}
                  >
                    {staffProductList.length === 0 ? <option value="">(none)</option> : null}
                    {staffProductList.map((c) => (
                      <option key={c} value={c}>
                        {workflowLoanProductDisplayName(c)}
                      </option>
                    ))}
                  </select>
                  {mode === 'ADMIN_INTERNAL' &&
                  workflowsForSelectedProduct.length > 1 &&
                  !(isInvoiceDiscountingProduct(form.loanProduct) && form.invoiceOnboardingChoice === 'ANCHOR') ? (
                    <label className="mt-2 block text-sm text-slate-700">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Workflow (admin / test) *</span>
                      <select
                        className="bt-input w-full text-slate-900 disabled:cursor-not-allowed disabled:bg-slate-50"
                        value={form.workflowId}
                        onChange={(e) => setForm((f) => ({ ...f, workflowId: e.target.value }))}
                        disabled={workflowsState !== 'ok' || productLocked}
                      >
                        <option value="">— Select workflow —</option>
                        {workflowsForSelectedProduct.map((w) => (
                          <option key={w.id} value={w.id}>
                            {w.name} (v{w.version})
                          </option>
                        ))}
                      </select>
                    </label>
                  ) : selectedWorkflow ? (
                    <p className="mt-1.5 text-xs text-slate-500">
                      Workflow pinned by Customer Category:{' '}
                      <span className="font-medium text-slate-800">{selectedWorkflow.name}</span> (v
                      {selectedWorkflow.version})
                    </p>
                  ) : (
                    <p className="mt-1.5 text-xs text-slate-500">
                      Workflow Version is pinned after Customer Category selection — not chosen independently.
                    </p>
                  )}
                </label>

                {isInvoiceDiscountingProduct(form.loanProduct) ? (
                  <InvoiceOnboardingTypeCards
                    value={form.invoiceOnboardingChoice}
                    onChange={(choice) =>
                      setForm((f) => {
                        const segment = choice === 'ANCHOR' ? 'ANCHOR' : 'BORROWER'
                        return {
                          ...f,
                          invoiceOnboardingChoice: choice,
                          workflowId: f.workflowId,
                          ...(choice === 'ANCHOR'
                            ? { purpose: '', borrowerType: ANCHOR_BORROWER_TYPE }
                            : {}),
                        }
                      })
                    }
                    disabled={productLocked}
                  />
                ) : null}

                {!(isInvoiceDiscountingProduct(form.loanProduct) && form.invoiceOnboardingChoice === 'ANCHOR') ? (
                  <>
                    <label className="block text-sm text-slate-700">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Borrower type *</span>
                      <select
                        className="bt-input w-full text-slate-900"
                        value={form.borrowerType}
                        onChange={(e) => {
                          const bt = e.target.value as BorrowerType
                          const list = productsForBorrowerType(activeWorkflows, bt)
                          setForm((f) => {
                            const loanProduct = list.some((w) => w.loanProduct === f.loanProduct)
                              ? f.loanProduct
                              : (list[0]?.loanProduct ?? '')
                            return {
                              ...f,
                              borrowerType: bt,
                              loanProduct,
                              workflowId: f.workflowId,
                            }
                          })
                        }}
                        disabled={workflowsState !== 'ok' || !activeWorkflows.length || productLocked}
                      >
                        {BORROWER_TYPES.map((t) => (
                          <option key={t} value={t}>
                            {BORROWER_TYPE_LABELS[t]}
                          </option>
                        ))}
                      </select>
                    </label>
                    <div className="hidden sm:block" aria-hidden />
                    {variant === 'staff' &&
                    productsForType.length > 0 &&
                    !activeCatalogHasSecuredProduct(activeWorkflows, form.borrowerType) &&
                    (canAccessAdminConfigNav(user?.role ?? '') || import.meta.env.DEV) ? (
                      <p
                        className="sm:col-span-2 mt-2 rounded border border-amber-200 bg-amber-50 px-2 py-1.5 text-xs text-amber-950"
                        role="note"
                      >
                        No secured products are active. Configure workflow for Loan Against Property, Loan Against
                        Securities, or Loan Against Gold.
                      </p>
                    ) : null}
                  </>
                ) : (
                  <p className="sm:col-span-2 text-sm text-slate-600">
                    Next takes you to anchor corporate and identity steps. Amount and tenure below still apply.
                  </p>
                )}

                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Requested amount (INR) *</span>
                  <input
                    type="number"
                    min={0.01}
                    step="0.01"
                    className="bt-input w-full text-slate-900 tabular-nums"
                    value={form.requestedAmount}
                    onChange={(e) => setForm((f) => ({ ...f, requestedAmount: e.target.value }))}
                    required
                  />
                  <AmountInputHint value={form.requestedAmount} />
                </label>
                <IntakeTenureField
                  workflow={selectedWorkflow}
                  value={form.tenureMonths}
                  lmsTenureUnit={form.lmsTenureUnit}
                  onChange={(v) => setForm((f) => ({ ...f, tenureMonths: v }))}
                />
                {!isInvoiceDiscountingProduct(form.loanProduct) ? (
                  <LmsWorkflowConfigReadonly
                    lmsProductCode={form.lmsProductCode}
                    lmsTenureUnit={form.lmsTenureUnit}
                  />
                ) : null}
                {shouldCollectLoanPurposeField(selectedWorkflow, true) &&
                !(
                  isInvoiceDiscountingProduct(form.loanProduct) && form.invoiceOnboardingChoice === 'ANCHOR'
                ) ? (
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">
                      Loan purpose
                      {selectedWorkflow?.intakeConfig?.personalFields?.loanPurpose?.required !== false ? ' *' : ''}
                    </span>
                    <select
                      className="bt-input w-full text-slate-900"
                      value={form.loanPurpose}
                      onChange={(e) => {
                        const code = e.target.value
                        setForm((f) => ({
                          ...f,
                          loanPurpose: code,
                          purpose: code ? labelForLoanPurpose(code, selectedWorkflow) : '',
                        }))
                      }}
                    >
                      <option value="">— Select loan purpose —</option>
                      {resolveLoanPurposeOptions(selectedWorkflow).map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  </label>
                ) : null}
              </div>
            </>
          ) : (
            <>
              {mode === 'ADMIN_INTERNAL' ? (
                <p className="text-xs text-slate-500">
                  Internal: application created from this path records your user id in personal info where applicable.
                  Ensure product matches an active workflow for the selected borrower class.
                </p>
              ) : null}
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Borrower type *</span>
                  <select
                    className="bt-input w-full text-slate-900"
                    value={form.borrowerType}
                    onChange={(e) => {
                      const bt = e.target.value as BorrowerType
                      const list = productsForBorrowerType(activeWorkflows, bt)
                      const loanProduct = list[0]?.loanProduct ?? ''
                      setForm((f) => ({
                        ...f,
                        borrowerType: bt,
                        loanProduct,
                        workflowId: f.workflowId,
                      }))
                    }}
                    disabled={workflowsState !== 'ok' || !activeWorkflows.length || productLocked}
                  >
                    {BORROWER_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {BORROWER_TYPE_LABELS[t]}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Loan product *</span>
                  <select
                    className="bt-input w-full text-slate-900 disabled:cursor-not-allowed disabled:bg-slate-50"
                    value={productsForType.length === 0 ? '' : form.loanProduct}
                    onChange={(e) => {
                      const lp = e.target.value
                      setForm((f) => ({
                        ...f,
                        loanProduct: lp,
                        workflowId: f.workflowId,
                      }))
                    }}
                    disabled={workflowsState !== 'ok' || !productsForType.length || productLocked}
                  >
                    {productsForType.length === 0 ? <option value="">(none for this type)</option> : null}
                    {productsForType.map((w) => (
                      <option key={w.id} value={w.loanProduct}>
                        {workflowLoanProductDisplayName(w.loanProduct)}
                      </option>
                    ))}
                  </select>
                  {workflowsForSelectedProduct.length > 1 ? (
                    <label className="mt-2 block text-sm text-slate-700">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Workflow *</span>
                      <select
                        className="bt-input w-full text-slate-900 disabled:cursor-not-allowed disabled:bg-slate-50"
                        value={form.workflowId}
                        onChange={(e) => setForm((f) => ({ ...f, workflowId: e.target.value }))}
                        disabled={workflowsState !== 'ok' || productLocked}
                      >
                        <option value="">— Select workflow —</option>
                        {workflowsForSelectedProduct.map((w) => (
                          <option key={w.id} value={w.id}>
                            {w.name} (v{w.version})
                          </option>
                        ))}
                      </select>
                    </label>
                  ) : selectedWorkflow ? (
                    <p className="mt-1.5 text-xs text-slate-500">
                      Active workflow: <span className="font-medium text-slate-800">{selectedWorkflow.name}</span> (v
                      {selectedWorkflow.version})
                    </p>
                  ) : null}
                </div>
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Requested amount (INR) *</span>
                  <input
                    type="number"
                    min={0.01}
                    step="0.01"
                    className="bt-input w-full text-slate-900 tabular-nums"
                    value={form.requestedAmount}
                    onChange={(e) => setForm((f) => ({ ...f, requestedAmount: e.target.value }))}
                    required
                  />
                  <AmountInputHint value={form.requestedAmount} />
                </label>
                <IntakeTenureField
                  workflow={selectedWorkflow}
                  value={form.tenureMonths}
                  lmsTenureUnit={form.lmsTenureUnit}
                  onChange={(v) => setForm((f) => ({ ...f, tenureMonths: v }))}
                />
                {!isInvoiceDiscountingProduct(form.loanProduct) ? (
                  <LmsWorkflowConfigReadonly
                    lmsProductCode={form.lmsProductCode}
                    lmsTenureUnit={form.lmsTenureUnit}
                  />
                ) : null}
                {shouldCollectLoanPurposeField(selectedWorkflow, true) &&
                !(
                  isInvoiceDiscountingProduct(form.loanProduct) && form.invoiceOnboardingChoice === 'ANCHOR'
                ) ? (
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">
                      Loan purpose
                      {selectedWorkflow?.intakeConfig?.personalFields?.loanPurpose?.required !== false ? ' *' : ''}
                    </span>
                    <select
                      className="bt-input w-full text-slate-900"
                      value={form.loanPurpose}
                      onChange={(e) => {
                        const code = e.target.value
                        setForm((f) => ({
                          ...f,
                          loanPurpose: code,
                          purpose: code ? labelForLoanPurpose(code, selectedWorkflow) : '',
                        }))
                      }}
                    >
                      <option value="">— Select loan purpose —</option>
                      {resolveLoanPurposeOptions(selectedWorkflow).map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  </label>
                ) : null}
              </div>
            </>
          )}
          {applicationId && productLocked ? (
            <p className="text-xs text-amber-800">
              Product and borrower class are fixed for this application so the workflow does not get out of sync. You
              can still change amount, tenure, and purpose.
            </p>
          ) : null}
        </section>
      ) : null}

      {needPlpAnchorStep && step === steps.plp ? (
        <SelectAnchorProgramStep
          selectedSubProgramId={form.selectedSubProgramId}
          onSelect={(subProgramId) =>
            setForm((f) => ({ ...f, selectedSubProgramId: subProgramId }))
          }
        />
      ) : null}

      {step === steps.borrower ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Basic borrower details</h2>
          {form.borrowerType === 'INDIVIDUAL' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Full name (as per PAN) *</span>
                <input
                  className="bt-input w-full"
                  value={form.fullName}
                  onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
                  autoComplete="name"
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Mobile *</span>
                <input
                  type="tel"
                  className="bt-input w-full"
                  value={form.mobile}
                  onChange={(e) => {
                    clearFieldError('mobile')
                    setForm((f) => ({ ...f, mobile: e.target.value }))
                  }}
                  autoComplete="tel"
                />
                <IntakeFieldError message={fieldErrors.mobile} />
              </label>
              {mode === 'SALES_ASSISTED' && form.borrowerMobile ? (
                <p className="text-xs text-slate-500 sm:col-span-2">
                  Sales capture: main contact number was <span className="font-mono text-slate-800">{form.borrowerMobile}</span>
                  {form.mobile && form.mobile.replace(/\D/g, '') !== form.borrowerMobile.replace(/\D/g, '') ? (
                    <span> (you are overriding it in the next field for this create)</span>
                  ) : null}
                </p>
              ) : null}
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Email *</span>
                <input
                  type="email"
                  className="bt-input w-full"
                  value={form.email}
                  onChange={(e) => {
                    clearFieldError('email')
                    setForm((f) => ({ ...f, email: e.target.value }))
                  }}
                />
                <IntakeFieldError message={fieldErrors.email} />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">
                  Date of birth
                  {shouldCollectPersonalField(selectedWorkflow, 'dateOfBirth', false) &&
                  selectedWorkflow?.intakeConfig?.personalFields?.dateOfBirth?.required
                    ? ' *'
                    : ''}
                </span>
                <input
                  type="date"
                  className="bt-input w-full"
                  value={form.dateOfBirth}
                  onChange={(e) => setForm((f) => ({ ...f, dateOfBirth: e.target.value }))}
                />
              </label>
              {shouldCollectPersonalField(selectedWorkflow, 'gender', false) ? (
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">
                    Gender
                    {selectedWorkflow?.intakeConfig?.personalFields?.gender?.required ? ' *' : ''}
                  </span>
                  <select
                    className="bt-input w-full"
                    value={form.gender}
                    onChange={(e) => setForm((f) => ({ ...f, gender: e.target.value }))}
                  >
                    <option value="">Select</option>
                    {(selectedWorkflow?.intakeConfig?.personalFields?.gender?.allowedValues ?? [
                      'MALE',
                      'FEMALE',
                      'OTHER',
                      'PREFER_NOT_TO_SAY',
                    ]).map((g) => (
                      <option key={g} value={g}>
                        {g.replaceAll('_', ' ')}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
              {form.borrowerType === 'INDIVIDUAL' &&
              shouldCollectPersonalField(selectedWorkflow, 'occupation', true) ? (
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">
                    Occupation
                    {selectedWorkflow?.intakeConfig?.personalFields?.occupation?.required !== false ? ' *' : ''}
                  </span>
                  <select
                    className="bt-input w-full"
                    value={form.occupation}
                    onChange={(e) => {
                      const code = e.target.value
                      setForm((f) => ({
                        ...f,
                        occupation: code,
                        occupationIndustry: code ? labelForOccupation(code, selectedWorkflow) : '',
                      }))
                    }}
                  >
                    <option value="">— Select occupation —</option>
                    {resolveOccupationOptions(selectedWorkflow).map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Address</span>
                <input
                  className="bt-input w-full"
                  value={form.addressLine}
                  onChange={(e) => setForm((f) => ({ ...f, addressLine: e.target.value }))}
                />
              </label>
              <IndiaStateCityPincodeFields
                stateValue={form.state}
                cityValue={form.city}
                pincodeValue={form.pincode}
                onStateChange={(v) => setForm((f) => ({ ...f, state: v, city: '' }))}
                onCityChange={(v) => setForm((f) => ({ ...f, city: v }))}
                onPincodeChange={(v) => setForm((f) => ({ ...f, pincode: v }))}
                allowedStates={workflowAllowedStates}
              />
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Business / entity name *</span>
                <input
                  className="bt-input w-full"
                  value={form.businessName}
                  onChange={(e) => setForm((f) => ({ ...f, businessName: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Contact person *</span>
                <input
                  className="bt-input w-full"
                  value={form.contactPersonName}
                  onChange={(e) => setForm((f) => ({ ...f, contactPersonName: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Contact mobile *</span>
                <input
                  type="tel"
                  className="bt-input w-full"
                  value={form.contactMobile}
                  onChange={(e) => {
                    clearFieldError('mobile')
                    setForm((f) => ({ ...f, contactMobile: e.target.value }))
                  }}
                />
                <IntakeFieldError message={fieldErrors.mobile} />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Contact email</span>
                <input
                  type="email"
                  className="bt-input w-full"
                  value={form.contactEmail}
                  onChange={(e) => {
                    clearFieldError('email')
                    setForm((f) => ({ ...f, contactEmail: e.target.value }))
                  }}
                />
                <IntakeFieldError message={fieldErrors.email} />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">GSTIN *</span>
                <input
                  className="bt-input w-full"
                  value={form.gstin}
                  onChange={(e) => setForm((f) => ({ ...f, gstin: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Udyam (if applicable)</span>
                <input
                  className="bt-input w-full"
                  value={form.udyam}
                  onChange={(e) => setForm((f) => ({ ...f, udyam: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Business address</span>
                <input
                  className="bt-input w-full"
                  value={form.businessAddress}
                  onChange={(e) => setForm((f) => ({ ...f, businessAddress: e.target.value }))}
                />
              </label>
              <IndiaStateCityPincodeFields
                stateValue={form.businessState}
                cityValue={form.businessCity}
                pincodeValue={form.businessPincode}
                onStateChange={(v) => setForm((f) => ({ ...f, businessState: v, businessCity: '' }))}
                onCityChange={(v) => setForm((f) => ({ ...f, businessCity: v }))}
                onPincodeChange={(v) => setForm((f) => ({ ...f, businessPincode: v }))}
                allowedStates={workflowAllowedStates}
              />
            </div>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <WorkflowCustomIntakeFields
              workflow={selectedWorkflow}
              values={form.customFieldValues}
              onChange={(key, value) =>
                setForm((f) => ({
                  ...f,
                  customFieldValues: { ...f.customFieldValues, [key]: value },
                }))
              }
            />
          </div>
          {isInvoiceDiscountingProduct(form.loanProduct) &&
          (form.invoiceOnboardingChoice === 'BORROWER' || mode === 'BORROWER_SELF_SERVICE') ? (
            <div>
              <p className="mb-2 text-sm font-medium text-slate-800">Anchor relationship details</p>
              <InvoiceDiscountingVintageFields
                form={form}
                onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
              />
            </div>
          ) : null}
          {needPlpAnchorStep && form.selectedSubProgramId ? (
            <LinkedAnchorProgramReadonly subProgramId={form.selectedSubProgramId} />
          ) : null}
        </section>
      ) : null}

      {step === steps.borrower && coApplicantConfig ? (
        <CoApplicantsSection
          coApplicants={coApplicants}
          onChange={(rows) => {
            setCoApplicants(rows)
            if (rows.length === 0) setStaffMultiPartyPath(null)
          }}
          min={coApplicantMin}
          max={coApplicantMax}
          showCompletionPathChooser={variant === 'staff'}
          completionPath={staffMultiPartyPath}
          onCompletionPathChange={setStaffMultiPartyPath}
        />
      ) : null}

      {staffCoFillIndex != null && coApplicants[staffCoFillIndex] ? (
        <StaffCoApplicantDetailForm
          index={staffCoFillIndex}
          total={coApplicants.length}
          row={coApplicants[staffCoFillIndex]!}
          onChange={(patch) => {
            setCoApplicants((prev) => {
              const next = [...prev]
              next[staffCoFillIndex] = { ...next[staffCoFillIndex]!, ...patch }
              return next
            })
          }}
          collectDob={coApplicantConfig?.personalFields?.dateOfBirth?.collect !== false}
          collectGender={coApplicantConfig?.personalFields?.gender?.collect !== false}
          collectOccupation={coApplicantConfig?.personalFields?.occupation?.collect === true}
          requireDob={coApplicantConfig?.personalFields?.dateOfBirth?.required === true}
        />
      ) : null}

      {step === steps.category && applicationId ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Customer Category</h2>
          <p className="text-xs text-slate-600">
            Category selection pins the exact Workflow Version and Policy Document for this application.
            Intake fields on the next steps come from that pin — not from a product default.
          </p>
          <CategorySelectionPanel
            applicationId={applicationId}
            actor={user?.name || 'user'}
            actorRole={variant === 'staff' ? 'RM' : 'CUSTOMER'}
            onSelected={(result) => {
              const wf = result.selected?.workflowId
              if (wf) setForm((f) => ({ ...f, workflowId: wf }))
            }}
          />
        </section>
      ) : null}

      {step === steps.collateral && needColl && detectSecuredCollateralKind(form.loanProduct) ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Collateral</h2>
          <p className="text-xs text-slate-600">Secured product — capture the asset offered and upload supporting files.</p>
          <CollateralIntakeFields
            kind={detectSecuredCollateralKind(form.loanProduct)!}
            form={form}
            setForm={setForm}
            applicationId={applicationId}
            onUploadFile={onUploadFile}
            busy={busy}
            documentWarning={collateralDocWarn}
          />
        </section>
      ) : null}

      {step === steps.kyc ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Identity &amp; KYC</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            {shouldShowKycIntakeField(selectedWorkflow, 'PAN_VERIFY', true) ? (
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">PAN *</span>
              <input
                className="bt-input w-full font-mono uppercase"
                value={form.panNumber}
                onChange={(e) => {
                  clearFieldError('panNumber')
                  setForm((f) => ({ ...f, panNumber: e.target.value.toUpperCase() }))
                }}
                maxLength={10}
                autoComplete="off"
              />
              <IntakeFieldError message={fieldErrors.panNumber} />
            </label>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'AADHAAR_OTP', true) ? (
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Aadhaar (last 4 digits, or full 12 for internal use)</span>
              <input
                className="bt-input w-full"
                value={form.aadhaar}
                onChange={(e) => setForm((f) => ({ ...f, aadhaar: e.target.value }))}
                inputMode="numeric"
              />
            </label>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'VOTER_ID_VERIFY', false) ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Voter ID (EPIC)</span>
              <input
                className="bt-input w-full uppercase"
                value={form.voterId}
                onChange={(e) => setForm((f) => ({ ...f, voterId: e.target.value.toUpperCase() }))}
              />
            </label>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'DL_VERIFY', false) ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Driving licence number</span>
              <input
                className="bt-input w-full uppercase"
                value={form.dlNumber}
                onChange={(e) => setForm((f) => ({ ...f, dlNumber: e.target.value.toUpperCase() }))}
              />
            </label>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'BANK_PENNY_DROP', false) ? (
              <>
                <label className="block text-sm text-slate-700 sm:col-span-2">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Account number *</span>
                  <input
                    className="bt-input w-full font-mono"
                    value={form.bankAccountNumber}
                    onChange={(e) => {
                      clearFieldError('bankAccountNumber')
                      setForm((f) => ({ ...f, bankAccountNumber: e.target.value }))
                    }}
                    inputMode="numeric"
                  />
                  <IntakeFieldError message={fieldErrors.bankAccountNumber} />
                </label>
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">IFSC *</span>
                  <input
                    className="bt-input w-full font-mono uppercase"
                    value={form.ifscCode}
                    onChange={(e) => {
                      clearFieldError('ifscCode')
                      setForm((f) => ({ ...f, ifscCode: e.target.value.toUpperCase() }))
                    }}
                    maxLength={11}
                  />
                  <IntakeFieldError message={fieldErrors.ifscCode} />
                </label>
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Bank name</span>
                  <input
                    className="bt-input w-full"
                    value={form.bankName}
                    onChange={(e) => setForm((f) => ({ ...f, bankName: e.target.value }))}
                  />
                </label>
              </>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'AADHAAR_OTP', true) ? (
            <label className="flex items-center gap-2 text-sm text-slate-800 sm:col-span-2">
              <input
                type="checkbox"
                checked={form.mobileLinkedAadhaar}
                onChange={(e) => setForm((f) => ({ ...f, mobileLinkedAadhaar: e.target.checked }))}
              />
              The mobile number we hold is the same as (or can be used with) the Aadhaar-linked number for verification.
            </label>
            ) : null}
            {isBusinessBorrowerType(form.borrowerType) ? (
              <>
                <p className="text-xs text-slate-500 sm:col-span-2">
                  Business verification: reconfirm GSTIN and Udyam for processing; CIN is required for companies.
                </p>
                {shouldShowKycIntakeField(selectedWorkflow, 'GSTIN_VERIFY', true) ? (
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">GSTIN *</span>
                  <input
                    className="bt-input w-full"
                    value={form.gstin}
                    onChange={(e) => {
                      clearFieldError('gstin')
                      setForm((f) => ({ ...f, gstin: e.target.value }))
                    }}
                  />
                  <IntakeFieldError message={fieldErrors.gstin} />
                </label>
                ) : null}
                {shouldShowKycIntakeField(selectedWorkflow, 'UDYAM_VERIFY', true) ? (
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Udyam</span>
                  <input
                    className="bt-input w-full"
                    value={form.udyam}
                    onChange={(e) => setForm((f) => ({ ...f, udyam: e.target.value }))}
                  />
                </label>
                ) : null}
                {form.borrowerType === 'COMPANY' && shouldShowKycIntakeField(selectedWorkflow, 'CIN_MCA21', true) ? (
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">CIN / MCA *</span>
                    <input
                      className="bt-input w-full"
                      value={form.cin}
                      onChange={(e) => setForm((f) => ({ ...f, cin: e.target.value }))}
                    />
                  </label>
                ) : null}
              </>
            ) : null}
          </div>

          {requiresItr ? (
            <ItrReturnFormsIntakePanel
              variant={variant}
              username={itrUsername}
              password={itrPassword}
              consent={itrConsent}
              success={itrSuccess}
              statusLabel={itrStatusLabel}
              error={itrError || itrLongRun.state.errorMessage}
              busy={itrLongRun.state.blocking || busy}
              processingBackground={itrLongRun.state.showBackgroundNotice}
              terminalPhase={itrLongRun.state.phase}
              onUsernameChange={setItrUsername}
              onPasswordChange={setItrPassword}
              onConsentChange={setItrConsent}
              onVerify={() => {
                void (async () => {
                  if (!applicationId) return
                  if (itrLongRun.state.inFlight) return
                  setItrError(null)
                  if (!itrUsername.trim() || !itrPassword || !itrConsent) {
                    setItrError('Username, password, and consent are required.')
                    return
                  }
                  try {
                    await itrLongRun.run(async () => {
                      const res = await submitBorrowerItrReturnForms(applicationId, {
                        username: itrUsername.trim(),
                        password: itrPassword,
                        consent: true,
                      })
                      setItrPassword('')
                      const ok = String(res.outcome).toUpperCase() === 'SUCCESS'
                      setItrSuccess(ok)
                      setItrStatusLabel(String(res.outcome))
                      if (!ok) {
                        const msg = res.errorMessage || 'ITR verification failed. Check credentials and retry.'
                        setItrError(msg)
                        itrLongRun.markOutcome(false, res.outcome, msg)
                      } else {
                        setItrError(null)
                        notifySuccess('ITR verified successfully.')
                        itrLongRun.markOutcome(true, res.outcome)
                      }
                      return res
                    })
                  } catch (err) {
                    setItrPassword('')
                    setItrError(intakeErrorMessage(err, 'ITR verification failed.'))
                  }
                })()
              }}
            />
          ) : null}

          {requiresGstAnalysis ? (
            <GstAnalysisIntakePanel
              applicationId={applicationId}
              variant={variant}
              gstin={gstAnalysisGstin}
              consent={gstAnalysisConsent}
              prepared={gstAnalysisPrepared}
              statusLabel={gstAnalysisStatusLabel}
              reportSuccess={gstAnalysisReportSuccess}
              busy={gstAnalysisBusy}
              disabled={busy}
              error={gstAnalysisError}
              onGstinChange={setGstAnalysisGstin}
              onConsentChange={setGstAnalysisConsent}
              onError={setGstAnalysisError}
              onBusy={setGstAnalysisBusy}
              onPreparedChange={(prepared, status) => {
                setGstAnalysisPrepared(prepared)
                if (status != null) setGstAnalysisStatusLabel(status)
              }}
            />
          ) : null}
        </section>
      ) : null}

      {step === steps.consent ? (
        <section className="space-y-3 bt-card p-5">
          <h2 className="bt-card-title">Consents</h2>
          <p className="text-xs text-slate-600">{consentHelper(mode)}</p>
          <div className="space-y-2 text-sm text-slate-800">
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentKyc}
                onChange={(e) => setForm((f) => ({ ...f, consentKyc: e.target.checked }))}
              />
              <span>I consent to KYC verification, including use of the documents and details provided.</span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentBureau}
                onChange={(e) => setForm((f) => ({ ...f, consentBureau: e.target.checked }))}
              />
              <span>I consent to a credit bureau pull and related credit checks for this application.</span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentAccountAggregator}
                onChange={(e) => setForm((f) => ({ ...f, consentAccountAggregator: e.target.checked }))}
              />
              <span>
                I consent to bank statement or account-aggregated data access when required to assess the application.
              </span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentComms}
                onChange={(e) => setForm((f) => ({ ...f, consentComms: e.target.checked }))}
              />
              <span>I consent to receive updates about this application on WhatsApp, SMS, and email.</span>
            </label>
          </div>
        </section>
      ) : null}

      {step === steps.documents && applicationId ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Upload documents</h2>
          <p className="text-sm text-slate-600">
            Upload a clear copy for each required type so underwriters can complete checks without back-and-forth.
            Optional documents are listed under a separate expandable section.
          </p>
          <IntakeDocumentUploadList
            slots={documentSlots}
            documentUploaded={form.documentUploaded}
            busy={busy}
            onUploadFile={(documentType, file) => void onUploadFile(documentType, file)}
          />
        </section>
      ) : null}

      {step === steps.review && applicationId && staffCoFillIndex == null ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Review &amp; submit</h2>
          {docWarning ? (
            <p className="bt-alert bt-alert-warning">{docWarning}</p>
          ) : null}
          <div className="grid gap-3 text-sm sm:grid-cols-2">
            <div className="rounded border border-slate-100 p-3">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Product</h3>
              <p className="mt-1 text-slate-900">{form.loanProduct}</p>
              <p className="text-slate-600">
                {form.requestedAmount ? formatMoneyWithScale(Number(form.requestedAmount)) : '—'}
                {form.tenureMonths
                  ? ` · ${form.tenureMonths} ${tenureMagnitudeShortUnit(form.lmsTenureUnit)}`
                  : ''}
                {!isInvoiceDiscountingProduct(form.loanProduct) && form.lmsTenureUnit
                  ? ` · LMS ${lmsTenureUnitLabel(form.lmsTenureUnit)}`
                  : ''}
                {!isInvoiceDiscountingProduct(form.loanProduct) && form.lmsProductCode
                  ? ` · ${form.lmsProductCode}`
                  : ''}
              </p>
            </div>
            <div className="rounded border border-slate-100 p-3">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Borrower</h3>
              {form.borrowerType === 'INDIVIDUAL' ? (
                <p className="mt-1 text-slate-900">{form.fullName || '—'}</p>
              ) : (
                <p className="mt-1 text-slate-900">{form.businessName || '—'}</p>
              )}
            </div>
            <div className="rounded border border-slate-100 p-3 sm:col-span-2">
              <h3 className="text-xs font-semibold uppercase text-slate-500">KYC &amp; consents</h3>
              <p className="mt-1 text-slate-800">
                PAN {form.panNumber ? '— on file' : 'missing'} · Aadhaar {form.aadhaar ? 'captured' : 'missing'}
              </p>
              <p className="text-slate-800">
                Consents: {allConsentsChecked(form) ? 'all accepted' : 'incomplete (go back)'}
              </p>
            </div>
            {needColl ? (
              <div className="rounded border border-slate-100 p-3 sm:col-span-2">
                <h3 className="text-xs font-semibold uppercase text-slate-500">Collateral (declared)</h3>
                <p className="mt-1 text-sm text-slate-800">
                  {detectSecuredCollateralKind(form.loanProduct) === 'PROPERTY' && `Property — est. ₹${form.collateralEstimatedMarketValue || '—'}`}
                  {detectSecuredCollateralKind(form.loanProduct) === 'SHARES' && `Securities — ISIN ${form.collateralIsin || '—'}`}
                  {detectSecuredCollateralKind(form.loanProduct) === 'GOLD' && `Gold — est. ₹${form.collateralGoldEstimatedValue || '—'}`}
                </p>
              </div>
            ) : null}
            <div className="rounded border border-slate-100 p-3 sm:col-span-2">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Documents</h3>
              <ul className="mt-1 list-inside list-disc text-slate-700">
                {documentSlots.map((s) => (
                  <li key={s.documentType}>
                    {s.label} — {form.documentUploaded[s.documentType] ? 'uploaded' : 'optional / missing (demo)'}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>
      ) : null}

      <div className="mt-6 flex flex-wrap items-center gap-3">
        {step > 0 || staffCoFillIndex != null ? (
          <button
            type="button"
            onClick={goBack}
            disabled={busy}
            className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800"
          >
            Back
          </button>
        ) : null}
        {staffCoFillIndex != null ? (
          <button
            type="button"
            onClick={() => {
              void onSubmitFinal()
            }}
            disabled={busy}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy
              ? 'Please wait…'
              : staffCoFillIndex < coApplicants.length - 1
                ? 'Save & next co-applicant'
                : 'Save co-applicants & submit'}
          </button>
        ) : step < lastStep ? (
          <button
            type="button"
            onClick={() => {
              void goNext()
            }}
            disabled={
              busy ||
              (step === steps.product &&
                (workflowsState !== 'ok' ||
                  (variant === 'staff' &&
                    isInvoiceDiscountingProduct(form.loanProduct) &&
                    !form.invoiceOnboardingChoice))) ||
              (step === steps.category && !form.workflowId.trim()) ||
              (step === steps.kyc && !applicationId) ||
              (step === steps.documents && !applicationId) ||
              (step === steps.consent && !applicationId) ||
              (step === steps.borrower &&
                Boolean(coApplicantConfig) &&
                coApplicants.length > 0 &&
                !staffMultiPartyPath)
            }
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy
              ? 'Please wait…'
              : step === steps.borrower && staffMultiPartyPath === 'notify' && coApplicants.length > 0
                ? 'Save basics'
                : 'Continue'}
          </button>
        ) : (
          <button
            type="button"
            onClick={() => {
              void onSubmitFinal()
            }}
            disabled={busy}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy
              ? isStaffPostSubmitEditStatus(resumedAppStatus)
                ? 'Saving…'
                : 'Submitting…'
              : isStaffPostSubmitEditStatus(resumedAppStatus)
                ? 'Save changes'
                : delegatedApp
                  ? 'Submit for review'
                  : variant === 'borrower'
                    ? 'Submit application'
                    : staffMultiPartyPath === 'staff_fill' && coApplicants.length > 0
                      ? 'Continue to co-applicant details'
                      : 'Submit for verification'}
          </button>
        )}
        {staffCanCreateOrNotify &&
        step === steps.borrower &&
        staffCoFillIndex == null &&
        !anchorBranch &&
        notifyBasicsOk &&
        !isStaffPostSubmitEditStatus(resumedAppStatus) &&
        (coApplicants.length === 0 || staffMultiPartyPath === 'notify') ? (
          <button
            type="button"
            onClick={() => {
              void onNotifyBorrower()
            }}
            disabled={busy || (coApplicants.length > 0 && !staffMultiPartyPath)}
            className="rounded-md border border-indigo-400 bg-indigo-50 px-4 py-2 text-sm font-medium text-indigo-950 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy
              ? 'Please wait…'
              : coApplicants.length > 0
                ? 'Save draft & notify all applicants'
                : 'Save draft & notify borrower'}
          </button>
        ) : null}
        {variant === 'staff' ? (
          <Link
            to={editApplicationId ? `/applications/${editApplicationId}` : '/applications'}
            className="text-sm text-slate-600 underline"
          >
            Cancel
          </Link>
        ) : null}
      </div>
    </div>
  )
}
