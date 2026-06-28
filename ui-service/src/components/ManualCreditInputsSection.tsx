import { useState, type ReactNode } from 'react'
import { saveManualCreditInputs, type ManualCreditInputsPayload } from '@/api/applications'
import { uploadDocument } from '@/api/documents'
import { ErrorState } from '@/components/ErrorState'
import { messageForKycAction } from '@/api/kycErrorMessage'
import { providerSnapshotForManualForm } from '@/lib/manualCreditDisplay'
import { getVisibleUnderwritingFields } from '@/lib/credit/underwritingFieldVisibility'
import type { ApplicationResponse } from '@/types/application'

const SRC = ['PROVIDER', 'MANUAL'] as const

function Card({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle?: string
  children: ReactNode
}) {
  return (
    <section className="bt-section-card bt-section-card--default p-4 shadow-sm">
      <h4 className="bt-card-title">{title}</h4>
      {subtitle ? <p className="mb-3 text-xs text-slate-500">{subtitle}</p> : <div className="mb-2" />}
      <div className="space-y-3">{children}</div>
    </section>
  )
}

function DsNote({ bureau, income, kyc }: { bureau: string; income: string; kyc: string }) {
  return (
    <p className="text-xs text-slate-600">
      Underwriting uses: <span className="font-medium">Bureau</span> from <strong>{bureau}</strong>,{' '}
      <span className="font-medium">Income / obligations</span> from <strong>{income}</strong>,{' '}
      <span className="font-medium">KYC</span> from <strong>{kyc}</strong>.
    </p>
  )
}

function Row2({
  label,
  prov,
  input,
  mono,
  rowId,
}: {
  label: string
  prov: string
  input: ReactNode
  mono?: boolean
  /** Sticky app layout: keep deep-linked field below the top bar. */
  rowId?: string
}) {
  return (
    <div
      id={rowId}
      className={[
        'grid gap-2 border-b border-slate-100 pb-2 last:border-0 sm:grid-cols-2',
        rowId ? 'scroll-mt-28' : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div>
        <div className="text-xs font-medium text-slate-500">{label}</div>
        <div className={mono ? 'mt-0.5 font-mono text-xs text-slate-800' : 'mt-0.5 text-sm text-slate-800'}>
          {prov || '—'}
        </div>
        <div className="text-[10px] uppercase tracking-wide text-slate-400">On file (provider / application)</div>
      </div>
      <div>
        <div className="text-xs font-medium text-slate-500">Your adjustment</div>
        {input}
      </div>
    </div>
  )
}

export function ManualCreditInputsSection({
  applicationId,
  app,
  onRefetch,
  bankStatementOnFile = false,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void
  /** From documents list: a BANK_STATEMENT file exists for this application. */
  bankStatementOnFile?: boolean
}) {
  const [saving, setSaving] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [okMsg, setOkMsg] = useState<string | null>(null)
  const cc = app.creditControlView as
    | { creditControl?: { manual?: Record<string, unknown>; decisionSources?: Record<string, string> } }
    | undefined
  const manual = cc?.creditControl?.manual
  const ds = cc?.creditControl?.decisionSources

  const [panName, setPanName] = useState((manual?.panName as { value?: string } | undefined)?.value ?? '')
  const [panStatus, setPanStatus] = useState((manual?.panStatus as { value?: string } | undefined)?.value ?? '')
  const [aadhaarName, setAadhaarName] = useState(
    (manual?.aadhaarName as { value?: string } | undefined)?.value ?? '',
  )
  const [aadhaarStatus, setAadhaarStatus] = useState(
    (manual?.aadhaarStatus as { value?: string } | undefined)?.value ?? '',
  )
  const [monthlyIncome, setMonthlyIncome] = useState(
    (manual?.monthlyIncome as { value?: string } | undefined)?.value ?? '',
  )
  const [monthlyObligation, setMonthlyObligation] = useState(
    (manual?.monthlyObligation as { value?: string } | undefined)?.value ?? '',
  )
  const [gstIncome, setGstIncome] = useState(
    (manual?.gstIncome as { value?: string } | undefined)?.value ?? '',
  )
  const [bankStatementIncome, setBankStatementIncome] = useState(
    (manual?.bankStatementIncome as { value?: string } | undefined)?.value ?? '',
  )
  const [averageBankBalance, setAverageBankBalance] = useState(
    (manual?.averageBankBalance as { value?: string } | undefined)?.value ?? '',
  )
  const [obligationRatio, setObligationRatio] = useState(
    (manual?.obligationRatio as { value?: string } | undefined)?.value ?? '',
  )
  const [emiObligation, setEmiObligation] = useState(
    (manual?.emiObligation as { value?: string } | undefined)?.value ?? '',
  )
  const [propertyValue, setPropertyValue] = useState(
    (manual?.propertyValue as { value?: string } | undefined)?.value ?? '',
  )
  const [ltvManual, setLtvManual] = useState((manual?.ltv as { value?: string } | undefined)?.value ?? '')
  const [businessVintageMonths, setBusinessVintageMonths] = useState(
    (manual?.businessVintageMonths as { value?: string } | undefined)?.value ?? '',
  )
  const [industryRisk, setIndustryRisk] = useState(
    (manual?.industryRisk as { value?: string } | undefined)?.value ?? '',
  )
  const [repaymentHistory, setRepaymentHistory] = useState(
    (manual?.repaymentHistory as { value?: string } | undefined)?.value ?? '',
  )
  const [ebitdaProxy, setEbitdaProxy] = useState(
    (manual?.ebitdaProxy as { value?: string } | undefined)?.value ?? '',
  )
  const [leverageRatio, setLeverageRatio] = useState(
    (manual?.leverageRatio as { value?: string } | undefined)?.value ?? '',
  )
  const [state, setState] = useState((manual?.state as { value?: string } | undefined)?.value ?? '')
  const [city, setCity] = useState((manual?.city as { value?: string } | undefined)?.value ?? '')
  const [creditRemarks, setCreditRemarks] = useState(
    (manual?.creditRemarks as { value?: string } | undefined)?.value ?? '',
  )
  const [manualBureauScore, setManualBureauScore] = useState(
    String(app.manualBureauScore ?? (manual?.bureauScore as { value?: string } | undefined)?.value ?? ''),
  )
  const [manualBureauRemarks, setManualBureauRemarks] = useState(String(app.manualBureauRemarks ?? ''))
  const [manualKycOutcome, setManualKycOutcome] = useState(
    (manual?.manualKycOutcome as { value?: string } | undefined)?.value ?? '',
  )
  const [bureauScoreSource, setBureauScoreSource] = useState(
    (ds?.bureauScoreSource as string) || 'PROVIDER',
  )
  const [incomeSource, setIncomeSource] = useState((ds?.incomeSource as string) || 'PROVIDER')
  const [kycSource, setKycSource] = useState((ds?.kycSource as string) || 'PROVIDER')

  const supRaw = manual?.supportingDocumentIds as { value?: unknown } | undefined
  const [supportingDocumentIds, setSupportingDocumentIds] = useState<string[]>(
    () =>
      supRaw && Array.isArray(supRaw.value) ? (supRaw.value as unknown[]).map((x) => String(x)) : [],
  )
  const [evidenceUploading, setEvidenceUploading] = useState(false)
  const [evidenceJustUploaded, setEvidenceJustUploaded] = useState<string | null>(null)

  const uwFields = getVisibleUnderwritingFields(app.borrowerType)
  const pv = providerSnapshotForManualForm(app, { bankStatementOnFile })
  const bureauSrc = bureauScoreSource === 'MANUAL' ? 'your manual / uploaded evidence' : 'bureau feed'
  const incSrc = incomeSource === 'MANUAL' ? 'your entries below' : 'application & statements'
  const kycS = kycSource === 'MANUAL' ? 'manual KYC override' : 'KYC workflow'

  async function onSave() {
    setErr(null)
    setOkMsg(null)
    setSaving(true)
    const p: ManualCreditInputsPayload = {
      decisionSources: {
        bureauScoreSource: bureauScoreSource,
        incomeSource: incomeSource,
        kycSource: kycSource,
      },
      panName: panName.trim() || undefined,
      panStatus: panStatus.trim() || undefined,
      aadhaarName: aadhaarName.trim() || undefined,
      aadhaarStatus: aadhaarStatus.trim() || undefined,
      monthlyIncome: monthlyIncome ? Number.parseFloat(monthlyIncome) : undefined,
      monthlyObligation: monthlyObligation ? Number.parseFloat(monthlyObligation) : undefined,
      gstIncome: gstIncome ? Number.parseFloat(gstIncome) : undefined,
      bankStatementIncome: bankStatementIncome ? Number.parseFloat(bankStatementIncome) : undefined,
      averageBankBalance: averageBankBalance ? Number.parseFloat(averageBankBalance) : undefined,
      obligationRatio: obligationRatio ? Number.parseFloat(obligationRatio) : undefined,
      emiObligation: emiObligation ? Number.parseFloat(emiObligation) : undefined,
      propertyValue: propertyValue ? Number.parseFloat(propertyValue) : undefined,
      ltv: ltvManual ? Number.parseFloat(ltvManual) : undefined,
      businessVintageMonths: businessVintageMonths
        ? Number.parseInt(businessVintageMonths, 10)
        : undefined,
      industryRisk: industryRisk.trim() || undefined,
      repaymentHistory: repaymentHistory.trim() || undefined,
      ebitdaProxy: ebitdaProxy ? Number.parseFloat(ebitdaProxy) : undefined,
      leverageRatio: leverageRatio ? Number.parseFloat(leverageRatio) : undefined,
      state: state.trim() || undefined,
      city: city.trim() || undefined,
      creditRemarks: creditRemarks.trim() || undefined,
      manualBureauRemarks: manualBureauRemarks.trim() || undefined,
      manualKycOutcome: manualKycOutcome.trim() || undefined,
      supportingDocumentIds: supportingDocumentIds.length ? supportingDocumentIds : undefined,
    }
    const n = Number.parseInt(manualBureauScore, 10)
    if (manualBureauScore.trim() && !Number.isNaN(n) && n > 0) {
      p.manualBureauScore = n
    }
    try {
      await saveManualCreditInputs(applicationId, p)
      onRefetch()
      setOkMsg('Saved. Manual adjustments are applied for the next underwriting run.')
    } catch (e) {
      setErr(messageForKycAction(e))
    } finally {
      setSaving(false)
    }
  }

  const inputCls = 'bt-input w-full text-sm'

  return (
    <div
      id="manual-credit-inputs"
      className="scroll-mt-20 space-y-4 rounded-lg border border-slate-200 bg-slate-50/80 p-4 text-sm text-slate-800"
    >
      <div>
        <h3 className="text-base font-semibold text-slate-900">Manual credit inputs</h3>
        <p className="mt-1 text-xs text-slate-600">
          Credit Manager adjustments layer on top of provider data. Provider fields are not overwritten; saved values feed
          underwriting when you choose <strong>Manual</strong> as the source of truth below.
        </p>
      </div>
      {okMsg ? (
        <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
          {okMsg}
        </div>
      ) : null}
      {err ? <ErrorState message={err} /> : null}

      <Card
        title="How underwriting uses your data"
        subtitle="These choices apply to the next credit decision, not the raw KYC or bureau store."
      >
        <div className="grid gap-3 sm:grid-cols-3">
          <label className="block text-xs text-slate-600">
            Bureau score for decision
            <select
              className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1.5"
              value={bureauScoreSource}
              onChange={(e) => setBureauScoreSource(e.target.value)}
            >
              {SRC.map((s) => (
                <option key={`b${s}`} value={s}>
                  {s === 'PROVIDER' ? 'Provider (pull or file)' : 'Manual override'}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-xs text-slate-600">
            Income & obligations
            <select
              className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1.5"
              value={incomeSource}
              onChange={(e) => setIncomeSource(e.target.value)}
            >
              {SRC.map((s) => (
                <option key={`i${s}`} value={s}>
                  {s === 'PROVIDER' ? 'Application & statements' : 'Manual values below'}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-xs text-slate-600">
            KYC outcome
            <select
              className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1.5"
              value={kycSource}
              onChange={(e) => setKycSource(e.target.value)}
            >
              {SRC.map((s) => (
                <option key={`k${s}`} value={s}>
                  {s === 'PROVIDER' ? 'Workflow / provider KYC' : 'Manual override'}
                </option>
              ))}
            </select>
          </label>
        </div>
        <DsNote
          bureau={bureauSrc}
          income={incSrc}
          kyc={kycS}
        />
        <p className="text-[11px] text-slate-500">
          After saving, reload this page or return from underwriting to see updated effective values in the
          <span className="font-medium"> Underwriting</span> tab.
        </p>
      </Card>

      <Card title="A. Identity overrides" subtitle="Name checks vs application / KYC on file.">
        <Row2
          label="PAN name"
          prov={pv.panName}
          input={
            <input className={inputCls} value={panName} onChange={(e) => setPanName(e.target.value)} placeholder="Name as on PAN" />
          }
        />
        <Row2
          label="PAN status"
          prov="—"
          input={
            <input className={inputCls} value={panStatus} onChange={(e) => setPanStatus(e.target.value)} placeholder="e.g. ACTIVE" />
          }
        />
        <Row2
          label="Aadhaar / ID name"
          prov={pv.aadhaarName}
          input={
            <input className={inputCls} value={aadhaarName} onChange={(e) => setAadhaarName(e.target.value)} />
          }
        />
        <Row2
          label="Aadhaar / ID status"
          prov="—"
          input={
            <input
              className={inputCls}
              value={aadhaarStatus}
              onChange={(e) => setAadhaarStatus(e.target.value)}
              placeholder="e.g. verified"
            />
          }
        />
        <Row2
          label="Manual KYC outcome"
          prov="—"
          rowId="manual-credit-kyc-outcome"
          input={
            <input
              className={inputCls}
              value={manualKycOutcome}
              onChange={(e) => setManualKycOutcome(e.target.value)}
              placeholder="Used when KYC source = Manual: PASS or FAIL"
            />
          }
        />
      </Card>

      <Card title="B. Income & cashflow" subtitle="GST, bank, and average balances.">
        <Row2
          label="Monthly income"
          prov={pv.monthlyIncome}
          mono
          rowId="manual-credit-monthly-income"
          input={
            <input
              className={inputCls}
              value={monthlyIncome}
              onChange={(e) => setMonthlyIncome(e.target.value.replace(/[^0-9.]/g, ''))}
            />
          }
        />
        {uwFields.showGstIncome ? (
          <Row2
            label="GST / assessed (monthly)"
            prov="—"
            rowId="manual-credit-gst-income"
            input={
              <input className={inputCls} value={gstIncome} onChange={(e) => setGstIncome(e.target.value.replace(/[^0-9.]/g, ''))} />
            }
          />
        ) : null}
        <Row2
          label="Bank statement income (monthly)"
          prov="—"
          rowId="manual-credit-bank-statement-income"
          input={
            <input
              className={inputCls}
              value={bankStatementIncome}
              onChange={(e) => setBankStatementIncome(e.target.value.replace(/[^0-9.]/g, ''))}
            />
          }
        />
        <Row2
          label="Average bank balance"
          prov="—"
          rowId="manual-credit-average-bank-balance"
          input={
            <input
              className={inputCls}
              value={averageBankBalance}
              onChange={(e) => setAverageBankBalance(e.target.value.replace(/[^0-9.]/g, ''))}
            />
          }
        />
        {uwFields.showEbitdaProxy ? (
          <Row2
            label="EBITDA proxy"
            prov="—"
            rowId="manual-credit-ebitda-proxy"
            input={
              <input
                className={inputCls}
                value={ebitdaProxy}
                onChange={(e) => setEbitdaProxy(e.target.value.replace(/[^0-9.]/g, ''))}
              />
            }
          />
        ) : null}
      </Card>

      <Card title="C. Obligations" subtitle="EMI, total obligations, and ratio.">
        <Row2
          label="Monthly obligation / EMI (total)"
          prov={pv.monthlyObligation}
          mono
          rowId="manual-credit-monthly-obligation"
          input={
            <input
              className={inputCls}
              value={monthlyObligation}
              onChange={(e) => setMonthlyObligation(e.target.value.replace(/[^0-9.]/g, ''))}
            />
          }
        />
        <Row2
          label="EMI (manual, monthly)"
          prov="—"
          rowId="manual-credit-emi-obligation"
          input={
            <input
              className={inputCls}
              value={emiObligation}
              onChange={(e) => setEmiObligation(e.target.value.replace(/[^0-9.]/g, ''))}
            />
          }
        />
        <Row2
          label="Obligation ratio % (0–100) or leave blank for system"
          prov="—"
          rowId="manual-credit-obligation-ratio"
          input={
            <input
              className={inputCls}
              value={obligationRatio}
              onChange={(e) => {
                const t = e.target.value.replace(/[^0-9.]/g, '')
                const f = t.indexOf('.')
                setObligationRatio(f === -1 ? t : t.slice(0, f + 1) + t.slice(f + 1).replace(/\./g, ''))
              }}
            />
          }
        />
      </Card>

      <Card title="D. Collateral / LAP" subtitle="Property value and LTV.">
        <Row2
          label="Property / collateral value"
          prov="—"
          rowId="manual-credit-property-value"
          input={
            <input
              className={inputCls}
              value={propertyValue}
              onChange={(e) => setPropertyValue(e.target.value.replace(/[^0-9.]/g, ''))}
            />
          }
        />
        <Row2
          label="LTV % (manual override)"
          prov="—"
          rowId="manual-credit-ltv"
          input={
            <input className={inputCls} value={ltvManual} onChange={(e) => setLtvManual(e.target.value.replace(/[^0-9.]/g, ''))} />
          }
        />
      </Card>

      {uwFields.showBusinessRiskCard ? (
        <Card title="E. Business risk" subtitle="Vintage, industry, repayment, leverage.">
          <Row2
            label="Business vintage (months)"
            prov="—"
            rowId="manual-credit-business-vintage"
            input={
              <input
                className={inputCls}
                value={businessVintageMonths}
                onChange={(e) => setBusinessVintageMonths(e.target.value.replace(/\D/g, ''))}
              />
            }
          />
          <Row2
            label="Industry risk"
            prov="—"
            rowId="manual-credit-industry-risk"
            input={
              <input
                className={inputCls}
                value={industryRisk}
                onChange={(e) => setIndustryRisk(e.target.value)}
                placeholder="LOW / MED / HIGH"
              />
            }
          />
          <Row2
            label="Repayment history"
            prov="—"
            rowId="manual-credit-repayment-history"
            input={
              <input
                className={inputCls}
                value={repaymentHistory}
                onChange={(e) => setRepaymentHistory(e.target.value)}
                placeholder="CLEAN / etc."
              />
            }
          />
          <Row2
            label="Leverage ratio"
            prov="—"
            rowId="manual-credit-leverage-ratio"
            input={
              <input
                className={inputCls}
                value={leverageRatio}
                onChange={(e) => setLeverageRatio(e.target.value.replace(/[^0-9.]/g, ''))}
              />
            }
          />
        </Card>
      ) : null}

      <Card title="F. Geography" subtitle="State / city for policy or LTV.">
        <Row2
          label="State"
          prov={pv.state}
          rowId="manual-credit-state"
          input={<input className={inputCls} value={state} onChange={(e) => setState(e.target.value)} />}
        />
        <Row2
          label="City"
          prov={pv.city}
          rowId="manual-credit-city"
          input={<input className={inputCls} value={city} onChange={(e) => setCity(e.target.value)} />}
        />
      </Card>

      <Card title="Bureau (manual) & credit remarks" subtitle="If automated bureau is not available, enter score here and attach a report.">
        <Row2
          label="Bureau score (manual)"
          prov={pv.bureauScore}
          mono
          rowId="manual-credit-bureau-score"
          input={
            <input
              className={inputCls}
              value={manualBureauScore}
              onChange={(e) => setManualBureauScore(e.target.value.replace(/\D/g, ''))}
              inputMode="numeric"
            />
          }
        />
        <Row2
          label="Bureau remarks"
          prov="—"
          input={
            <input
              className={inputCls}
              value={manualBureauRemarks}
              onChange={(e) => setManualBureauRemarks(e.target.value)}
            />
          }
        />
        <div>
          <div className="text-xs font-medium text-slate-500">Notes for the file</div>
          <textarea
            className="mt-1 bt-input w-full text-sm"
            rows={3}
            value={creditRemarks}
            onChange={(e) => setCreditRemarks(e.target.value)}
            placeholder="Rationale, caveats, or follow-ups for the credit file."
          />
        </div>
      </Card>

      <Card
        title="G. Supporting evidence"
        subtitle="Upload files; document IDs are saved with your manual input record."
      >
        {evidenceJustUploaded ? (
          <p className="text-sm text-emerald-800">
            Added to list: <span className="font-mono text-xs">{evidenceJustUploaded}</span> — press Save to persist
            links.
          </p>
        ) : null}
        <div className="flex flex-wrap items-center gap-2">
          <label className="inline-flex cursor-pointer items-center rounded border border-slate-700 bg-slate-900 px-3 py-2 text-xs font-medium text-white disabled:opacity-50">
            {evidenceUploading ? 'Uploading…' : 'Upload evidence'}
            <input
              type="file"
              className="sr-only"
              disabled={evidenceUploading}
              onChange={async (e) => {
                const file = e.target.files?.[0]
                e.target.value = ''
                if (!file) return
                setErr(null)
                setEvidenceJustUploaded(null)
                setEvidenceUploading(true)
                try {
                  const d = await uploadDocument(applicationId, file, 'MANUAL_CREDIT_EVIDENCE')
                  setSupportingDocumentIds((p) => [...p, d.id])
                  setEvidenceJustUploaded(d.fileName || d.id)
                } catch (ex) {
                  setErr(ex instanceof Error ? ex.message : 'Upload failed')
                } finally {
                  setEvidenceUploading(false)
                }
              }}
            />
          </label>
        </div>
        {supportingDocumentIds.length > 0 ? (
          <ul className="space-y-1 text-xs text-slate-700">
            {supportingDocumentIds.map((id) => (
              <li key={id} className="flex flex-wrap items-center gap-2">
                <span className="font-mono">{id}</span>
                <button
                  type="button"
                  className="text-rose-700 underline"
                  onClick={() => setSupportingDocumentIds((p) => p.filter((x) => x !== id))}
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-xs text-slate-500">No document IDs linked yet. Upload, then press Save below.</p>
        )}
      </Card>

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          disabled={saving}
          onClick={() => void onSave()}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save manual credit inputs'}
        </button>
        <span className="text-xs text-slate-500">
          {saving ? 'Save in progress…' : okMsg ? 'Last save succeeded.' : ' '}
        </span>
      </div>
    </div>
  )
}
