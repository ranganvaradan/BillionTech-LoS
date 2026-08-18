import { useCallback, useEffect, useMemo, useState } from 'react'
import { getKycOutcome, getKycResults } from '@/api/kyc'
import { retryKycFlow, runKycFlow, submitApplicationForKyc } from '@/api/flow'
import { getWorkflow } from '@/api/workflows'
import { messageForKycAction, messageFromKycRunOutput, businessKycOutcomeLabel, businessKycStepLabel } from '@/api/kycErrorMessage'
import { updateApplication } from '@/api/applications'
import { ErrorState } from '@/components/ErrorState'
import { ProcessOverrideCard } from '@/components/ProcessOverrideCard'
import type { ApplicationResponse } from '@/types/application'
import type { KycStepResultResponse } from '@/types/kyc'
import type { WorkflowConfigResponse } from '@/types/workflow'
import { applicationPartyLabels } from '@/lib/applicationPartyLabels'
import { workflowAnyGroupKycHint } from '@/lib/workflow/workflowIntakeRules'
import {
  buildAnchorKycSavePayload,
  hydrateAnchorKycFields,
  isAnchorApplication,
} from '@/lib/intake/anchorKycBridge'
import {
  filterAnchorKycUiStepNames,
  filterAnchorKycUiSteps,
} from '@/lib/intake/anchorKycUi'
import { isBusinessBorrowerType } from '@/lib/intake/intakeTypes'
import type { BorrowerType } from '@/types/createApplication'

function pickStr(m: Record<string, unknown> | null | undefined, key: string): string {
  if (!m) return ''
  const v = m[key]
  return v == null ? '' : String(v)
}

function nonEmptyPayload(obj: Record<string, string>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(obj)) {
    const t = v.trim()
    if (t) out[k] = t
  }
  return out
}

const KNOWN_KYC_INPUT_STEPS = new Set([
  'PAN_VERIFY',
  'AADHAAR_OTP',
  'BANK_PENNY_DROP',
  'GSTIN_VERIFY',
  'UDYAM_VERIFY',
  'DL_VERIFY',
  'VOTER_ID_VERIFY',
  'MOBILE_OTP',
  'MNRL',
])

/** Steps that are not staff form inputs (borrower portal / dedicated admin panels only). */
const NO_STAFF_KYC_INPUT_STEPS = new Set([
  'ITR_RETURN_FORMS',
  'GST_ANALYSIS',
  'FACE_MATCH',
  'LIVENESS',
  'VIDEO_KYC',
  'CKYC_DOWNLOAD',
  'CKYC_UPLOAD',
  'BUREAU_PULL',
  'ESIGN_KFS',
  'ESIGN_AGREEMENT',
  'EMAIL_OTP',
  'AML_SCREENING',
])

function toStepName(step: Record<string, unknown>): string {
  const raw = step.step
  return raw == null ? '' : String(raw).trim().toUpperCase()
}

function summaryPairs(stepType: string, parsedData: Record<string, unknown> | null, errorMessage: string | null) {
  const data = parsedData ?? {}
  const s = stepType.toUpperCase()
  const read = (...keys: string[]) => {
    for (const key of keys) {
      const value = data[key]
      if (value != null && String(value).trim() !== '') return String(value)
    }
    return ''
  }
  const out: Array<{ label: string; value: string }> = []
  if (s === 'PAN_VERIFY') {
    const name = read('name', 'fullName')
    if (name) out.push({ label: 'Name', value: name })
  } else if (s === 'GSTIN_VERIFY') {
    const rows: Array<[string, string]> = [
      ['Trade name', read('tradeName', 'tradeNam')],
      ['Legal name', read('legalName', 'lgnm')],
      ['Status', read('status', 'sts')],
      ['Registration date', read('registrationDate', 'rgdt')],
    ]
    rows.forEach(([label, value]) => value && out.push({ label, value }))
  } else if (s === 'UDYAM_VERIFY') {
    const rows: Array<[string, string]> = [
      ['Enterprise name', read('enterpriseName', 'name', 'legalName')],
      ['Enterprise type', read('enterpriseType', 'businessType')],
      ['Major activity', read('majorActivity', 'activity')],
      ['Registration no', read('udyamRegistrationNo', 'udyam', 'registrationNumber')],
    ]
    rows.forEach(([label, value]) => value && out.push({ label, value }))
  } else if (s === 'DL_VERIFY') {
    const rows: Array<[string, string]> = [
      ['Name', read('name')],
      ['DL number', read('dlNo', 'dlNumber', 'licenseNumber')],
      ['DOB', read('dob')],
      ['Status', read('status')],
      ['Validity', read('validity', 'validUpto')],
      ['Address', read('address')],
    ]
    rows.forEach(([label, value]) => value && out.push({ label, value }))
  } else if (s === 'VOTER_ID_VERIFY') {
    const rows: Array<[string, string]> = [
      ['Name', read('name')],
      ['EPIC number', read('epicNo', 'voterId')],
      ['Age', read('age')],
      ['Gender', read('gender')],
      ['District', read('district')],
      ['State', read('state')],
    ]
    rows.forEach(([label, value]) => value && out.push({ label, value }))
  } else if (s === 'BANK_PENNY_DROP') {
    const rows: Array<[string, string]> = [
      ['Account holder', read('accountHolderName', 'accountName')],
      [
        'Bank status',
        read('bankTxnStatus') === 'true' || read('bankTxnStatus') === 'TRUE'
          ? 'Success'
          : read('bankTxnStatus') === 'false' || read('bankTxnStatus') === 'FALSE'
            ? 'Failed'
            : read('accountStatus', 'status'),
      ],
      ['Bank response', read('bankResponse')],
      ['Match result', read('nameMatch', 'matchResult')],
    ]
    rows.forEach(([label, value]) => value && out.push({ label, value }))
  } else if (s === 'MOBILE_OTP') {
    if (read('mobileVerified') === 'true' || data.mobileVerified === true) {
      out.push({ label: 'Status', value: 'Verified' })
    }
    const masked = read('mobileNumberMasked')
    if (masked) out.push({ label: 'Mobile', value: masked })
    if (errorMessage) {
      if (/required for verification/i.test(errorMessage) || /invalid mobile/i.test(errorMessage)) {
        out.push({ label: 'Issue', value: 'Mobile verification could not be completed' })
      } else {
        out.push({ label: 'Issue', value: errorMessage })
      }
    }
  } else if (s === 'ITR_RETURN_FORMS') {
    const metrics =
      data.mappedMetrics && typeof data.mappedMetrics === 'object'
        ? (data.mappedMetrics as Record<string, unknown>)
        : {}
    const result =
      data.result && typeof data.result === 'object' ? (data.result as Record<string, unknown>) : {}
    const gen =
      result.generalInformation && typeof result.generalInformation === 'object'
        ? (result.generalInformation as Record<string, unknown>)
        : {}
    const readM = (...keys: string[]) => {
      for (const key of keys) {
        const value = metrics[key] ?? data[key] ?? gen[key]
        if (value != null && String(value).trim() !== '') return String(value)
      }
      return ''
    }
    const rows: Array<[string, string]> = [
      ['Entity', readM('entityName')],
      ['PAN', readM('panNumber', 'entityPan')],
      ['Assessment year', readM('assessmentYear')],
      ['Total revenue (ITR income)', readM('itrIncome', 'grossTotalIncome')],
      ['PAT', readM('pat')],
      ['EBITDA', readM('ebitda')],
      ['Username used', read('username')],
      ['Request id', read('requestId')],
    ]
    rows.forEach(([label, value]) => value && out.push({ label, value }))
  } else if (s === 'GST_ANALYSIS') {
    const metrics =
      data.mappedMetrics && typeof data.mappedMetrics === 'object'
        ? (data.mappedMetrics as Record<string, unknown>)
        : {}
    const readM = (...keys: string[]) => {
      for (const key of keys) {
        const value = metrics[key] ?? data[key]
        if (value != null && String(value).trim() !== '') return String(value)
      }
      return ''
    }
    const rows: Array<[string, string]> = [
      ['Phase', read('phase')],
      ['GSTIN', readM('gstin')],
      ['Legal name', readM('legalName')],
      ['Trade name', readM('tradeName')],
      ['Annual GST turnover', readM('annualGstTurnover')],
      ['GST income', readM('gstIncome')],
      ['Avg GMV (3m)', readM('avgGmv3m')],
      ['Active 90 days', readM('active90days')],
      ['Request id', read('requestId', 'uploadRequestId')],
      ['Documents stored', Array.isArray(data.storedDocuments) ? String(data.storedDocuments.length) : ''],
    ]
    rows.forEach(([label, value]) => value && out.push({ label, value }))
  } else if (s === 'MNRL') {
    const active = read('active').toLowerCase()
    const activeLabel = active === 'y' ? 'Yes' : active === 'n' ? 'No' : read('active')
    if (activeLabel) out.push({ label: 'Active', value: activeLabel })
    const historyRaw = data.mnrlHistory
    if (Array.isArray(historyRaw)) {
      const historyLabel = historyRaw
        .map((item) => {
          const row = item as Record<string, unknown>
          const start = row.startDate == null ? '' : String(row.startDate)
          const end = row.endDate == null ? '' : String(row.endDate)
          return start || end ? `${start || 'NA'} -> ${end || 'NA'}` : ''
        })
        .filter(Boolean)
        .join(', ')
      if (historyLabel) out.push({ label: 'History', value: historyLabel })
    }
  }
  if (out.length === 0 && data) {
    Object.entries(data)
      .filter(([, value]) => value != null && String(value).trim() !== '')
      .slice(0, 4)
      .forEach(([key, value]) => out.push({ label: key, value: String(value) }))
  }
  if (out.length === 0 && errorMessage) out.push({ label: 'Issue', value: errorMessage })
  return out
}

/**
 * KYC is complete only after an explicit PASS outcome, or once the application has genuinely moved
 * past the review/KYC stages. Queue statuses like BORROWER_SUBMITTED and PENDING_CREDIT_OFFICER
 * must not show a false "success" banner before any verification has actually run.
 */
function isKycChecksComplete(app: ApplicationResponse, kycOutcome: Record<string, unknown> | null): boolean {
  const st = app.status
  if (kycOutcome) {
    const o = String((kycOutcome as { outcome?: unknown }).outcome ?? '').toUpperCase()
    if (o === 'PASS') return true
  }
  return (
    st === 'UNDERWRITING' ||
    st === 'UNDERWRITING_COMPLETED' ||
    st === 'CAM_READY' ||
    st === 'CAM_REVIEWED' ||
    st === 'SANCTION_PENDING' ||
    st === 'APPROVED' ||
    st === 'SANCTIONED' ||
    st === 'KFS_GENERATED' ||
    st === 'SANCTION_ISSUED' ||
    st === 'ESIGN_PENDING' ||
    st === 'ESIGN_COMPLETED' ||
    st === 'READY_FOR_DISBURSEMENT' ||
    st === 'DISBURSEMENT_PENDING' ||
    st === 'DISBURSED'
  )
}

export function KycDetailsSection({
  applicationId,
  app,
  onApplicationRefetch,
  onStepsRefetch,
  className = 'mb-8',
  allowRunKyc = true,
}: {
  applicationId: string
  app: ApplicationResponse
  onApplicationRefetch: () => void
  onStepsRefetch: () => void
  /** Panel spacing when embedded (e.g. in tabs). */
  className?: string
  /** When false (e.g. Relationship Manager), hide Run KYC but still allow save. */
  allowRunKyc?: boolean
}) {
  const [panNumber, setPanNumber] = useState('')
  const [name, setName] = useState('')
  const [aadhaarNumber, setAadhaarNumber] = useState('')
  const [mobile, setMobile] = useState('')
  const [gstin, setGstin] = useState('')
  const [udyamRegistrationNo, setUdyamRegistrationNo] = useState('')
  const [businessName, setBusinessName] = useState('')
  const [dlNo, setDlNo] = useState('')
  const [dlDob, setDlDob] = useState('')
  const [epicNo, setEpicNo] = useState('')
  const [accountNumber, setAccountNumber] = useState('')
  const [ifsc, setIfsc] = useState('')
  const [bankName, setBankName] = useState('')
  const [cin, setCin] = useState('')
  const [extraStepValues, setExtraStepValues] = useState<Record<string, string>>({})
  const [activeWorkflow, setActiveWorkflow] = useState<WorkflowConfigResponse | null>(null)
  const [formDirty, setFormDirty] = useState(false)
  const [lastHydratedFromApp, setLastHydratedFromApp] = useState('')

  const [kycResults, setKycResults] = useState<KycStepResultResponse[] | null>(null)
  const [kycOutcome, setKycOutcome] = useState<Record<string, unknown> | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [showTechnical, setShowTechnical] = useState(false)

  const [submitting, setSubmitting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [running, setRunning] = useState(false)
  const [retrying, setRetrying] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [lastRunSummary, setLastRunSummary] = useState<Record<string, unknown> | null>(null)
  const [fullResponseModal, setFullResponseModal] = useState<{
    title: string
    body: Record<string, unknown> | null
  } | null>(null)

  const configuredKycStepNames = useMemo(() => {
    const steps = activeWorkflow?.steps ?? []
    const names = steps.map((s) => toStepName(s as Record<string, unknown>)).filter(Boolean)
    return names
  }, [activeWorkflow])
  const showDynamicFromWorkflow = configuredKycStepNames.length > 0
  const isAnchorApp = isAnchorApplication(app.intakeSegment)
  const shouldShow = useCallback(
    (stepName: string, fallback: boolean) =>
      showDynamicFromWorkflow ? configuredKycStepNames.includes(stepName) : fallback,
    [configuredKycStepNames, showDynamicFromWorkflow],
  )
  const showPan = shouldShow('PAN_VERIFY', true)
  const showAadhaar = shouldShow('AADHAAR_OTP', !isAnchorApp)
  const showBank = shouldShow('BANK_PENNY_DROP', !isAnchorApp)
  const showGstin = shouldShow('GSTIN_VERIFY', app.borrowerType !== 'INDIVIDUAL')
  const showUdyam = shouldShow('UDYAM_VERIFY', isBusinessBorrowerType((app.borrowerType as BorrowerType) ?? 'INDIVIDUAL'))
  const showDl = shouldShow('DL_VERIFY', false)
  const showVoter = shouldShow('VOTER_ID_VERIFY', false)
  const showMnrl = shouldShow('MNRL', false)
  const showMobile = shouldShow('MOBILE_OTP', false) || showAadhaar || showMnrl
  // When workflow KYC steps are configured, field visibility must follow those steps only
  // (including Anchor apps — do not force Bank / PAN / GSTIN when those steps are absent).
  const showFullName = showPan || showAadhaar || showDl || showVoter || showBank || (isAnchorApp && !showDynamicFromWorkflow)
  const showPanField = showPan || (isAnchorApp && !showDynamicFromWorkflow)
  const showBankFields = showBank
  const showGstinFields = showGstin || (isAnchorApp && !showDynamicFromWorkflow)
  const extraConfiguredSteps = useMemo(() => {
    const names = configuredKycStepNames.filter(
      (name) => !KNOWN_KYC_INPUT_STEPS.has(name) && !NO_STAFF_KYC_INPUT_STEPS.has(name),
    )
    return filterAnchorKycUiStepNames(isAnchorApp, names)
  }, [configuredKycStepNames, isAnchorApp])

  const visibleKycResults = useMemo(
    () => (kycResults ? filterAnchorKycUiSteps(isAnchorApp, kycResults) : null),
    [isAnchorApp, kycResults],
  )

  const isDraft = app.status === 'DRAFT'
  const isPreKyc = app.status === 'DRAFT' || app.status === 'CONSENT_PENDING'
  const isFailed = app.status === 'KYC_FAILED'
  const isKycInProgress = app.status === 'KYC_IN_PROGRESS'

  const kycChecksComplete = useMemo(
    () => isKycChecksComplete(app, kycOutcome),
    [app, kycOutcome],
  )

  const anyGroupKycHint = useMemo(() => workflowAnyGroupKycHint(activeWorkflow), [activeWorkflow])

  const kycActionBusy = running || retrying || submitting
  const fieldsLocked = kycChecksComplete || kycActionBusy
  const appHydrationKey = `${app.id}:${app.updatedAt ?? ''}`

  const kycFormData = useMemo(
    () => ({
      PAN: { panNumber, name },
      AADHAAR: { aadhaarNumber, mobile },
      BANK: { accountNumber, ifsc, bankName },
      DL: { dlNo, dob: dlDob },
      VOTER: { epicNo },
      GST: { gstin, businessName },
      UDYAM: { udyamRegistrationNo },
    }),
    [
      panNumber,
      name,
      aadhaarNumber,
      mobile,
      accountNumber,
      ifsc,
      bankName,
      dlNo,
      dlDob,
      epicNo,
      gstin,
      businessName,
      udyamRegistrationNo,
    ],
  )

  useEffect(() => {
    void (async () => {
      if (appHydrationKey === lastHydratedFromApp) return
      if (formDirty) return
      await Promise.resolve()
      const pi = app.personalInfo as Record<string, unknown> | null | undefined
      const bi = app.businessInfo as Record<string, unknown> | null | undefined
      const fi = app.financialInfo as Record<string, unknown> | null | undefined
      if (isAnchorApplication(app.intakeSegment)) {
        const anchor = hydrateAnchorKycFields(pi, bi, fi)
        setPanNumber(anchor.panNumber)
        setName(anchor.name)
        setMobile(anchor.mobile)
        setGstin(anchor.gstin)
        setBusinessName(anchor.businessName)
        setAccountNumber(anchor.accountNumber)
        setIfsc(anchor.ifsc)
        setBankName(anchor.bankName)
        setCin(anchor.cin)
      } else {
        setPanNumber(pickStr(pi, 'panNumber'))
        setName(pickStr(pi, 'fullName') || pickStr(pi, 'name'))
        setMobile(pickStr(pi, 'mobile') || pickStr(pi, 'phone'))
        setGstin(pickStr(bi, 'gstin'))
        setBusinessName(pickStr(bi, 'businessName'))
        setAccountNumber(pickStr(pi, 'bankAccountNumber') || pickStr(fi, 'accountNumber'))
        setIfsc(pickStr(pi, 'ifsc') || pickStr(fi, 'ifsc'))
        setBankName(pickStr(pi, 'bankName'))
        setCin(pickStr(bi, 'cin'))
      }
      const a12 = pickStr(pi, 'aadhaarNumber')
      if (a12) {
        setAadhaarNumber(a12)
      } else {
        const last4 = pickStr(pi, 'aadhaarLast4')
        setAadhaarNumber(last4 ? `••••••${last4}` : '')
      }
      setUdyamRegistrationNo(pickStr(bi, 'udyam') || pickStr(bi, 'udyamRegistrationNo'))
      setDlNo(pickStr(pi, 'dlNo') || pickStr(pi, 'drivingLicenseNumber') || pickStr(pi, 'dlNumber'))
      setDlDob(pickStr(pi, 'dob') || pickStr(pi, 'dateOfBirth') || pickStr(pi, 'drivingLicenseDob'))
      setEpicNo(pickStr(pi, 'epicNo') || pickStr(pi, 'voterId'))
      const seedExtra: Record<string, string> = {}
      extraConfiguredSteps.forEach((step) => {
        const fromPi = pickStr(pi, step)
        const fromBi = pickStr(bi, step)
        const fromFi = pickStr(fi, step)
        seedExtra[step] = fromPi || fromBi || fromFi
      })
      setExtraStepValues(seedExtra)
      setLastHydratedFromApp(appHydrationKey)
    })()
  }, [app, extraConfiguredSteps, appHydrationKey, lastHydratedFromApp, formDirty])

  useEffect(() => {
    void (async () => {
      const boundId = (app.workflowId ?? '').trim()
      if (!boundId) {
        setActiveWorkflow(null)
        return
      }
      try {
        setActiveWorkflow(await getWorkflow(boundId))
      } catch {
        setActiveWorkflow(null)
      }
    })()
  }, [app.workflowId])

  useEffect(() => {
    setFormDirty(false)
    setLastHydratedFromApp('')
  }, [applicationId])

  const loadKycMeta = useCallback(async () => {
    setLoadError(null)
    try {
      const [results, outcome] = await Promise.all([
        getKycResults(applicationId),
        getKycOutcome(applicationId),
      ])
      setKycResults(results)
      setKycOutcome(outcome)
    } catch (e) {
      setKycResults(null)
      setKycOutcome(null)
      setLoadError(e instanceof Error ? e.message : 'Could not load verification data.')
    }
  }, [applicationId])

  useEffect(() => {
    void (async () => {
      await loadKycMeta()
    })()
  }, [loadKycMeta, app.status])

  async function onSaveInputs() {
    if (kycChecksComplete || kycActionBusy) return
    setActionError(null)
    setSaving(true)
    try {
      if (isAnchorApp) {
        const pi = app.personalInfo as Record<string, unknown> | null | undefined
        const bi = app.businessInfo as Record<string, unknown> | null | undefined
        const fi = app.financialInfo as Record<string, unknown> | null | undefined
        const payload = buildAnchorKycSavePayload(pi, bi, fi, {
          panNumber,
          name,
          mobile,
          gstin,
          businessName,
          accountNumber,
          ifsc,
          bankName,
          cin,
        })
        await updateApplication(applicationId, payload)
        setFormDirty(false)
        onApplicationRefetch()
        return
      }
      const aadhaarClean = aadhaarNumber.replace(/\D/g, '')
      const personalInfo: Record<string, unknown> = {
        ...((app.personalInfo as Record<string, unknown> | null) ?? {}),
        panNumber: panNumber.trim() || undefined,
        name: name.trim() || undefined,
        fullName: name.trim() || undefined,
        aadhaarNumber: aadhaarClean.length === 12 ? aadhaarClean : undefined,
        aadhaarLast4: aadhaarClean.length === 4 ? aadhaarClean : undefined,
        mobile: mobile.trim() || undefined,
        phone: mobile.trim() || undefined,
        dlNo: dlNo.trim().toUpperCase() || undefined,
        dlNumber: dlNo.trim().toUpperCase() || undefined,
        drivingLicenseNumber: dlNo.trim().toUpperCase() || undefined,
        drivingLicenseDob: dlDob.trim() || undefined,
        epicNo: epicNo.trim().toUpperCase() || undefined,
        voterId: epicNo.trim().toUpperCase() || undefined,
        bankAccountNumber: accountNumber.replace(/\D/g, '').trim() || undefined,
        ifsc: ifsc.trim().toUpperCase() || undefined,
        bankName: bankName.trim() || undefined,
        ...Object.fromEntries(
          Object.entries(extraStepValues).map(([k, v]) => [k, v.trim() || undefined]),
        ),
      }
      if (personalInfo.aadhaarNumber) {
        delete personalInfo.aadhaarLast4
      }
      Object.keys(personalInfo).forEach((k) => {
        if (personalInfo[k] === undefined) delete personalInfo[k]
      })

      const businessInfo: Record<string, unknown> = {
        ...((app.businessInfo as Record<string, unknown> | null) ?? {}),
        gstin: gstin.trim() || undefined,
        udyam: udyamRegistrationNo.trim().toUpperCase() || undefined,
        udyamRegistrationNo: udyamRegistrationNo.trim().toUpperCase() || undefined,
        businessName: businessName.trim() || undefined,
      }
      Object.keys(businessInfo).forEach((k) => {
        if (businessInfo[k] === undefined) delete businessInfo[k]
      })

      const financialInfo: Record<string, unknown> = {
        ...((app.financialInfo as Record<string, unknown> | null) ?? {}),
        accountNumber: accountNumber.replace(/\D/g, '').trim() || undefined,
        ifsc: ifsc.trim().toUpperCase() || undefined,
      }
      Object.keys(financialInfo).forEach((k) => {
        if (financialInfo[k] === undefined) delete financialInfo[k]
      })

      await updateApplication(applicationId, { personalInfo, businessInfo, financialInfo })
      setFormDirty(false)
      onApplicationRefetch()
    } catch (e) {
      setActionError(messageForKycAction(e) || 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  function buildKycPayload(): Record<string, unknown> {
    const aad = aadhaarNumber.replace(/\D/g, '')
    const aadForRun = aad.length === 12 ? aad : aad.length === 4 ? aad : undefined
    return nonEmptyPayload({
      panNumber: kycFormData.PAN.panNumber,
      name: kycFormData.PAN.name,
      aadhaarNumber: aadForRun && aadForRun.length === 12 ? aadForRun : '',
      aadhaarLast4: aadForRun && aadForRun.length === 4 ? aadForRun : '',
      mobile: kycFormData.AADHAAR.mobile,
      gstin: kycFormData.GST.gstin,
      udyamRegistrationNo: kycFormData.UDYAM.udyamRegistrationNo,
      businessName: kycFormData.GST.businessName,
      dlNo: kycFormData.DL.dlNo,
      dlNumber: kycFormData.DL.dlNo,
      drivingLicenseNumber: kycFormData.DL.dlNo,
      dob: kycFormData.DL.dob,
      dateOfBirth: kycFormData.DL.dob,
      drivingLicenseDob: kycFormData.DL.dob,
      epicNo: kycFormData.VOTER.epicNo,
      voterId: kycFormData.VOTER.epicNo,
      accountNumber: kycFormData.BANK.accountNumber.replace(/\D/g, ''),
      ifsc: kycFormData.BANK.ifsc,
      ...(isAnchorApp ? {} : { bankName: kycFormData.BANK.bankName }),
      ...Object.fromEntries(extraConfiguredSteps.map((step) => [step, extraStepValues[step] ?? ''])),
    })
  }

  async function onSubmitForKyc() {
    if (kycActionBusy) return
    setActionError(null)
    setSubmitting(true)
    try {
      await submitApplicationForKyc(applicationId)
      setLastRunSummary(null)
      onApplicationRefetch()
      onStepsRefetch()
      void loadKycMeta()
    } catch (e) {
      setActionError(messageForKycAction(e) || 'Submit failed')
    } finally {
      setSubmitting(false)
    }
  }

  async function onRetryKyc() {
    if (kycChecksComplete || kycActionBusy) return
    setActionError(null)
    setLastRunSummary(null)
    setRetrying(true)
    try {
      await retryKycFlow(applicationId)
      onApplicationRefetch()
      onStepsRefetch()
      void loadKycMeta()
    } catch (e) {
      setActionError(messageForKycAction(e) || 'KYC retry failed')
    } finally {
      setRetrying(false)
    }
  }

  async function onRunKyc() {
    if (kycChecksComplete || kycActionBusy) return
    setActionError(null)
    setLastRunSummary(null)
    setRunning(true)
    try {
      const body = buildKycPayload()
      const out = await runKycFlow(applicationId, body)
      setLastRunSummary(out)
      const runIssue = messageFromKycRunOutput(out)
      if (runIssue) {
        setActionError(runIssue)
      }
      onApplicationRefetch()
      onStepsRefetch()
      void loadKycMeta()
    } catch (e) {
      setActionError(messageForKycAction(e) || 'KYC run failed')
    } finally {
      setRunning(false)
    }
  }

  const canRunKyc = isKycInProgress && !kycChecksComplete
  const canShowRunKyc = canRunKyc && !isFailed && allowRunKyc
  const rmEditableStatus =
    app.status === 'BORROWER_SUBMITTED' ||
    app.status === 'SENT_BACK_TO_RM' ||
    (isAnchorApp && (app.status === 'KYC_IN_PROGRESS' || app.status === 'KYC_FAILED'))
  const canSave =
    !kycChecksComplete && (isPreKyc || isKycInProgress || isFailed || rmEditableStatus) && !kycActionBusy
  const partyLabels = applicationPartyLabels(app.intakeSegment)

  return (
    <section
      className={`bt-card p-5 ${className}`.trim()}
    >
      <h2 className="mb-1 text-lg font-medium text-slate-900">KYC checks</h2>
      <p className="mb-3 text-sm text-slate-600">
        Enter the customer details used for identity verification, then run checks. Use <strong>Save to application</strong>{' '}
        to keep details on the application record, or <strong>Run KYC</strong> to send them for verification.         Values
        pre-filled from the applicant&apos;s journey are stored on the application record; the{' '}
        <strong>{partyLabels.profileTab}</strong> tab lists everything submitted at intake.
      </p>
      {loadError ? <p className="mb-2 text-sm text-amber-800">{loadError}</p> : null}
      {anyGroupKycHint ? (
        <p className="mb-3 rounded-md border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-900">{anyGroupKycHint}</p>
      ) : null}
      {actionError ? <ErrorState message={actionError} /> : null}
      {kycChecksComplete ? (
        <p className="mb-3 bt-section-card bt-section-card--success px-3 py-2 text-sm text-emerald-900">
          KYC checks completed successfully.
        </p>
      ) : null}
      {isFailed && !kycChecksComplete ? (
        <div className="mb-3 bt-alert bt-alert-warning">
          <p>
            Verification did not pass. You can update the details where needed, then use <strong>Retry KYC</strong> to try
            again. Earlier attempts stay in the history below.
          </p>
          <button
            type="button"
            onClick={() => void onRetryKyc()}
            disabled={kycActionBusy}
            className="mt-2 rounded-md bg-amber-800 px-3 py-1.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {retrying ? 'Preparing…' : 'Retry KYC'}
          </button>
          <ProcessOverrideCard
            applicationId={applicationId}
            processCode="KYC"
            failureCode="KYC_FAILED"
            title="Manual override for KYC failure"
            onSuccess={onApplicationRefetch}
          />
        </div>
      ) : null}

      {isDraft ? (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <p className="text-sm text-slate-600">This application is in draft. Submit to start KYC, then you can run checks.</p>
          <button
            type="button"
            onClick={() => void onSubmitForKyc()}
            disabled={submitting || running || retrying}
            className="rounded-md bg-slate-800 px-3 py-1.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? 'Submitting…' : 'Submit for KYC'}
          </button>
        </div>
      ) : null}

      <fieldset disabled={fieldsLocked} className="min-w-0">
        <div className="grid gap-4 sm:grid-cols-2">
          {showFullName ? (
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">
                {isAnchorApp ? 'Account holder / signatory name' : 'Full name'}
              </span>
              <input
                className="bt-input w-full disabled:cursor-not-allowed disabled:bg-slate-50"
                value={name}
                onChange={(e) => {
                  setFormDirty(true)
                  setName(e.target.value)
                }}
                autoComplete="name"
              />
            </label>
          ) : null}
          {showPanField ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">
                {isAnchorApp ? 'Entity PAN' : 'PAN'}
              </span>
              <input
                className="bt-input w-full uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
                value={panNumber}
                onChange={(e) => {
                  setFormDirty(true)
                  setPanNumber(e.target.value.toUpperCase())
                }}
                maxLength={10}
              />
            </label>
          ) : null}
          {showAadhaar ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Aadhaar (12 digits or last 4)</span>
              <input
                className="bt-input w-full tabular-nums disabled:cursor-not-allowed disabled:bg-slate-50"
                value={aadhaarNumber}
                onChange={(e) => {
                  setFormDirty(true)
                  const v = e.target.value
                  if (v.startsWith('•')) {
                    setAadhaarNumber(v)
                    return
                  }
                  setAadhaarNumber(v.replace(/\D/g, '').slice(0, 12))
                }}
                inputMode="numeric"
              />
              <span className="mt-1 block text-xs text-slate-500">
                If the applicant only provided the last 4, enter the full 12 digits here when you need a full e-KYC run.
              </span>
            </label>
          ) : null}
          {showMobile ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Mobile</span>
              <input
                className="bt-input w-full tabular-nums disabled:cursor-not-allowed disabled:bg-slate-50"
                value={mobile}
                onChange={(e) => {
                  setFormDirty(true)
                  setMobile(e.target.value.replace(/\D/g, '').slice(0, 12))
                }}
                inputMode="tel"
              />
            </label>
          ) : null}
          {showGstinFields ? (
            <>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">
                  {isAnchorApp ? 'Corporate / trade name' : 'Business / trade name'}
                </span>
                <input
                  className="bt-input w-full disabled:cursor-not-allowed disabled:bg-slate-50"
                  value={businessName}
                  onChange={(e) => {
                    setFormDirty(true)
                    setBusinessName(e.target.value)
                  }}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">GSTIN</span>
                <input
                  className="bt-input w-full uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
                  value={gstin}
                  onChange={(e) => {
                    setFormDirty(true)
                    setGstin(e.target.value.toUpperCase().slice(0, 15))
                  }}
                />
              </label>
            </>
          ) : null}
          {showUdyam ? (
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Udyam registration number</span>
              <input
                className="bt-input w-full uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
                value={udyamRegistrationNo}
                onChange={(e) => {
                  setFormDirty(true)
                  setUdyamRegistrationNo(e.target.value.toUpperCase())
                }}
              />
            </label>
          ) : null}
          {showDl ? (
            <>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Driving license number</span>
                <input
                  className="bt-input w-full uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
                  value={dlNo}
                  onChange={(e) => {
                    setFormDirty(true)
                    setDlNo(e.target.value.toUpperCase())
                  }}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">DOB (DD-MM-YYYY)</span>
                <input
                  className="bt-input w-full disabled:cursor-not-allowed disabled:bg-slate-50"
                  value={dlDob}
                  onChange={(e) => {
                    setFormDirty(true)
                    setDlDob(e.target.value)
                  }}
                  placeholder="12-02-1983"
                />
              </label>
            </>
          ) : null}
          {showVoter ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Voter EPIC number</span>
              <input
                className="bt-input w-full uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
                value={epicNo}
                onChange={(e) => {
                  setFormDirty(true)
                  setEpicNo(e.target.value.toUpperCase())
                }}
              />
            </label>
          ) : null}
          {showBankFields ? (
            <>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Bank account</span>
                <input
                  className="bt-input w-full tabular-nums disabled:cursor-not-allowed disabled:bg-slate-50"
                  value={accountNumber}
                  onChange={(e) => {
                    setFormDirty(true)
                    setAccountNumber(e.target.value.replace(/\D/g, ''))
                  }}
                  inputMode="numeric"
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">IFSC</span>
                <input
                  className="bt-input w-full uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
                  value={ifsc}
                  onChange={(e) => {
                    setFormDirty(true)
                    setIfsc(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 11))
                  }}
                />
              </label>
              {!isAnchorApp ? (
                <label className="block text-sm text-slate-700 sm:col-span-2">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Bank name</span>
                  <input
                    className="bt-input w-full disabled:cursor-not-allowed disabled:bg-slate-50"
                    value={bankName}
                    onChange={(e) => {
                      setFormDirty(true)
                      setBankName(e.target.value)
                    }}
                  />
                </label>
              ) : null}
            </>
          ) : null}
          {extraConfiguredSteps.map((step) => (
            <label key={step} className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">{step.split('_').join(' ')}</span>
              <input
                className="bt-input w-full disabled:cursor-not-allowed disabled:bg-slate-50"
                value={extraStepValues[step] ?? ''}
                onChange={(e) =>
                  {
                    setFormDirty(true)
                    setExtraStepValues((prev) => ({
                      ...prev,
                      [step]: e.target.value,
                    }))
                  }
                }
              />
            </label>
          ))}
        </div>
      </fieldset>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => void onSaveInputs()}
          disabled={!canSave}
          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save to application'}
        </button>
        {canShowRunKyc ? (
          <button
            type="button"
            onClick={() => void onRunKyc()}
            disabled={!canRunKyc || running || kycActionBusy}
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {running ? 'Running KYC checks…' : 'Run KYC'}
          </button>
        ) : null}
        {!kycChecksComplete && !isDraft && !isFailed && !isKycInProgress && !running ? (
          <span className="text-xs text-slate-500">This stage is not open for new KYC runs. Current status: {app.status}.</span>
        ) : null}
      </div>

      {lastRunSummary || kycOutcome ? (
        <div className="mt-4">
          <button
            type="button"
            onClick={() => setShowTechnical((s) => !s)}
            className="text-sm font-medium text-slate-700 underline"
          >
            {showTechnical ? 'Hide' : 'Show'} technical details
          </button>
          {showTechnical ? (
            <div className="mt-2 space-y-2 bt-section-card bt-section-card--default p-3 text-sm bg-slate-50">
              {lastRunSummary ? (
                <div>
                  <div className="text-xs font-medium text-slate-500">Last run (raw)</div>
                  <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap break-all text-xs text-slate-700">
                    {JSON.stringify(lastRunSummary, null, 2)}
                  </pre>
                </div>
              ) : null}
              {kycOutcome ? (
                <div>
                  <div className="text-xs font-medium text-slate-500">Outcome (raw)</div>
                  <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap break-all text-xs text-slate-700">
                    {JSON.stringify(kycOutcome, null, 2)}
                  </pre>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}

      {visibleKycResults && visibleKycResults.length > 0 ? (
        <div className="mt-4">
          <h3 className="bt-card-title">Verification history (checks)</h3>
          <div className="mt-2 overflow-x-auto">
            <table className="min-w-full text-left text-xs">
              <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-2 py-1.5">Check</th>
                  <th className="px-2 py-1.5">Result</th>
                  <th className="px-2 py-1.5">Provider</th>
                  <th className="px-2 py-1.5">Summary</th>
                  <th className="px-2 py-1.5">Issue</th>
                  <th className="px-2 py-1.5">Raw response</th>
                </tr>
              </thead>
              <tbody className="">
                {visibleKycResults.map((r) => {
                  const pairs = summaryPairs(r.stepType, r.parsedData, r.errorMessage)
                  return (
                    <tr key={r.id}>
                      <td className="px-2 py-1.5 font-medium text-slate-800">{businessKycStepLabel(r.stepType)}</td>
                      <td className="px-2 py-1.5">{businessKycOutcomeLabel(r.outcome)}</td>
                      <td className="px-2 py-1.5">{r.provider}</td>
                      <td className="px-2 py-1.5 text-slate-700">
                        {pairs.length > 0 ? (
                          <div className="space-y-0.5">
                            {pairs.map((pair) => (
                              <div key={`${r.id}-${pair.label}`}>
                                <span className="text-slate-500">{pair.label}: </span>
                                <span>{pair.value}</span>
                              </div>
                            ))}
                          </div>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="px-2 py-1.5 text-slate-600">{r.errorMessage ?? '—'}</td>
                      <td className="px-2 py-1.5 align-top">
                        <button
                          type="button"
                          onClick={() =>
                            setFullResponseModal({
                              title: 'KYC Full Response',
                              body: (r.parsedData ?? { errorMessage: r.errorMessage }) as Record<string, unknown>,
                            })
                          }
                          className="text-xs font-medium text-slate-700 underline"
                        >
                          View full response
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : visibleKycResults && visibleKycResults.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">No verification results yet.</p>
      ) : null}
      {fullResponseModal ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="kyc-full-response-title"
        >
          <div className="w-full max-w-3xl rounded-lg border border-slate-200 bg-white p-5 shadow-lg">
            <div className="flex items-center justify-between gap-2">
              <h3 id="kyc-full-response-title" className="text-base font-semibold text-slate-900">
                {fullResponseModal.title}
              </h3>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700"
                onClick={() => setFullResponseModal(null)}
              >
                Close
              </button>
            </div>
            <div className="mt-3 max-h-[60vh] overflow-auto rounded border border-slate-200 bg-slate-50 p-3">
              <pre className="whitespace-pre-wrap break-all text-xs text-slate-700">
                {JSON.stringify(fullResponseModal.body ?? {}, null, 2)}
              </pre>
            </div>
            <div className="mt-3 flex justify-end gap-2">
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
                onClick={() => {
                  void navigator.clipboard.writeText(JSON.stringify(fullResponseModal.body ?? {}, null, 2))
                }}
              >
                Copy JSON
              </button>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
                onClick={() => setFullResponseModal(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  )
}
