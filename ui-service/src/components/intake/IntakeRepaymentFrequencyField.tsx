import { repaymentFrequencyForTenureUnit } from '@/catalog/lmsTenureUnits'

/**
 * Displays Repayment Frequency next to Tenure — auto-derived from lmsTenureUnit, not
 * independently editable. The actual EMI cadence is determined by the Encore product/
 * tenure-unit combination, so a free-choice value here could describe a cadence Encore
 * cannot actually honor.
 */
export function IntakeRepaymentFrequencyField({ lmsTenureUnit }: { lmsTenureUnit: string }) {
  const value = repaymentFrequencyForTenureUnit(lmsTenureUnit)

  return (
    <label className="block text-sm text-slate-700">
      <span className="mb-1 block text-xs font-medium text-slate-500">Repayment Frequency</span>
      <input className="bt-input w-full bg-slate-50 text-slate-600" value={value} readOnly disabled />
    </label>
  )
}
