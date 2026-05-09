import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canAccessAdminConfigNav } from '@/auth/types'
import { createApplication, updateApplication } from '@/api/applications'
import { listDocuments, uploadDocument } from '@/api/documents'
import { listWorkflows } from '@/api/workflows'
import { submitApplicationForKyc } from '@/api/flow'
import { ApiError } from '@/api/http'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { consentHelper, pageDescription, pageTitle } from '@/lib/intake/intakeLabels'
import { CollateralIntakeFields } from '@/components/intake/CollateralIntakeFields'
import { persistBorrowerIntakeCollateral } from '@/lib/intake/collateralPersist'
import { buildConsentUpdate, buildIntakeBorrowerUpdate, buildIntakeCreateRequest, buildKycUpdate } from '@/lib/intake/intakePayloads'
import { allDocumentSlotsForIntake } from '@/lib/intake/intakeDocumentSlots'
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
  validateProductStep,
} from '@/lib/intake/intakeValidation'
import { BORROWER_TYPE_LABELS } from '@/catalog/borrowerTypes'
import { activeCatalogHasSecuredProduct, workflowLoanProductDisplayName } from '@/utils/workflowProducts'
import type { WorkflowConfigResponse } from '@/types/workflow'
import type { BorrowerType } from '@/types/createApplication'

function buildStaffStepLabels(needColl: boolean): readonly string[] {
  return needColl
    ? ['Product', 'Borrower', 'Collateral', 'KYC', 'Consent', 'Documents', 'Review']
    : ['Product', 'Borrower', 'KYC', 'Consent', 'Documents', 'Review']
}

function Stepper({ step, labels }: { step: number; labels: readonly string[] }) {
  return (
    <ol className="mb-8 flex flex-wrap items-center gap-2 border-b border-slate-200 pb-4 text-sm">
      {labels.map((label, i) => (
        <li key={label} className="flex items-center gap-2">
          <span
            className={[
              'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
              i < step
                ? 'bg-emerald-100 text-emerald-900'
                : i === step
                  ? 'bg-slate-900 text-white'
                  : 'bg-slate-100 text-slate-500',
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
}

export function ApplicationIntakeWizard({ mode, variant }: ApplicationIntakeWizardProps) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState<IntakeFormState>(createEmptyIntakeFormState)
  const [applicationId, setApplicationId] = useState<string | null>(null)
  const [activeWorkflows, setActiveWorkflows] = useState<WorkflowConfigResponse[]>([])
  const [workflowsState, setWorkflowsState] = useState<'loading' | 'ok' | 'err'>('loading')
  const [workflowsError, setWorkflowsError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [docWarning, setDocWarning] = useState<string | null>(null)
  const [collateralDocWarn, setCollateralDocWarn] = useState<string | null>(null)

  const needColl = useMemo(() => requiresCollateral(form.loanProduct), [form.loanProduct])
  const stepLabels = useMemo(() => buildStaffStepLabels(needColl), [needColl])
  const lastStep = needColl ? 6 : 5
  const docsStep = needColl ? 5 : 4
  const productsForType = productsForBorrowerType(activeWorkflows, form.borrowerType)
  const selectedWorkflow = productsForType.find((w) => w.loanProduct === form.loanProduct) ?? null
  const productLocked = Boolean(applicationId)

  const loadWorkflows = useCallback(async () => {
    setWorkflowsState('loading')
    setWorkflowsError(null)
    try {
      const all = await listWorkflows()
      const act = all.filter((w) => w.active)
      setActiveWorkflows(act)
      setWorkflowsState('ok')
      setForm((f) => {
        const list = productsForBorrowerType(act, f.borrowerType)
        if (list.length === 0) {
          return f.loanProduct ? { ...f, loanProduct: '' } : f
        }
        if (list.some((w) => w.loanProduct === f.loanProduct)) return f
        return { ...f, loanProduct: list[0]!.loanProduct }
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
    if (step === docsStep && applicationId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- async listDocuments; updates upload flags
      void syncDocumentsFromServer(applicationId)
    }
  }, [step, applicationId, syncDocumentsFromServer, docsStep])
  useEffect(() => {
    if (step === 2 && needColl && applicationId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void syncDocumentsFromServer(applicationId)
    }
  }, [step, needColl, applicationId, syncDocumentsFromServer])

  async function persistFromProductStep(): Promise<boolean> {
    if (!applicationId) return true
    setBusy(true)
    try {
      const req = buildIntakeBorrowerUpdate(form, mode, user)
      await updateApplication(applicationId, req)
      return true
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save product details.')
      return false
    } finally {
      setBusy(false)
    }
  }

  async function goNext() {
    setError(null)
    setCollateralDocWarn(null)
    if (step === 0) {
      const v = validateProductStep(form, mode, activeWorkflows)
      if (v) {
        setError(v)
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
        setStep(1)
        return
      }
      setForm((f) =>
        mode === 'SALES_ASSISTED' && f.borrowerType === 'INDIVIDUAL' && !f.mobile.trim() && f.borrowerMobile
          ? { ...f, mobile: f.borrowerMobile }
          : f,
      )
      setStep(1)
      return
    }
    if (step === 1) {
      const v = validateBorrowerStep(form, mode)
      if (v) {
        setError(v)
        return
      }
      setBusy(true)
      try {
        if (!applicationId) {
          const req = buildIntakeCreateRequest(form, mode, user)
          const res = await createApplication(req)
          setApplicationId(res.id)
        } else {
          await updateApplication(applicationId, buildIntakeBorrowerUpdate(form, mode, user))
        }
        setStep(2)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not save application details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === 2) {
      if (needColl) {
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
          setStep(3)
        } catch (err) {
          setError(err instanceof ApiError ? err.message : 'Could not save collateral details.')
        } finally {
          setBusy(false)
        }
        return
      }
      const v = validateKycStep(form)
      if (v) {
        setError(v)
        return
      }
      if (!applicationId) return
      setBusy(true)
      try {
        await updateApplication(applicationId, buildKycUpdate(form))
        setStep(3)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not save KYC details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === 3) {
      if (needColl) {
        const v = validateKycStep(form)
        if (v) {
          setError(v)
          return
        }
        if (!applicationId) return
        setBusy(true)
        try {
          await updateApplication(applicationId, buildKycUpdate(form))
          setStep(4)
        } catch (err) {
          setError(err instanceof ApiError ? err.message : 'Could not save KYC details.')
        } finally {
          setBusy(false)
        }
        return
      }
      const v3 = validateConsentStep(form)
      if (v3) {
        setError(v3)
        return
      }
      if (!applicationId) return
      setBusy(true)
      try {
        await updateApplication(applicationId, buildConsentUpdate(form, mode, user))
        setStep(4)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not save consents.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === 4) {
      if (needColl) {
        const v3 = validateConsentStep(form)
        if (v3) {
          setError(v3)
          return
        }
        if (!applicationId) return
        setBusy(true)
        try {
          await updateApplication(applicationId, buildConsentUpdate(form, mode, user))
          setStep(5)
        } catch (err) {
          setError(err instanceof ApiError ? err.message : 'Could not save consents.')
        } finally {
          setBusy(false)
        }
        return
      }
      const miss = missingIntakeDocumentTypes(form)
      if (miss.length) {
        setDocWarning(
          `For a complete package you may still add: ${miss.join(', ')}. You can continue to review, or go back to upload more.`,
        )
      } else {
        setDocWarning(null)
      }
      setStep(5)
      return
    }
    if (step === 5) {
      if (needColl) {
        const miss = missingIntakeDocumentTypes(form)
        if (miss.length) {
          setDocWarning(
            `For a complete package you may still add: ${miss.join(', ')}. You can continue to review, or go back to upload more.`,
          )
        } else {
          setDocWarning(null)
        }
        setStep(6)
      }
    }
  }

  function goBack() {
    setError(null)
    if (step > 0) {
      if (applicationId && step === 0) {
        // cannot go back before step0 from elsewhere
        return
      }
      setStep((s) => s - 1)
    }
  }

  async function onSubmitFinal() {
    if (!applicationId) return
    if (!allConsentsChecked(form)) {
      setError('All consents are required before submission.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await submitApplicationForKyc(applicationId)
      if (mode === 'BORROWER_SELF_SERVICE') {
        void navigate(`/borrower/applications/${applicationId}`, { replace: true })
      } else {
        void navigate(`/applications/${applicationId}`, { replace: true })
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit application for verification.')
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

  return (
    <div>
      {variant === 'staff' ? (
        <>
          <PageHeader title={pageTitle(mode)} description={pageDescription(mode)} />
          <p className="mb-4 text-sm text-slate-600">
            <Link to="/applications" className="font-medium text-slate-800 underline">
              ← Applications
            </Link>
          </p>
        </>
      ) : (
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-slate-900">{pageTitle(mode)}</h1>
          <p className="mt-1 text-sm text-slate-600">{pageDescription(mode)}</p>
        </div>
      )}

      {workflowsState === 'loading' ? <p className="mb-4 text-sm text-slate-600">Loading active workflows…</p> : null}
      {workflowsState === 'err' && workflowsError ? <ErrorState message={workflowsError} /> : null}
      {workflowsState === 'ok' && activeWorkflows.length === 0 ? (
        <p className="mb-4 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          There are no active workflows. Add and activate a workflow in Workflows before creating an application.
        </p>
      ) : null}
      {error ? <ErrorState message={error} /> : null}

      <Stepper step={step} labels={stepLabels} />

      {step === 0 ? (
        <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Product &amp; request</h2>
          {mode === 'SALES_ASSISTED' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Sales officer name *</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.salesOfficerName}
                  onChange={(e) => setForm((f) => ({ ...f, salesOfficerName: e.target.value }))}
                  disabled={!!applicationId}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Sales / branch ID</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.salesOfficerId}
                  onChange={(e) => setForm((f) => ({ ...f, salesOfficerId: e.target.value }))}
                  disabled={!!applicationId}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Borrower mobile *</span>
                <input
                  type="tel"
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
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
          {mode === 'ADMIN_INTERNAL' ? (
            <p className="text-xs text-slate-500">
              Internal: application created from this path records your user id in personal info where applicable. Ensure
              product matches an active workflow for the selected borrower class.
            </p>
          ) : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Borrower type *</span>
              <select
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-slate-900"
                value={form.borrowerType}
                onChange={(e) => {
                  const bt = e.target.value as BorrowerType
                  const list = productsForBorrowerType(activeWorkflows, bt)
                  setForm((f) => ({ ...f, borrowerType: bt, loanProduct: list[0]?.loanProduct ?? '' }))
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
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-slate-900 disabled:cursor-not-allowed disabled:bg-slate-50"
                value={productsForType.length === 0 ? '' : form.loanProduct}
                onChange={(e) => setForm((f) => ({ ...f, loanProduct: e.target.value }))}
                disabled={workflowsState !== 'ok' || !productsForType.length || productLocked}
              >
                {productsForType.length === 0 ? <option value="">(none for this type)</option> : null}
                {productsForType.map((w) => (
                  <option key={w.id} value={w.loanProduct}>
                    {workflowLoanProductDisplayName(w.loanProduct)}
                  </option>
                ))}
              </select>
              {selectedWorkflow ? (
                <p className="mt-1.5 text-xs text-slate-500">
                  Active workflow: <span className="font-medium text-slate-800">{selectedWorkflow.name}</span> (v
                  {selectedWorkflow.version})
                </p>
              ) : null}
              {variant === 'staff' &&
              productsForType.length > 0 &&
              !activeCatalogHasSecuredProduct(activeWorkflows, form.borrowerType) &&
              (canAccessAdminConfigNav(user?.role ?? '') || import.meta.env.DEV) ? (
                <p className="mt-2 rounded border border-amber-200 bg-amber-50 px-2 py-1.5 text-xs text-amber-950" role="note">
                  No secured products are active. Configure workflow for Loan Against Property, Loan Against Securities,
                  or Loan Against Gold.
                </p>
              ) : null}
            </div>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Requested amount (INR) *</span>
              <input
                type="number"
                min={0.01}
                step="0.01"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-slate-900 tabular-nums"
                value={form.requestedAmount}
                onChange={(e) => setForm((f) => ({ ...f, requestedAmount: e.target.value }))}
                required
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Tenure (months)</span>
              <input
                type="number"
                min={1}
                step={1}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-slate-900 tabular-nums"
                value={form.tenureMonths}
                onChange={(e) => setForm((f) => ({ ...f, tenureMonths: e.target.value }))}
                placeholder="Optional"
              />
            </label>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Purpose of loan</span>
              <textarea
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-slate-900"
                rows={2}
                value={form.purpose}
                onChange={(e) => setForm((f) => ({ ...f, purpose: e.target.value }))}
                placeholder="How you plan to use the funds (short note)"
              />
            </label>
          </div>
          {applicationId && productLocked ? (
            <p className="text-xs text-amber-800">
              Product and borrower class are fixed for this application so the workflow does not get out of sync. You
              can still change amount, tenure, and purpose.
            </p>
          ) : null}
        </section>
      ) : null}

      {step === 1 ? (
        <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Basic borrower details</h2>
          {form.borrowerType === 'INDIVIDUAL' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Full name (as per PAN) *</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.fullName}
                  onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
                  autoComplete="name"
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Mobile *</span>
                <input
                  type="tel"
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.mobile}
                  onChange={(e) => setForm((f) => ({ ...f, mobile: e.target.value }))}
                  autoComplete="tel"
                />
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
                <span className="mb-1 block text-xs font-medium text-slate-500">Email</span>
                <input
                  type="email"
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.email}
                  onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Date of birth</span>
                <input
                  type="date"
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.dateOfBirth}
                  onChange={(e) => setForm((f) => ({ ...f, dateOfBirth: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Address</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.addressLine}
                  onChange={(e) => setForm((f) => ({ ...f, addressLine: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">City</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.city}
                  onChange={(e) => setForm((f) => ({ ...f, city: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">State</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.state}
                  onChange={(e) => setForm((f) => ({ ...f, state: e.target.value }))}
                />
              </label>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Business / entity name *</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.businessName}
                  onChange={(e) => setForm((f) => ({ ...f, businessName: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Contact person *</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.contactPersonName}
                  onChange={(e) => setForm((f) => ({ ...f, contactPersonName: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Contact mobile *</span>
                <input
                  type="tel"
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.contactMobile}
                  onChange={(e) => setForm((f) => ({ ...f, contactMobile: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Contact email</span>
                <input
                  type="email"
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.contactEmail}
                  onChange={(e) => setForm((f) => ({ ...f, contactEmail: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">GSTIN *</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.gstin}
                  onChange={(e) => setForm((f) => ({ ...f, gstin: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Udyam (if applicable)</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.udyam}
                  onChange={(e) => setForm((f) => ({ ...f, udyam: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Business address</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.businessAddress}
                  onChange={(e) => setForm((f) => ({ ...f, businessAddress: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">City</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.businessCity}
                  onChange={(e) => setForm((f) => ({ ...f, businessCity: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">State</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2"
                  value={form.businessState}
                  onChange={(e) => setForm((f) => ({ ...f, businessState: e.target.value }))}
                />
              </label>
            </div>
          )}
        </section>
      ) : null}

      {step === 2 && needColl && detectSecuredCollateralKind(form.loanProduct) ? (
        <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Collateral</h2>
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

      {((step === 2 && !needColl) || (step === 3 && needColl)) ? (
        <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Identity &amp; KYC</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">PAN *</span>
              <input
                className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono uppercase"
                value={form.panNumber}
                onChange={(e) => setForm((f) => ({ ...f, panNumber: e.target.value.toUpperCase() }))}
                maxLength={10}
                autoComplete="off"
              />
            </label>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Aadhaar (last 4 digits, or full 12 for internal use)</span>
              <input
                className="w-full rounded-md border border-slate-300 px-3 py-2"
                value={form.aadhaar}
                onChange={(e) => setForm((f) => ({ ...f, aadhaar: e.target.value }))}
                inputMode="numeric"
              />
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-800 sm:col-span-2">
              <input
                type="checkbox"
                checked={form.mobileLinkedAadhaar}
                onChange={(e) => setForm((f) => ({ ...f, mobileLinkedAadhaar: e.target.checked }))}
              />
              The mobile number we hold is the same as (or can be used with) the Aadhaar-linked number for verification.
            </label>
            {isBusinessBorrowerType(form.borrowerType) ? (
              <>
                <p className="text-xs text-slate-500 sm:col-span-2">
                  Business verification: reconfirm GSTIN and Udyam for processing; CIN is required for companies.
                </p>
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">GSTIN *</span>
                  <input
                    className="w-full rounded-md border border-slate-300 px-3 py-2"
                    value={form.gstin}
                    onChange={(e) => setForm((f) => ({ ...f, gstin: e.target.value }))}
                  />
                </label>
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Udyam</span>
                  <input
                    className="w-full rounded-md border border-slate-300 px-3 py-2"
                    value={form.udyam}
                    onChange={(e) => setForm((f) => ({ ...f, udyam: e.target.value }))}
                  />
                </label>
                {form.borrowerType === 'COMPANY' ? (
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">CIN / MCA *</span>
                    <input
                      className="w-full rounded-md border border-slate-300 px-3 py-2"
                      value={form.cin}
                      onChange={(e) => setForm((f) => ({ ...f, cin: e.target.value }))}
                    />
                  </label>
                ) : null}
              </>
            ) : null}
          </div>
        </section>
      ) : null}

      {((step === 3 && !needColl) || (step === 4 && needColl)) ? (
        <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Consents</h2>
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

      {((step === 4 && !needColl) || (step === 5 && needColl)) && applicationId ? (
        <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Upload documents</h2>
          <p className="text-sm text-slate-600">Upload a clear copy for each type so underwriters can complete checks without back-and-forth.</p>
          <ul className="space-y-4">
            {allDocumentSlotsForIntake(form).map((slot) => (
              <li key={slot.documentType} className="rounded-md border border-slate-100 bg-slate-50/80 p-4">
                <div className="mb-2 text-sm font-medium text-slate-900">{slot.label}</div>
                <p className="mb-2 text-xs text-slate-600">{slot.reason}</p>
                <div className="flex flex-wrap items-center gap-3">
                  <input
                    type="file"
                    accept=".pdf,image/*"
                    className="text-sm"
                    onChange={(e) => {
                      const f = e.target.files?.[0] ?? null
                      if (e.target) e.target.value = ''
                      void onUploadFile(slot.documentType, f)
                    }}
                    disabled={busy}
                  />
                  {form.documentUploaded[slot.documentType] ? (
                    <span className="text-xs font-medium text-emerald-800">Received</span>
                  ) : (
                    <span className="text-xs text-amber-800">Not uploaded</span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {((step === 5 && !needColl) || (step === 6 && needColl)) && applicationId ? (
        <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Review &amp; submit</h2>
          {docWarning ? (
            <p className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">{docWarning}</p>
          ) : null}
          <div className="grid gap-3 text-sm sm:grid-cols-2">
            <div className="rounded border border-slate-100 p-3">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Product</h3>
              <p className="mt-1 text-slate-900">{form.loanProduct}</p>
              <p className="text-slate-600">
                {form.requestedAmount ? `INR ${form.requestedAmount}` : '—'}
                {form.tenureMonths ? ` · ${form.tenureMonths} mo` : ''}
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
                {allDocumentSlotsForIntake(form).map((s) => (
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
        {step > 0 ? (
          <button
            type="button"
            onClick={goBack}
            disabled={busy}
            className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800"
          >
            Back
          </button>
        ) : null}
        {step < lastStep ? (
          <button
            type="button"
            onClick={() => {
              void goNext()
            }}
            disabled={busy || (step === 0 && workflowsState !== 'ok') || (((step === 2 && !needColl) || (step === 3 && needColl)) && !applicationId)}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy ? 'Please wait…' : 'Continue'}
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
            {busy ? 'Submitting…' : 'Submit for verification'}
          </button>
        )}
        {variant === 'staff' ? (
          <Link to="/applications" className="text-sm text-slate-600 underline">
            Cancel
          </Link>
        ) : null}
      </div>
    </div>
  )
}
