import { useCallback, useEffect, useState } from 'react'
import {
  createPlpProgram,
  listPlpProgramsForAnchor,
  listSyncedAnchors,
  retryPlpProgram,
  retryPlpSubProgram,
} from '@/api/plp'
import { getCam } from '@/api/cam'
import { ApiError } from '@/api/http'
import type { ApplicationResponse } from '@/types/application'
import type { CreatePlpProgramRequest, PlpProgramSetupResponse, PlpProgramSummary } from '@/types/plp'
import { PlpSyncStatusBadge } from '@/components/plp/PlpSyncStatusBadge'

const PROGRAM_TYPES = [
  { value: 'INVOICE_DISCOUNTING', label: 'Invoice discounting' },
  { value: 'PAY_DAY_LOAN', label: 'Pay day loan' },
]

const INVOICE_FLOW_TYPES = [
  { value: 'PURCHASE_BILL_DISCOUNTING', label: 'Purchase bill discounting (anchor = seller)' },
  { value: 'SALES_BILL_DISCOUNTING', label: 'Sales bill discounting (anchor = buyer)' },
  { value: 'PURCHASE_ORDER_DISCOUNTING', label: 'Purchase order discounting (anchor = buyer)' },
]

const DEFAULT_ID_INTEREST_RATE = '12'
const DEFAULT_ID_TENURE_DAYS = '90'

function setupResponseFromSummary(p: PlpProgramSummary, anchorId: string): PlpProgramSetupResponse {
  return {
    programId: p.programId,
    subProgramId: p.subProgramId ?? '',
    anchorId: p.anchorId ?? anchorId,
    programName: p.programName,
    programType: p.programType,
    creditLimit: p.creditLimit,
    interestRate: p.interestRate,
    tenureDays: p.tenureDays,
    currency: p.currency,
    validityStartDate: p.validityStartDate,
    validityEndDate: p.validityEndDate,
    programSyncStatus: p.programSyncStatus,
    programSyncError: null,
    subProgramSyncStatus: p.subProgramSyncStatus ?? 'NOT_SYNCED',
    subProgramSyncError: null,
    plpProgramId: null,
    plpSubProgramId: null,
    programSyncedAt: null,
    subProgramSyncedAt: null,
  }
}

export function PlpProgramSetupSection({ app }: { app: ApplicationResponse }) {
  const [open, setOpen] = useState(true)
  const [anchors, setAnchors] = useState<
    { id: string; name: string; code: string; sourceAnchorApplicationId?: string | null }[]
  >([])
  const [anchorId, setAnchorId] = useState('')
  const [programName, setProgramName] = useState('')
  const [programType, setProgramType] = useState('INVOICE_DISCOUNTING')
  const [flowType, setFlowType] = useState('PURCHASE_BILL_DISCOUNTING')
  const [lmsEntryIn, setLmsEntryIn] = useState('NO')
  const [encoreProductCode, setEncoreProductCode] = useState('')
  const [creditLimit, setCreditLimit] = useState('')
  const [interestRate, setInterestRate] = useState('')
  const [tenureDays, setTenureDays] = useState('')
  const [validityStart, setValidityStart] = useState('')
  const [validityEnd, setValidityEnd] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<PlpProgramSetupResponse | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  const loadAnchors = useCallback(async () => {
    try {
      const list = await listSyncedAnchors()
      setAnchors(
        list.map((a) => ({
          id: a.id,
          name: a.name,
          code: a.code,
          sourceAnchorApplicationId: a.sourceAnchorApplicationId,
        })),
      )
      const linked = app.id
        ? list.find((a) => a.sourceAnchorApplicationId === app.id)
        : undefined
      const resolvedAnchorId = linked ? linked.id : list.length === 1 ? list[0]!.id : ''
      let hasExistingProgram = false
      if (resolvedAnchorId) {
        setAnchorId(resolvedAnchorId)
        try {
          const programs = await listPlpProgramsForAnchor(resolvedAnchorId)
          const existing = programs[0]
          if (existing) {
            hasExistingProgram = true
            const hydrated = setupResponseFromSummary(existing, resolvedAnchorId)
            setSaved(hydrated)
            setProgramName(existing.programName)
            setProgramType(existing.programType)
            setCreditLimit(existing.creditLimit != null ? String(existing.creditLimit) : '')
            setInterestRate(existing.interestRate != null ? String(existing.interestRate) : '')
            setTenureDays(existing.tenureDays != null ? String(existing.tenureDays) : '')
            setValidityStart(existing.validityStartDate ?? '')
            setValidityEnd(existing.validityEndDate ?? '')
            if (existing.flowType) setFlowType(existing.flowType)
            if (existing.lmsEntryIn) setLmsEntryIn(existing.lmsEntryIn)
            if (existing.encoreProductCode) setEncoreProductCode(existing.encoreProductCode)
            if (
              hydrated.programSyncStatus === 'SYNC_SUCCESS' &&
              hydrated.subProgramSyncStatus === 'SYNC_SUCCESS'
            ) {
              setSuccessMsg('Program and sub-program are synced to PLP.')
            }
          }
        } catch {
          /* no existing program for anchor */
        }
      }
      if (!hasExistingProgram && app.id) {
        if (app.requestedAmount != null) {
          setCreditLimit(String(app.requestedAmount))
        }
        setInterestRate((prev) => prev || DEFAULT_ID_INTEREST_RATE)
        setTenureDays((prev) => prev || DEFAULT_ID_TENURE_DAYS)
        try {
          const cam = await getCam(app.id)
          if (app.requestedAmount == null && cam.recommendedAmount != null) {
            setCreditLimit(String(cam.recommendedAmount))
          }
          if (cam.recommendedRate != null) {
            setInterestRate(String(cam.recommendedRate))
          }
          if (cam.recommendedTenureMonths != null) {
            setTenureDays(String(cam.recommendedTenureMonths * 30))
          }
        } catch {
          /* CAM may not exist yet — keep requested amount / ID defaults */
        }
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load anchors.')
    }
  }, [app.id, app.requestedAmount])

  useEffect(() => {
    void loadAnchors()
  }, [loadAnchors])

  async function handleSave() {
    if (!anchorId || !programName.trim()) {
      setError('Anchor and program name are required.')
      return
    }
    if (
      programType === 'INVOICE_DISCOUNTING' &&
      lmsEntryIn === 'YES' &&
      !encoreProductCode.trim()
    ) {
      setError('Encore product code is required when LMS entry is enabled.')
      return
    }
    setBusy(true)
    setError(null)
    setSuccessMsg(null)
    try {
      const body: CreatePlpProgramRequest = {
        anchorId,
        programName: programName.trim(),
        programType,
        creditLimit: creditLimit ? Number(creditLimit) : undefined,
        interestRate: interestRate ? Number(interestRate) : undefined,
        tenureDays: tenureDays ? Number(tenureDays) : undefined,
        validityStartDate: validityStart || undefined,
        validityEndDate: validityEnd || undefined,
        flowType: programType === 'INVOICE_DISCOUNTING' ? flowType : undefined,
        lmsEntryIn,
        encoreProductCode:
          programType === 'INVOICE_DISCOUNTING' && lmsEntryIn === 'YES'
            ? encoreProductCode.trim()
            : undefined,
      }
      const result = await createPlpProgram(body)
      setSaved(result)
      if (
        result.programSyncStatus === 'SYNC_SUCCESS' &&
        result.subProgramSyncStatus === 'SYNC_SUCCESS'
      ) {
        setSuccessMsg('Program and sub-program were saved and synced to PLP successfully.')
      } else if (
        result.programSyncStatus === 'SYNC_FAILED' ||
        result.subProgramSyncStatus === 'SYNC_FAILED'
      ) {
        const parts = [
          result.programSyncError,
          result.subProgramSyncError,
        ].filter(Boolean)
        setError(
          parts.length > 0
            ? parts.join(' ')
            : 'Program saved in LOS but PLP sync failed. Use Retry on the badges below.',
        )
      } else {
        setSuccessMsg('Program saved in LOS. PLP sync is in progress or pending.')
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not create PLP program.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mt-6 rounded-lg border border-slate-200 bg-slate-50">
      <button
        type="button"
        className="flex w-full items-center justify-between px-4 py-3 text-left bt-card-title"
        onClick={() => setOpen((o) => !o)}
      >
        PLP Program Setup
        <span className="text-slate-500">{open ? '−' : '+'}</span>
      </button>
      {open ? (
        <ProgramSetupForm
          anchors={anchors}
          anchorId={anchorId}
          setAnchorId={setAnchorId}
          programName={programName}
          setProgramName={setProgramName}
          programType={programType}
          setProgramType={setProgramType}
          flowType={flowType}
          setFlowType={setFlowType}
          lmsEntryIn={lmsEntryIn}
          setLmsEntryIn={setLmsEntryIn}
          encoreProductCode={encoreProductCode}
          setEncoreProductCode={setEncoreProductCode}
          creditLimit={creditLimit}
          setCreditLimit={setCreditLimit}
          interestRate={interestRate}
          setInterestRate={setInterestRate}
          tenureDays={tenureDays}
          setTenureDays={setTenureDays}
          validityStart={validityStart}
          setValidityStart={setValidityStart}
          validityEnd={validityEnd}
          setValidityEnd={setValidityEnd}
          error={error}
          busy={busy}
          saved={saved}
          onSave={() => void handleSave()}
          successMsg={successMsg}
          onRetryProgram={async () => {
            if (!saved?.programId) return
            const r = await retryPlpProgram(saved.programId)
            setSaved((s) =>
              s
                ? {
                    ...s,
                    programSyncStatus: r.syncStatus,
                    programSyncError: r.syncError ?? null,
                  }
                : s,
            )
            if (r.syncStatus === 'SYNC_SUCCESS') {
              setSuccessMsg('Program synced to PLP successfully.')
              setError(null)
            } else if (r.syncError) {
              setError(r.syncError)
            }
          }}
          onRetrySubProgram={async () => {
            if (!saved?.subProgramId) return
            const r = await retryPlpSubProgram(saved.subProgramId)
            setSaved((s) =>
              s
                ? {
                    ...s,
                    subProgramSyncStatus: r.syncStatus,
                    subProgramSyncError: r.syncError ?? null,
                  }
                : s,
            )
            if (r.syncStatus === 'SYNC_SUCCESS') {
              setSuccessMsg('Sub-program synced to PLP successfully.')
              setError(null)
            } else if (r.syncError) {
              setError(r.syncError)
            }
          }}
        />
      ) : null}
    </div>
  )
}

function ProgramSetupForm(props: {
  anchors: { id: string; name: string; code: string }[]
  anchorId: string
  setAnchorId: (v: string) => void
  programName: string
  setProgramName: (v: string) => void
  programType: string
  setProgramType: (v: string) => void
  flowType: string
  setFlowType: (v: string) => void
  lmsEntryIn: string
  setLmsEntryIn: (v: string) => void
  encoreProductCode: string
  setEncoreProductCode: (v: string) => void
  creditLimit: string
  setCreditLimit: (v: string) => void
  interestRate: string
  setInterestRate: (v: string) => void
  tenureDays: string
  setTenureDays: (v: string) => void
  validityStart: string
  setValidityStart: (v: string) => void
  validityEnd: string
  setValidityEnd: (v: string) => void
  error: string | null
  successMsg: string | null
  busy: boolean
  saved: PlpProgramSetupResponse | null
  onSave: () => void
  onRetryProgram: () => Promise<void>
  onRetrySubProgram: () => Promise<void>
}) {
  const {
    anchors,
    anchorId,
    setAnchorId,
    programName,
    setProgramName,
    programType,
    setProgramType,
    flowType,
    setFlowType,
    lmsEntryIn,
    setLmsEntryIn,
    encoreProductCode,
    setEncoreProductCode,
    creditLimit,
    setCreditLimit,
    interestRate,
    setInterestRate,
    tenureDays,
    setTenureDays,
    validityStart,
    setValidityStart,
    validityEnd,
    setValidityEnd,
    error,
    successMsg,
    busy,
    saved,
    onSave,
    onRetryProgram,
    onRetrySubProgram,
  } = props

  return (
    <div className="border-t border-slate-200 px-4 pb-4 pt-2">
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
          Anchor
          <select
            className="mt-1 bt-input w-full text-sm"
            value={anchorId}
            onChange={(e) => setAnchorId(e.target.value)}
            disabled={anchors.length === 0}
          >
            {anchors.length === 0 ? (
              <option value="">
                No anchors synced to PLP. Approve CAM for an Invoice Discounting anchor application first.
              </option>
            ) : (
              <>
                <option value="">Select anchor…</option>
                {anchors.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name} ({a.code})
                  </option>
                ))}
              </>
            )}
          </select>
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Program name *
          <input
            className="mt-1 bt-input w-full text-sm"
            value={programName}
            onChange={(e) => setProgramName(e.target.value)}
          />
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Program type *
          <select
            className="mt-1 bt-input w-full text-sm"
            value={programType}
            onChange={(e) => setProgramType(e.target.value)}
          >
            {PROGRAM_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </label>
        {programType === 'INVOICE_DISCOUNTING' ? (
          <label className="block text-sm font-medium text-slate-700">
            Bill discounting flow *
            <select
              className="mt-1 bt-input w-full text-sm"
              value={flowType}
              onChange={(e) => setFlowType(e.target.value)}
            >
              {INVOICE_FLOW_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </label>
        ) : null}
        {programType === 'INVOICE_DISCOUNTING' ? (
          <label className="block text-sm font-medium text-slate-700">
            LMS entry (Encore)
            <select
              className="mt-1 bt-input w-full text-sm"
              value={lmsEntryIn}
              onChange={(e) => setLmsEntryIn(e.target.value)}
            >
              <option value="NO">No — internal loan account on PLP</option>
              <option value="YES">Yes — create loan in Encore LMS</option>
            </select>
          </label>
        ) : null}
        {programType === 'INVOICE_DISCOUNTING' && lmsEntryIn === 'YES' ? (
          <label className="block text-sm font-medium text-slate-700">
            Encore product code *
            <input
              className="mt-1 bt-input w-full text-sm"
              value={encoreProductCode}
              onChange={(e) => setEncoreProductCode(e.target.value)}
              placeholder="e.g. IPPOPAYM01"
            />
          </label>
        ) : null}
        <label className="block text-sm font-medium text-slate-700">
          Credit limit
          <input
            type="number"
            className="mt-1 bt-input w-full text-sm"
            value={creditLimit}
            onChange={(e) => setCreditLimit(e.target.value)}
          />
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Interest rate (%)
          <input
            type="number"
            step="0.01"
            className="mt-1 bt-input w-full text-sm"
            value={interestRate}
            onChange={(e) => setInterestRate(e.target.value)}
          />
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Tenure (days)
          <input
            type="number"
            className="mt-1 bt-input w-full text-sm"
            value={tenureDays}
            onChange={(e) => setTenureDays(e.target.value)}
          />
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Validity start
          <input
            type="date"
            className="mt-1 bt-input w-full text-sm"
            value={validityStart}
            onChange={(e) => setValidityStart(e.target.value)}
          />
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Validity end
          <input
            type="date"
            className="mt-1 bt-input w-full text-sm"
            value={validityEnd}
            onChange={(e) => setValidityEnd(e.target.value)}
          />
        </label>
      </div>

      {successMsg ? (
        <p className="mt-3 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
          {successMsg}
        </p>
      ) : null}
      {error ? <p className="mt-3 text-sm text-red-600">{error}</p> : null}

      {saved ? (
        <div className="mt-4 space-y-2">
          <div className="flex flex-wrap gap-3">
            <PlpSyncStatusBadge
              status={saved.programSyncStatus}
              label="Program"
              onRetry={onRetryProgram}
            />
            <PlpSyncStatusBadge
              status={saved.subProgramSyncStatus}
              label="Sub-program"
              onRetry={onRetrySubProgram}
            />
          </div>
          {saved.programSyncError ? (
            <p className="text-sm text-red-700">Program: {saved.programSyncError}</p>
          ) : null}
          {saved.subProgramSyncError ? (
            <p className="text-sm text-red-700">Sub-program: {saved.subProgramSyncError}</p>
          ) : null}
        </div>
      ) : null}

      <button
        type="button"
        className="mt-4 rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        disabled={busy}
        onClick={onSave}
      >
        {busy ? 'Saving…' : saved ? 'Update program setup' : 'Save program to PLP'}
      </button>
    </div>
  )
}
