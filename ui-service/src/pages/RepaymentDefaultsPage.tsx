import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  listRepaymentDefaults,
  updateRepaymentDefault,
  type LoanProductRepaymentDefaultResponse,
} from '@/api/repaymentDefaults'
import { PageHeader } from '@/components/PageHeader'
import {
  INVOICE_DISCOUNTING_PRODUCT_CODE,
  LOAN_PRODUCT_CODES,
  LOAN_PRODUCT_LABELS,
  loanProductLabel,
  type LoanProductCode,
} from '@/catalog/loanProducts'

const MECHANISM_OPTIONS = [
  { value: 'SMART_COLLECT', label: 'Smart Collect' },
  { value: 'PAYU_PG', label: 'PayU (Payment Gateway)' },
  { value: 'API_PG', label: 'API-based PG (reserved)' },
] as const

type EditState = {
  repaymentMechanism: string
  pgProviderCode: string
  enabled: boolean
}

function plpPlatformRepaymentUrl(): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  if (origin.includes('localhost')) {
    return 'http://localhost:3000/plp/repayment-defaults'
  }
  return `${origin.replace(/\/los\/?$/, '')}/plp/repayment-defaults`
}

function plpPgSettlementsUrl(): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  if (origin.includes('localhost')) {
    return 'http://localhost:3000/plp/pg-settlements'
  }
  return `${origin.replace(/\/los\/?$/, '')}/plp/pg-settlements`
}

export function RepaymentDefaultsPage() {
  const [rows, setRows] = useState<LoanProductRepaymentDefaultResponse[]>([])
  const [edits, setEdits] = useState<Record<string, EditState>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState<string | null>(null)

  const plpManagedProduct = INVOICE_DISCOUNTING_PRODUCT_CODE

  const displayProducts = useMemo(() => {
    const ordered: string[] = []
    for (const code of LOAN_PRODUCT_CODES) {
      if (code === plpManagedProduct) continue
      ordered.push(code)
    }
    for (const row of rows) {
      if (!ordered.includes(row.loanProduct) && row.loanProduct !== plpManagedProduct) {
        ordered.push(row.loanProduct)
      }
    }
    return ordered
  }, [rows, plpManagedProduct])

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await listRepaymentDefaults()
      setRows(data)
      const next: Record<string, EditState> = {}
      for (const row of data) {
        next[row.loanProduct] = {
          repaymentMechanism: row.repaymentMechanism ?? 'SMART_COLLECT',
          pgProviderCode: row.pgProviderCode ?? '',
          enabled: row.enabled !== false,
        }
      }
      setEdits(next)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load repayment defaults')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const rowFor = (code: string): EditState => {
    if (edits[code]) return edits[code]!
    const existing = rows.find((r) => r.loanProduct === code)
    return {
      repaymentMechanism: existing?.repaymentMechanism ?? 'SMART_COLLECT',
      pgProviderCode: existing?.pgProviderCode ?? '',
      enabled: existing?.enabled !== false,
    }
  }

  const save = async (loanProduct: string) => {
    const edit = edits[loanProduct] ?? rowFor(loanProduct)
    setSaving(loanProduct)
    setError('')
    try {
      await updateRepaymentDefault(loanProduct, {
        repaymentMechanism: edit.repaymentMechanism,
        pgProviderCode: edit.pgProviderCode.trim() || null,
        enabled: edit.enabled,
      })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(null)
    }
  }

  return (
    <div>
      <PageHeader
        title="Repayment defaults"
        description="Global repayment mechanism per LOS loan product (personal loan, term loan, etc.). Invoice discounting uses PLP — not configured here."
      />

      <div className="mb-6 rounded-xl border border-amber-200 bg-amber-50/80 p-5">
        <h2 className="text-sm font-semibold text-slate-800">Invoice discounting (PLP only)</h2>
        <p className="mt-2 text-sm text-slate-600">
          <strong>Business WC — Invoice Discounting</strong> repayments and PayU settlements are managed in PLP Platform
          Admin, not LOS. LOS borrower invoice discounting proxies PLP at runtime.
        </p>
        <a
          href={plpPlatformRepaymentUrl()}
          target="_blank"
          rel="noreferrer"
          className="mt-3 mr-4 inline-block text-sm font-medium text-bl-navy underline"
        >
          PLP repayment defaults
        </a>
        <a
          href={plpPgSettlementsUrl()}
          target="_blank"
          rel="noreferrer"
          className="mt-3 inline-block text-sm font-medium text-bl-navy underline"
        >
          PLP PG settlements (invoice discounting)
        </a>
      </div>

      <div className="mb-6 rounded-xl border border-amber-200 bg-amber-50/80 p-5">
        <h2 className="text-sm font-semibold text-slate-800">
          {loanProductLabel(plpManagedProduct)} (invoice discounting)
        </h2>
        <p className="mt-2 text-sm text-slate-600">
          Financed invoice repayments are served by PLP (cart / PayU). Configure the platform default under PLP
          Platform Admin → Repayment defaults → Invoice Discounting.
        </p>
        <a
          href={plpPlatformRepaymentUrl()}
          target="_blank"
          rel="noreferrer"
          className="mt-3 inline-block text-sm font-medium text-bl-navy underline"
        >
          Open PLP repayment defaults
        </a>
      </div>

      {error ? (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      ) : null}

      {loading ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs font-semibold uppercase text-slate-500">
                <th className="px-4 py-3">Loan product</th>
                <th className="px-4 py-3">Mechanism</th>
                <th className="px-4 py-3">PG provider</th>
                <th className="px-4 py-3">Enabled</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {displayProducts.map((code) => {
                const edit = rowFor(code)
                const isPg = edit.repaymentMechanism === 'PAYU_PG' || edit.repaymentMechanism === 'API_PG'
                const label =
                  code in LOAN_PRODUCT_LABELS
                    ? LOAN_PRODUCT_LABELS[code as LoanProductCode]
                    : loanProductLabel(code)
                return (
                  <tr key={code}>
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-800">{label}</div>
                      <div className="text-xs font-mono text-slate-400">{code}</div>
                    </td>
                    <td className="px-4 py-3">
                      <select
                        value={edit.repaymentMechanism}
                        onChange={(e) =>
                          setEdits((prev) => ({
                            ...prev,
                            [code]: { ...edit, repaymentMechanism: e.target.value },
                          }))
                        }
                        className="w-full max-w-xs rounded-lg border border-slate-300 px-2 py-1.5"
                      >
                        {MECHANISM_OPTIONS.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="px-4 py-3">
                      <input
                        type="text"
                        disabled={!isPg}
                        value={edit.pgProviderCode}
                        placeholder={isPg ? 'e.g. PAYU' : '—'}
                        onChange={(e) =>
                          setEdits((prev) => ({
                            ...prev,
                            [code]: { ...edit, pgProviderCode: e.target.value },
                          }))
                        }
                        className="w-full max-w-[8rem] rounded-lg border border-slate-300 px-2 py-1.5 disabled:bg-slate-50"
                      />
                    </td>
                    <td className="px-4 py-3">
                      <input
                        type="checkbox"
                        checked={edit.enabled}
                        onChange={(e) =>
                          setEdits((prev) => ({
                            ...prev,
                            [code]: { ...edit, enabled: e.target.checked },
                          }))
                        }
                      />
                    </td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        disabled={saving === code}
                        onClick={() => void save(code)}
                        className="rounded-lg bg-bl-navy px-3 py-1.5 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
                      >
                        {saving === code ? 'Saving…' : 'Save'}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
