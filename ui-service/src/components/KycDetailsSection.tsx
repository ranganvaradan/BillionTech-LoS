import { useCallback, useEffect, useMemo, useState } from 'react'
import { getKycOutcome, getKycResults } from '@/api/kyc'
import { retryKycFlow, runKycFlow, submitApplicationForKyc } from '@/api/flow'
import { getActiveWorkflow } from '@/api/workflows'
import { messageForKycAction, messageFromKycRunOutput } from '@/api/kycErrorMessage'
import { updateApplication } from '@/api/applications'
import { ErrorState } from '@/components/ErrorState'
import type { ApplicationResponse } from '@/types/application'
import type { KycStepResultResponse } from '@/types/kyc'
import type { WorkflowConfigResponse } from '@/types/workflow'
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

/** KYC flow finished successfully (outcome API) or application has moved past the KYC stage. */
function isKycChecksComplete(app: ApplicationResponse, kycOutcome: Record<string, unknown> | null): boolean {
  const st = app.status
  if (st !== 'DRAFT' && st !== 'CONSENT_PENDING' && st !== 'KYC_IN_PROGRESS' && st !== 'KYC_FAILED') {
    return true
  }
  if (st === 'KYC_IN_PROGRESS' && kycOutcome) {
    const o = String((kycOutcome as { outcome?: unknown }).outcome ?? '').toUpperCase()
    return o === 'PASS'
  }
  return false
}

export function KycDetailsSection({
  applicationId,
  app,
  onApplicationRefetch,
  onStepsRefetch,
  className = 'mb-8',
}: {
  applicationId: string
  app: ApplicationResponse
  onApplicationRefetch: () => void
  onStepsRefetch: () => void
  /** Panel spacing when embedded (e.g. in tabs). */
  className?: string
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
  const shouldShow = useCallback(
    (stepName: string, fallback: boolean) =>
      showDynamicFromWorkflow ? configuredKycStepNames.includes(stepName) : fallback,
    [configuredKycStepNames, showDynamicFromWorkflow],
  )
  const showPan = shouldShow('PAN_VERIFY', true)
  const showAadhaar = shouldShow('AADHAAR_OTP', true)
  const showBank = shouldShow('BANK_PENNY_DROP', true)
  const showGstin = shouldShow('GSTIN_VERIFY', app.borrowerType !== 'INDIVIDUAL')
  const showUdyam = shouldShow('UDYAM_VERIFY', isBusinessBorrowerType((app.borrowerType as BorrowerType) ?? 'INDIVIDUAL'))
  const showDl = shouldShow('DL_VERIFY', false)
  const showVoter = shouldShow('VOTER_ID_VERIFY', false)
  const showMnrl = shouldShow('MNRL', false)
  const showMobile = shouldShow('MOBILE_OTP', true) || showAadhaar || showMnrl
  const extraConfiguredSteps = useMemo(
    () =>
      configuredKycStepNames.filter(
        (name) =>
          !KNOWN_KYC_INPUT_STEPS.has(name) &&
          name !== 'FACE_MATCH' &&
          name !== 'LIVENESS' &&
          name !== 'VIDEO_KYC' &&
          name !== 'CKYC_DOWNLOAD' &&
          name !== 'CKYC_UPLOAD',
      ),
    [configuredKycStepNames],
  )

  const isDraft = app.status === 'DRAFT'
  const isPreKyc = app.status === 'DRAFT' || app.status === 'CONSENT_PENDING'
  const isFailed = app.status === 'KYC_FAILED'
  const isKycInProgress = app.status === 'KYC_IN_PROGRESS'

  const kycChecksComplete = useMemo(
    () => isKycChecksComplete(app, kycOutcome),
    [app, kycOutcome],
  )

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
      setPanNumber(pickStr(pi, 'panNumber'))
      setName(pickStr(pi, 'fullName') || pickStr(pi, 'name'))
      const a12 = pickStr(pi, 'aadhaarNumber')
      if (a12) {
        setAadhaarNumber(a12)
      } else {
        const last4 = pickStr(pi, 'aadhaarLast4')
        setAadhaarNumber(last4 ? `••••••${last4}` : '')
      }
      setMobile(pickStr(pi, 'mobile') || pickStr(pi, 'phone'))
      setGstin(pickStr(bi, 'gstin'))
      setUdyamRegistrationNo(pickStr(bi, 'udyam') || pickStr(bi, 'udyamRegistrationNo'))
      setBusinessName(pickStr(bi, 'businessName'))
      setDlNo(pickStr(pi, 'dlNo') || pickStr(pi, 'drivingLicenseNumber'))
      setDlDob(pickStr(pi, 'dob') || pickStr(pi, 'drivingLicenseDob'))
      setEpicNo(pickStr(pi, 'epicNo') || pickStr(pi, 'voterId'))
      setAccountNumber(pickStr(pi, 'bankAccountNumber') || pickStr(fi, 'accountNumber'))
      setIfsc(pickStr(pi, 'ifsc') || pickStr(fi, 'ifsc'))
      setBankName(pickStr(pi, 'bankName'))
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
      try {
        const workflow = await getActiveWorkflow(app.borrowerType, app.loanProduct)
        setActiveWorkflow(workflow)
      } catch {
        setActiveWorkflow(null)
      }
    })()
  }, [app.borrowerType, app.loanProduct])

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
      dob: kycFormData.DL.dob,
      epicNo: kycFormData.VOTER.epicNo,
      accountNumber: kycFormData.BANK.accountNumber.replace(/\D/g, ''),
      ifsc: kycFormData.BANK.ifsc,
      bankName: kycFormData.BANK.bankName,
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
  const canShowRunKyc = canRunKyc && !isFailed
  const canSave =
    !kycChecksComplete && (isPreKyc || isKycInProgress || isFailed) && !kycActionBusy

  return (
    <section
      className={`rounded-lg border border-slate-200 bg-white p-5 shadow-sm ${className}`.trim()}
    >
      <h2 className="mb-1 text-lg font-medium text-slate-900">KYC checks</h2>
      <p className="mb-3 text-sm text-slate-600">
        Enter the customer details used for identity verification, then run checks. Use <strong>Save to application</strong>{' '}
        to keep details on the application record, or <strong>Run KYC</strong> to send them for verification.         Values
        pre-filled from the applicant&apos;s journey are stored on the application record; the <strong>Borrower profile</strong>{' '}
        tab lists everything submitted at intake.
      </p>
      {loadError ? <p className="mb-2 text-sm text-amber-800">{loadError}</p> : null}
      {actionError ? <ErrorState message={actionError} /> : null}
      {kycChecksComplete ? (
        <p className="mb-3 rounded border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
          KYC checks completed successfully.
        </p>
      ) : null}
      {isFailed && !kycChecksComplete ? (
        <div className="mb-3 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
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
          {(showPan || showAadhaar || showDl || showVoter || showBank) ? (
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Full name</span>
              <input
                className="w-full rounded-md border border-slate-300 px-3 py-2 disabled:cursor-not-allowed disabled:bg-slate-50"
                value={name}
                onChange={(e) => {
                  setFormDirty(true)
                  setName(e.target.value)
                }}
                autoComplete="name"
              />
            </label>
          ) : null}
          {showPan ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">PAN</span>
              <input
                className="w-full rounded-md border border-slate-300 px-3 py-2 uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
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
                className="w-full rounded-md border border-slate-300 px-3 py-2 tabular-nums disabled:cursor-not-allowed disabled:bg-slate-50"
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
                className="w-full rounded-md border border-slate-300 px-3 py-2 tabular-nums disabled:cursor-not-allowed disabled:bg-slate-50"
                value={mobile}
                onChange={(e) => {
                  setFormDirty(true)
                  setMobile(e.target.value.replace(/\D/g, '').slice(0, 12))
                }}
                inputMode="tel"
              />
            </label>
          ) : null}
          {showGstin ? (
            <>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Business / trade name</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2 disabled:cursor-not-allowed disabled:bg-slate-50"
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
                  className="w-full rounded-md border border-slate-300 px-3 py-2 uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
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
                className="w-full rounded-md border border-slate-300 px-3 py-2 uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
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
                  className="w-full rounded-md border border-slate-300 px-3 py-2 uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
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
                  className="w-full rounded-md border border-slate-300 px-3 py-2 disabled:cursor-not-allowed disabled:bg-slate-50"
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
                className="w-full rounded-md border border-slate-300 px-3 py-2 uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
                value={epicNo}
                onChange={(e) => {
                  setFormDirty(true)
                  setEpicNo(e.target.value.toUpperCase())
                }}
              />
            </label>
          ) : null}
          {showBank ? (
            <>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Bank account</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2 tabular-nums disabled:cursor-not-allowed disabled:bg-slate-50"
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
                  className="w-full rounded-md border border-slate-300 px-3 py-2 uppercase disabled:cursor-not-allowed disabled:bg-slate-50"
                  value={ifsc}
                  onChange={(e) => {
                    setFormDirty(true)
                    setIfsc(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 11))
                  }}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Bank name</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2 disabled:cursor-not-allowed disabled:bg-slate-50"
                  value={bankName}
                  onChange={(e) => {
                    setFormDirty(true)
                    setBankName(e.target.value)
                  }}
                />
              </label>
            </>
          ) : null}
          {extraConfiguredSteps.map((step) => (
            <label key={step} className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">{step.split('_').join(' ')}</span>
              <input
                className="w-full rounded-md border border-slate-300 px-3 py-2 disabled:cursor-not-allowed disabled:bg-slate-50"
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
            <div className="mt-2 space-y-2 rounded border border-slate-200 bg-slate-50 p-3 text-sm">
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

      {kycResults && kycResults.length > 0 ? (
        <div className="mt-4">
          <h3 className="text-sm font-semibold text-slate-900">Verification history (checks)</h3>
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
              <tbody className="divide-y divide-slate-100">
                {kycResults.map((r) => {
                  const pairs = summaryPairs(r.stepType, r.parsedData, r.errorMessage)
                  return (
                    <tr key={r.id}>
                      <td className="px-2 py-1.5 font-mono">{r.stepType}</td>
                      <td className="px-2 py-1.5">{r.outcome}</td>
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
      ) : kycResults && kycResults.length === 0 ? (
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
