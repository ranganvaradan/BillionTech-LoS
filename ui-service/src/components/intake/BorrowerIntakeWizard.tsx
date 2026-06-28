import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { createApplication, getApplication, updateApplication } from '@/api/applications'
import { deleteBorrowerDraftApplication, listBorrowerApplications, type BorrowerAppSummary } from '@/api/borrowerPortal'
import { listDocuments, uploadDocument } from '@/api/documents'
import { listWorkflows } from '@/api/workflows'
import { submitApplicationForKyc } from '@/api/flow'
import { IntakeFieldError } from '@/components/intake/IntakeFieldError'
import { ErrorState } from '@/components/ErrorState'
import { consentHelper } from '@/lib/intake/intakeLabels'
import { CollateralIntakeFields } from '@/components/intake/CollateralIntakeFields'
import { IndiaStateCityPincodeFields } from '@/components/intake/IndiaStateCityPincodeFields'
import { persistBorrowerIntakeCollateral } from '@/lib/intake/collateralPersist'
import {
  buildBorrowerEmploymentUpdate,
  buildConsentUpdate,
  buildIntakeBorrowerUpdate,
  buildIntakeCreateRequest,
  buildKycUpdate,
} from '@/lib/intake/intakePayloads'
import { detectSecuredCollateralKind, requiresCollateral } from '@/lib/intake/securedProducts'
import { prefetchIntakeGeoForValidation } from '@/lib/intake/masterGeoClientCache'
import { BORROWER_TYPES, createEmptyIntakeFormState, type IntakeFormState } from '@/lib/intake/intakeTypes'
import {
  allConsentsChecked,
  collateralDocumentMissingWarning,
  productsForBorrowerType,
  validateBorrowerBankKycStep,
  validateBorrowerIncomeStep,
  validateBorrowerPersonalAddressStep,
  validateBorrowerProductStep,
  validateCollateralIntakeStep,
  validateConsentStep,
} from '@/lib/intake/intakeValidation'
import {
  activeCatalogHasSecuredProduct,
  workflowLoanProductDisplayName,
} from '@/utils/workflowProducts'
import { clearDraft, loadDraftState, saveDraftState } from '@/lib/borrowerWizardDraft'
import { isBorrowerDeletableApplicationStatus } from '@/lib/borrowerApplicationDeletable'
import { hydrateIntakeFormFromApplication } from '@/lib/intake/hydrateIntakeFromApplication'
import {
  checkBorrowerIdentity,
  checkKycIdentity,
  intakeErrorMessage,
} from '@/lib/intake/checkIntakeIdentity'
import { duplicateFieldErrors, duplicateFieldFromError } from '@/lib/userFriendlyError'
import { notifyError, notifySuccess } from '@/lib/notify'
import type { WorkflowConfigResponse } from '@/types/workflow'
import { type BorrowerType } from '@/types/createApplication'

function buildStepLabels(needColl: boolean): readonly string[] {
  return needColl
    ? [
        'Loan details',
        'Personal & address',
        'Collateral',
        'Bank & KYC',
        'Income & employment',
        'Consent',
        'Review & submit',
      ]
    : [
        'Loan details',
        'Personal & address',
        'Bank & KYC',
        'Income & employment',
        'Consent',
        'Review & submit',
      ]
}

function Stepper({ step, labels }: { step: number; labels: readonly string[] }) {
  return (
    <ol className="mb-8 flex flex-wrap items-center gap-2 border-b border-slate-200 pb-4 text-sm">
      {labels.map((label, i) => (
        <li key={label} className="flex items-center gap-2">
          <span
            className={[
              'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
              i < step ? 'bg-[var(--bt-green-bg)] text-[var(--bt-green)]' : i === step ? 'bg-[var(--bt-orange)] text-white' : 'bg-[var(--bt-gray-100)] text-[var(--bt-gray-500)]',
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

function isIntakeState(x: unknown): x is IntakeFormState {
  if (!x || typeof x !== 'object') return false
  return 'borrowerType' in (x as object) && 'loanProduct' in (x as object)
}

export function BorrowerIntakeWizard() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { user } = useAuth()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState<IntakeFormState>(createEmptyIntakeFormState)
  const [applicationId, setApplicationId] = useState<string | null>(null)
  const [workflows, setWorkflows] = useState<WorkflowConfigResponse[]>([])
  const [wfState, setWfState] = useState<'loading' | 'ok' | 'err'>('loading')
  const [wfError, setWfError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [reviewWarning, setReviewWarning] = useState<string | null>(null)
  const [draftLoaded, setDraftLoaded] = useState(false)
  const [incompleteServer, setIncompleteServer] = useState<BorrowerAppSummary[]>([])
  const [incompleteBusy, setIncompleteBusy] = useState(false)
  const [hydrating, setHydrating] = useState(false)
  const [resumeError, setResumeError] = useState<string | null>(null)

  const products = productsForBorrowerType(workflows, 'INDIVIDUAL')
  const selectedWf = products.find((w) => w.loanProduct === form.loanProduct) ?? null
  const productLocked = Boolean(applicationId)
  const needColl = useMemo(() => requiresCollateral(form.loanProduct), [form.loanProduct])
  const stepLabels = useMemo(() => buildStepLabels(needColl), [needColl])
  const lastStep = needColl ? 6 : 5
  const bankKycStep = needColl ? 3 : 2
  const [collateralDocWarn, setCollateralDocWarn] = useState<string | null>(null)

  function clearFieldError(key: string) {
    setFieldErrors((prev) => {
      if (!prev[key]) return prev
      const next = { ...prev }
      delete next[key]
      return next
    })
  }

  const loadWorkflows = useCallback(async () => {
    setWfState('loading')
    setWfError(null)
    try {
      const all = await listWorkflows()
      const act = all.filter((w) => w.active)
      setWorkflows(act)
      setWfState('ok')
      setForm((f) => {
        const list = productsForBorrowerType(act, 'INDIVIDUAL')
        if (list.length === 0) {
          return f.borrowerType === 'INDIVIDUAL' && f.loanProduct ? { ...f, loanProduct: '' } : f
        }
        if (list.some((w) => w.loanProduct === f.loanProduct)) return f
        return { ...f, loanProduct: list[0]!.loanProduct, borrowerType: 'INDIVIDUAL' as BorrowerType }
      })
    } catch (e) {
      setWorkflows([])
      setWfError(e instanceof Error ? e.message : 'Failed to load workflows')
      setWfState('err')
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async listWorkflows
    void loadWorkflows()
  }, [loadWorkflows])

  useEffect(() => {
    if (draftLoaded) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- one-time; remaining sets restore draft
    setDraftLoaded(true)
    if (searchParams.get('resume')) {
      return
    }
    const d = loadDraftState()
    if (d && isIntakeState(d.state)) {
      const st = d.state as IntakeFormState
      setForm({ ...st, borrowerType: 'INDIVIDUAL' })
      if (d.applicationId) setApplicationId(d.applicationId)
      const max = buildStepLabels(requiresCollateral(st.loanProduct)).length - 1
      if (d.step > 0 && d.step <= max) setStep(d.step)
    }
  }, [draftLoaded, searchParams])

  const reloadIncomplete = useCallback(() => {
    if (!user) return
    void listBorrowerApplications(0, 40)
      .then((p) => {
        setIncompleteServer(p.content.filter((a) => isBorrowerDeletableApplicationStatus(a.status)))
      })
      .catch(() => {
        setIncompleteServer([])
      })
  }, [user])

  useEffect(() => {
    reloadIncomplete()
  }, [reloadIncomplete])

  const resumeId = searchParams.get('resume')
  useEffect(() => {
    if (!resumeId || !user || !draftLoaded) {
      return
    }
    let c = true
    void (async () => {
      setResumeError(null)
      setHydrating(true)
      try {
        const app = await getApplication(resumeId)
        if (!c) return
        if (app.customerId !== user.userId) {
          setResumeError('You can only open your own application.')
          return
        }
        if (!isBorrowerDeletableApplicationStatus(app.status)) {
          setResumeError('This application is no longer a draft. Open it from your applications list.')
          return
        }
        const h = hydrateIntakeFormFromApplication(app)
        setForm({ ...h, borrowerType: 'INDIVIDUAL' })
        setApplicationId(app.id)
        setStep(0)
        saveDraftState(h, app.id, 0)
        setSearchParams(
          (prev) => {
            const next = new URLSearchParams(prev)
            next.delete('resume')
            return next
          },
          { replace: true },
        )
      } catch (e) {
        if (c) setResumeError(intakeErrorMessage(e, 'Could not load application'))
      } finally {
        if (c) setHydrating(false)
      }
    })()
    return () => {
      c = false
    }
  }, [resumeId, user, draftLoaded, setSearchParams])

  useEffect(() => {
    if (!user) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- seed name/email from session when empty
    setForm((f) => {
      if (f.email.trim() || f.fullName.trim()) return f
      return { ...f, fullName: f.fullName || user.name, email: f.email || user.email, borrowerType: 'INDIVIDUAL' }
    })
  }, [user])

  const persistDraft = useCallback(
    (s: number, f: IntakeFormState, appId: string | null) => {
      saveDraftState(f, appId, s)
    },
    [],
  )

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
      // optional
    }
  }, [])

  useEffect(() => {
    if (step === bankKycStep && applicationId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- async listDocuments
      void syncDocumentsFromServer(applicationId)
    }
  }, [step, applicationId, syncDocumentsFromServer, bankKycStep])
  useEffect(() => {
    if (step === 2 && needColl && applicationId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void syncDocumentsFromServer(applicationId)
    }
  }, [step, needColl, applicationId, syncDocumentsFromServer])

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

  async function goNext() {
    setError(null)
    setFieldErrors({})
    setCollateralDocWarn(null)
    const f0 = { ...form, borrowerType: 'INDIVIDUAL' as BorrowerType }
    if (step === 0) {
      const v = validateBorrowerProductStep(f0, workflows)
      if (v) {
        setError(v)
        return
      }
      setStep(1)
      persistDraft(1, f0, applicationId)
      return
    }
    if (step === 1) {
      try {
        await prefetchIntakeGeoForValidation(f0)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Could not load location master data. Try again.')
        return
      }
      const v = validateBorrowerPersonalAddressStep(f0)
      if (v) {
        setError(v)
        return
      }
      setBusy(true)
      try {
        const dup = await checkBorrowerIdentity(f0, 'BORROWER_SELF_SERVICE', applicationId)
        if (dup) {
          setFieldErrors(dup)
          return
        }
        if (!applicationId) {
          const req = buildIntakeCreateRequest(f0, 'BORROWER_SELF_SERVICE', user)
          const res = await createApplication(req)
          setApplicationId(res.id)
          persistDraft(2, f0, res.id)
        } else {
          await updateApplication(applicationId, buildIntakeBorrowerUpdate(f0, 'BORROWER_SELF_SERVICE', user))
          persistDraft(2, f0, applicationId)
        }
        setStep(2)
      } catch (err) {
        const msg = intakeErrorMessage(err, 'Could not save your details.')
        setError(msg)
        notifyError(err, 'Could not save your details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === 2) {
      if (needColl) {
        const v = validateCollateralIntakeStep(f0)
        if (v) {
          setError(v)
          return
        }
        setCollateralDocWarn(collateralDocumentMissingWarning(f0))
        if (!applicationId) return
        setBusy(true)
        try {
          await persistBorrowerIntakeCollateral(applicationId, f0, 'BORROWER_SELF_SERVICE')
          persistDraft(3, f0, applicationId)
          setStep(3)
        } catch (err) {
          setError(intakeErrorMessage(err, 'Could not save collateral details.'))
          notifyError(err, 'Could not save collateral details.')
        } finally {
          setBusy(false)
        }
        return
      }
      const v = validateBorrowerBankKycStep(f0)
      if (v) {
        setError(v)
        return
      }
      if (!applicationId) return
      setBusy(true)
      try {
        const dup = await checkKycIdentity(f0, applicationId)
        if (dup) {
          setFieldErrors(dup)
          return
        }
        await updateApplication(applicationId, buildKycUpdate(f0))
        persistDraft(3, f0, applicationId)
        setStep(3)
      } catch (err) {
        const msg = intakeErrorMessage(err, 'Could not save KYC and bank details.')
        setError(msg)
        notifyError(err, 'Could not save KYC and bank details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === 3) {
      if (needColl) {
        const v = validateBorrowerBankKycStep(f0)
        if (v) {
          setError(v)
          return
        }
        if (!applicationId) return
        setBusy(true)
        try {
          const dup = await checkKycIdentity(f0, applicationId)
          if (dup) {
            setFieldErrors(dup)
            return
          }
          await updateApplication(applicationId, buildKycUpdate(f0))
          persistDraft(4, f0, applicationId)
          setStep(4)
        } catch (err) {
          const msg = intakeErrorMessage(err, 'Could not save KYC and bank details.')
          setError(msg)
          notifyError(err, 'Could not save KYC and bank details.')
        } finally {
          setBusy(false)
        }
        return
      }
      const v2 = validateBorrowerIncomeStep(f0)
      if (v2) {
        setError(v2)
        return
      }
      if (!applicationId) return
      setBusy(true)
      try {
        await updateApplication(applicationId, buildBorrowerEmploymentUpdate(f0))
        persistDraft(4, f0, applicationId)
        setStep(4)
      } catch (err) {
        setError(intakeErrorMessage(err, 'Could not save income details.'))
        notifyError(err, 'Could not save income details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === 4) {
      if (needColl) {
        const v2 = validateBorrowerIncomeStep(f0)
        if (v2) {
          setError(v2)
          return
        }
        if (!applicationId) return
        setBusy(true)
        try {
          await updateApplication(applicationId, buildBorrowerEmploymentUpdate(f0))
          persistDraft(5, f0, applicationId)
          setStep(5)
        } catch (err) {
          setError(intakeErrorMessage(err, 'Could not save income details.'))
        notifyError(err, 'Could not save income details.')
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
        await updateApplication(applicationId, buildConsentUpdate(f0, 'BORROWER_SELF_SERVICE', user))
        setReviewWarning('Some documents are still optional. You can submit to start verification, or go back to upload more.')
        persistDraft(5, f0, applicationId)
        setStep(5)
      } catch (err) {
        setError(intakeErrorMessage(err, 'Could not save consents.'))
        notifyError(err, 'Could not save consents.')
      } finally {
        setBusy(false)
      }
    }
    if (step === 5) {
      if (needColl) {
        const v3 = validateConsentStep(form)
        if (v3) {
          setError(v3)
          return
        }
        if (!applicationId) return
        setBusy(true)
        try {
          await updateApplication(applicationId, buildConsentUpdate(f0, 'BORROWER_SELF_SERVICE', user))
          setReviewWarning('Some documents are still optional. You can submit to start verification, or go back to upload more.')
          persistDraft(6, f0, applicationId)
          setStep(6)
        } catch (err) {
          setError(intakeErrorMessage(err, 'Could not save consents.'))
        notifyError(err, 'Could not save consents.')
        } finally {
          setBusy(false)
        }
      }
    }
  }

  function goBack() {
    setError(null)
    if (step > 0) {
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
      notifySuccess('Application submitted for verification.')
      clearDraft()
      void navigate(`/borrower/applications/${applicationId}`, { replace: true })
    } catch (err) {
      const dupField = duplicateFieldFromError(err)
      if (dupField) {
        const dup = duplicateFieldErrors(err)
        if (dup) setFieldErrors(dup)
        if (dupField === 'email' || dupField === 'mobile') setStep(1)
        else if (dupField === 'panNumber') setStep(bankKycStep)
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

  async function deleteServerDraftApp(id: string) {
    if (!window.confirm('Delete this application draft? This cannot be undone.')) {
      return
    }
    setIncompleteBusy(true)
    setError(null)
    try {
      await deleteBorrowerDraftApplication(id)
      if (applicationId === id) {
        setApplicationId(null)
        setForm(createEmptyIntakeFormState())
        setStep(0)
        clearDraft()
      }
      reloadIncomplete()
    } catch (e) {
      setError(intakeErrorMessage(e, 'Could not delete application.'))
      notifyError(e, 'Could not delete application.')
    } finally {
      setIncompleteBusy(false)
    }
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-slate-900">Apply for a loan</h1>
        <p className="mt-1 text-sm text-slate-600">Complete the steps below. You can save and resume later on this device.</p>
      </div>

      {hydrating || resumeError ? (
        <div className="mb-4 rounded border border-slate-200 bg-white p-3 text-sm text-slate-800 shadow-sm" role="status">
          {hydrating ? 'Loading your saved application…' : null}
          {resumeError ? <span className="text-rose-800">{resumeError}</span> : null}
        </div>
      ) : null}

      {incompleteServer.length > 0 ? (
        <div className="mb-4 rounded border border-amber-200 bg-amber-50/90 p-4 text-sm text-amber-950">
          <p className="font-medium text-amber-950">You have an incomplete application</p>
          <ul className="mt-2 space-y-2">
            {incompleteServer.map((a) => (
              <li
                key={a.applicationId}
                className="flex flex-wrap items-center justify-between gap-2 border-b border-amber-200/80 pb-2 last:border-0 last:pb-0"
              >
                <span>
                  {a.applicationNumber} — {a.friendlyStatus}
                </span>
                <span className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="rounded border border-amber-800 bg-white px-2 py-1 text-xs font-medium text-amber-950"
                    onClick={() => {
                      setSearchParams((prev) => {
                        const n = new URLSearchParams(prev)
                        n.set('resume', a.applicationId)
                        return n
                      })
                    }}
                    disabled={incompleteBusy}
                  >
                    Resume
                  </button>
                  <button
                    type="button"
                    className="rounded border border-rose-600 bg-rose-50 px-2 py-1 text-xs font-medium text-rose-900"
                    onClick={() => void deleteServerDraftApp(a.applicationId)}
                    disabled={incompleteBusy}
                  >
                    Delete draft
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {wfState === 'loading' ? <p className="mb-4 text-sm text-slate-600">Loading active workflows…</p> : null}
      {wfState === 'err' && wfError ? <ErrorState message={wfError} /> : null}
      {wfState === 'ok' && workflows.length === 0 ? (
        <p className="mb-4 bt-alert bt-alert-warning">
          There are no active workflows. Please try again later or contact support.
        </p>
      ) : null}
      {error ? <ErrorState message={error} /> : null}

      <Stepper step={step} labels={stepLabels} />

      {step === 0 ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Loan details</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Borrower class</span>
              <select
                className="bt-input w-full text-slate-900"
                value="INDIVIDUAL"
                disabled
              >
                {BORROWER_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t === 'INDIVIDUAL' ? 'Individual' : t.replaceAll('_', ' ')}
                  </option>
                ))}
              </select>
            </label>
            <div className="text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Loan product *</span>
              <select
                className="bt-input w-full text-slate-900 disabled:cursor-not-allowed disabled:bg-slate-50"
                value={products.length === 0 ? '' : form.loanProduct}
                onChange={(e) => setForm((f) => ({ ...f, loanProduct: e.target.value, borrowerType: 'INDIVIDUAL' }))}
                disabled={wfState !== 'ok' || !products.length || productLocked}
              >
                {products.length === 0 ? <option value="">(none available)</option> : null}
                {products.map((w) => (
                  <option key={w.id} value={w.loanProduct}>
                    {workflowLoanProductDisplayName(w.loanProduct)}
                  </option>
                ))}
              </select>
              {selectedWf ? (
                <p className="mt-1.5 text-xs text-slate-500">
                  Workflow: <span className="font-medium text-slate-800">{selectedWf.name}</span> (v{selectedWf.version})
                </p>
              ) : null}
              {import.meta.env.DEV && products.length > 0 && !activeCatalogHasSecuredProduct(workflows, 'INDIVIDUAL') ? (
                <p className="mt-2 rounded border border-amber-200 bg-amber-50 px-2 py-1.5 text-xs text-amber-950" role="note">
                  No secured products are active. Configure workflow for Loan Against Property, Loan Against Securities, or
                  Loan Against Gold.
                </p>
              ) : null}
            </div>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Amount (INR) *</span>
              <input
                type="number"
                min={0.01}
                step="0.01"
                className="bt-input w-full tabular-nums"
                value={form.requestedAmount}
                onChange={(e) => setForm((f) => ({ ...f, requestedAmount: e.target.value }))}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Preferred tenure (months)</span>
              <input
                type="number"
                min={1}
                step={1}
                className="bt-input w-full tabular-nums"
                value={form.tenureMonths}
                onChange={(e) => setForm((f) => ({ ...f, tenureMonths: e.target.value }))}
                placeholder="Optional"
              />
            </label>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Purpose of loan *</span>
              <textarea
                className="bt-input w-full"
                rows={2}
                value={form.purpose}
                onChange={(e) => setForm((f) => ({ ...f, purpose: e.target.value }))}
              />
            </label>
            <div className="sm:col-span-2">
              <span className="mb-2 block text-xs font-medium text-slate-500">Do you have any existing loans? *</span>
              <div className="flex flex-wrap gap-4 text-sm text-slate-800">
                <label className="inline-flex items-center gap-2">
                  <input
                    type="radio"
                    name="ex"
                    checked={form.hasExistingLoans === 'no'}
                    onChange={() => setForm((f) => ({ ...f, hasExistingLoans: 'no' }))}
                  />
                  No
                </label>
                <label className="inline-flex items-center gap-2">
                  <input
                    type="radio"
                    name="ex"
                    checked={form.hasExistingLoans === 'yes'}
                    onChange={() => setForm((f) => ({ ...f, hasExistingLoans: 'yes' }))}
                  />
                  Yes
                </label>
              </div>
            </div>
            {form.hasExistingLoans === 'yes' ? (
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Existing loan details *</span>
                <textarea
                  className="bt-input w-full"
                  rows={2}
                  value={form.existingLoansDetails}
                  onChange={(e) => setForm((f) => ({ ...f, existingLoansDetails: e.target.value }))}
                  placeholder="Lender, EMI, outstanding, etc."
                />
              </label>
            ) : null}
          </div>
        </section>
      ) : null}

      {step === 1 ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Personal &amp; address</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Full name (as per PAN) *</span>
              <input
                className="bt-input w-full"
                value={form.fullName}
                onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
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
              />
              <IntakeFieldError message={fieldErrors.mobile} />
            </label>
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
              <span className="mb-1 block text-xs font-medium text-slate-500">Date of birth *</span>
              <input
                type="date"
                className="bt-input w-full"
                value={form.dateOfBirth}
                onChange={(e) => setForm((f) => ({ ...f, dateOfBirth: e.target.value }))}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Gender *</span>
              <select
                className="bt-input w-full"
                value={form.gender}
                onChange={(e) => setForm((f) => ({ ...f, gender: e.target.value }))}
              >
                <option value="">Select</option>
                <option value="FEMALE">Female</option>
                <option value="MALE">Male</option>
                <option value="OTHER">Other</option>
                <option value="PREFER_NOT_SAY">Prefer not to say</option>
              </select>
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Marital status *</span>
              <select
                className="bt-input w-full"
                value={form.maritalStatus}
                onChange={(e) => setForm((f) => ({ ...f, maritalStatus: e.target.value }))}
              >
                <option value="">Select</option>
                <option value="SINGLE">Single</option>
                <option value="MARRIED">Married</option>
                <option value="DIVORCED">Divorced</option>
                <option value="WIDOWED">Widowed</option>
                <option value="OTHER">Other</option>
              </select>
            </label>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Address line 1 *</span>
              <input
                className="bt-input w-full"
                value={form.addressLine}
                onChange={(e) => setForm((f) => ({ ...f, addressLine: e.target.value }))}
              />
            </label>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Address line 2</span>
              <input
                className="bt-input w-full"
                value={form.addressLine2}
                onChange={(e) => setForm((f) => ({ ...f, addressLine2: e.target.value }))}
              />
            </label>
            <IndiaStateCityPincodeFields
              stateValue={form.state}
              cityValue={form.city}
              pincodeValue={form.pincode}
              onStateChange={(v) => setForm((f) => ({ ...f, state: v, city: '' }))}
              onCityChange={(v) => setForm((f) => ({ ...f, city: v }))}
              onPincodeChange={(v) => setForm((f) => ({ ...f, pincode: v }))}
              stateCityRequired
            />
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Address proof type *</span>
              <select
                className="bt-input w-full"
                value={form.addressProofType}
                onChange={(e) => setForm((f) => ({ ...f, addressProofType: e.target.value }))}
              >
                <option value="">Select</option>
                <option value="AADHAAR_CARD">Aadhaar card</option>
                <option value="UTILITY_BILL">Utility bill</option>
                <option value="RENT_AGREEMENT">Rent agreement</option>
                <option value="BANK_PASSBOOK_ADDRESS">Passbook (address page)</option>
                <option value="OTHER">Other</option>
              </select>
            </label>
          </div>
        </section>
      ) : null}

      {step === 2 && needColl && detectSecuredCollateralKind(form.loanProduct) ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Collateral for this loan</h2>
          <p className="text-xs text-slate-600">
            This product is secured. Provide details and upload documents for the property, securities, or gold offered as
            security.
          </p>
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

      {((step === 2 && !needColl) || (step === 3 && needColl)) && applicationId ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Identity, bank account &amp; documents</h2>
          <p className="text-xs text-slate-600">
            Optional uploads: address proof, PAN copy, bank statement, and salary slip help us process faster. You can add them
            after saving this step.
          </p>
          <div className="rounded border border-slate-100 bg-slate-50/80 p-4">
            <div className="text-sm font-medium text-slate-900">Address proof (optional)</div>
            <p className="text-xs text-slate-600">Type selected: {form.addressProofType || '—'}. Upload one clear copy.</p>
            <input
              type="file"
              accept=".pdf,image/*"
              className="mt-2 text-sm"
              onChange={(e) => {
                const f = e.target.files?.[0] ?? null
                if (e.target) e.target.value = ''
                void onUploadFile('OTHER', f)
              }}
              disabled={busy}
            />
            {form.documentUploaded.OTHER ? <span className="ml-2 text-xs text-emerald-800">Received</span> : null}
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
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
              />
              <IntakeFieldError message={fieldErrors.panNumber} />
            </label>
            <div className="sm:col-span-2">
              <span className="text-sm font-medium text-slate-800">Optional PAN copy</span>
              <input
                type="file"
                accept=".pdf,image/*"
                className="ml-0 mt-1 block text-sm"
                onChange={(e) => {
                  const f = e.target.files?.[0] ?? null
                  if (e.target) e.target.value = ''
                  void onUploadFile('PAN_CARD', f)
                }}
                disabled={busy}
              />
              {form.documentUploaded.PAN_CARD ? <span className="text-xs text-emerald-800">Received</span> : null}
            </div>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Aadhaar (last 4 or full 12) — optional</span>
              <input
                className="bt-input w-full"
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
              My mobile is suitable for e-KYC / Aadhaar-linked verification where required.
            </label>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Account number *</span>
              <input
                className="bt-input w-full font-mono"
                value={form.bankAccountNumber}
                onChange={(e) => setForm((f) => ({ ...f, bankAccountNumber: e.target.value }))}
                inputMode="numeric"
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">IFSC *</span>
              <input
                className="bt-input w-full font-mono uppercase"
                value={form.ifscCode}
                onChange={(e) => setForm((f) => ({ ...f, ifscCode: e.target.value.toUpperCase() }))}
                maxLength={11}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Bank name *</span>
              <input
                className="bt-input w-full"
                value={form.bankName}
                onChange={(e) => setForm((f) => ({ ...f, bankName: e.target.value }))}
              />
            </label>
            <div className="sm:col-span-2">
              <span className="text-sm font-medium text-slate-800">3-month bank statement (optional)</span>
              <input
                type="file"
                accept=".pdf,image/*"
                className="mt-1 block text-sm"
                onChange={(e) => {
                  const f = e.target.files?.[0] ?? null
                  if (e.target) e.target.value = ''
                  void onUploadFile('BANK_STATEMENT', f)
                }}
                disabled={busy}
              />
              {form.documentUploaded.BANK_STATEMENT ? <span className="text-xs text-emerald-800">Received</span> : null}
            </div>
            <div className="sm:col-span-2">
              <span className="text-sm font-medium text-slate-800">Latest salary slip (optional)</span>
              <input
                type="file"
                accept=".pdf,image/*"
                className="mt-1 block text-sm"
                onChange={(e) => {
                  const f = e.target.files?.[0] ?? null
                  if (e.target) e.target.value = ''
                  void onUploadFile('INCOME_PROOF', f)
                }}
                disabled={busy}
              />
              {form.documentUploaded.INCOME_PROOF ? <span className="text-xs text-emerald-800">Received</span> : null}
            </div>
          </div>
        </section>
      ) : null}

      {((step === 3 && !needColl) || (step === 4 && needColl)) ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Income &amp; employment</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Employment type *</span>
              <select
                className="bt-input w-full"
                value={form.employmentType}
                onChange={(e) => setForm((f) => ({ ...f, employmentType: e.target.value }))}
              >
                <option value="">Select</option>
                <option value="SALARIED">Salaried</option>
                <option value="SELF_EMPLOYED">Self-employed</option>
                <option value="BUSINESS">Business / proprietor</option>
                <option value="OTHER">Other</option>
              </select>
            </label>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Employer / business name *</span>
              <input
                className="bt-input w-full"
                value={form.employerName}
                onChange={(e) => setForm((f) => ({ ...f, employerName: e.target.value }))}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Monthly net income (INR) *</span>
              <input
                type="number"
                min={1}
                step="1"
                className="bt-input w-full tabular-nums"
                value={form.monthlyNetIncome}
                onChange={(e) => setForm((f) => ({ ...f, monthlyNetIncome: e.target.value }))}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Occupation / industry *</span>
              <input
                className="bt-input w-full"
                value={form.occupationIndustry}
                onChange={(e) => setForm((f) => ({ ...f, occupationIndustry: e.target.value }))}
                placeholder="e.g. software engineer, retail"
              />
            </label>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Work experience (years, optional)</span>
              <input
                type="number"
                min={0}
                step={1}
                className="w-full max-w-xs bt-input"
                value={form.workExperienceYears}
                onChange={(e) => setForm((f) => ({ ...f, workExperienceYears: e.target.value }))}
              />
            </label>
          </div>
        </section>
      ) : null}

      {((step === 4 && !needColl) || (step === 5 && needColl)) ? (
        <section className="space-y-3 bt-card p-5">
          <h2 className="bt-card-title">Consents</h2>
          <p className="text-xs text-slate-600">{consentHelper('BORROWER_SELF_SERVICE')}</p>
          <div className="space-y-2 text-sm text-slate-800">
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentKyc}
                onChange={(e) => setForm((f) => ({ ...f, consentKyc: e.target.checked }))}
              />
              <span>KYC: I consent to verification using the information and documents I provide.</span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentBureau}
                onChange={(e) => setForm((f) => ({ ...f, consentBureau: e.target.checked }))}
              />
              <span>Credit: I consent to a bureau report and related credit checks for this application.</span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentAccountAggregator}
                onChange={(e) => setForm((f) => ({ ...f, consentAccountAggregator: e.target.checked }))}
              />
              <span>Bank / AA: I consent to bank statement or account-aggregated data if required to assess the loan.</span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentComms}
                onChange={(e) => setForm((f) => ({ ...f, consentComms: e.target.checked }))}
              />
              <span>Contact: I consent to updates about this application on WhatsApp, SMS, and email.</span>
            </label>
          </div>
        </section>
      ) : null}

      {((step === 5 && !needColl) || (step === 6 && needColl)) && applicationId ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Review &amp; submit</h2>
          {reviewWarning ? <p className="bt-alert bt-alert-warning">{reviewWarning}</p> : null}
          <ul className="grid gap-2 text-sm text-slate-800 sm:grid-cols-2">
            <li className="rounded border border-slate-100 p-2">
              <div className="text-xs text-slate-500">Product &amp; request</div>
              <div>{workflowLoanProductDisplayName(form.loanProduct)} · ₹{form.requestedAmount}</div>
            </li>
            <li className="rounded border border-slate-100 p-2">
              <div className="text-xs text-slate-500">Contact</div>
              <div>{form.fullName}</div>
            </li>
            <li className="rounded border border-slate-100 p-2 sm:col-span-2">
              <div className="text-xs text-slate-500">Address</div>
              <div>
                {form.addressLine}
                {form.addressLine2 ? `, ${form.addressLine2}` : ''}, {form.city} {form.pincode}, {form.state}
              </div>
            </li>
            <li className="rounded border border-slate-100 p-2 sm:col-span-2">
              <div className="text-xs text-slate-500">KYC &amp; bank</div>
              <div>
                PAN {form.panNumber} · {form.bankName} · a/c ending …{form.bankAccountNumber.replace(/\D/g, '').slice(-4)}
              </div>
            </li>
            <li className="rounded border border-slate-100 p-2 sm:col-span-2">
              <div className="text-xs text-slate-500">Income &amp; employment</div>
              <div>
                {form.employmentType} · {form.employerName} · net ₹{form.monthlyNetIncome}/mo
              </div>
            </li>
            {needColl && detectSecuredCollateralKind(form.loanProduct) ? (
              <li className="rounded border border-slate-100 p-2 sm:col-span-2">
                <div className="text-xs text-slate-500">Collateral</div>
                <div className="text-slate-800">
                  {detectSecuredCollateralKind(form.loanProduct) === 'PROPERTY'
                    ? `Property — est. value ₹${form.collateralEstimatedMarketValue || '—'}`
                    : null}
                  {detectSecuredCollateralKind(form.loanProduct) === 'SHARES'
                    ? `Securities (ISIN ${form.collateralIsin || '—'}) — est. ₹${form.collateralShareMarketValue || '—'}`
                    : null}
                  {detectSecuredCollateralKind(form.loanProduct) === 'GOLD'
                    ? `Gold — est. value ₹${form.collateralGoldEstimatedValue || '—'}`
                    : null}
                </div>
              </li>
            ) : null}
            <li className="rounded border border-slate-100 p-2 sm:col-span-2">
              <div className="text-xs text-slate-500">Consents</div>
              <div>{allConsentsChecked(form) ? 'All accepted' : 'Incomplete — go back to consent step'}</div>
            </li>
          </ul>
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
            disabled={busy || (step === 0 && wfState !== 'ok') || (((step === 2 && !needColl) || (step === 3 && needColl)) && !applicationId)}
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
            {busy ? 'Submitting…' : 'Submit application'}
          </button>
        )}
        <button
          type="button"
          onClick={() => {
            saveDraftState(form, applicationId, step)
          }}
          className="text-sm text-slate-600 underline"
        >
          Save draft
        </button>
      </div>
    </div>
  )
}
