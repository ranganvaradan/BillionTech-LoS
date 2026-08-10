import type { IntakeFormState } from '@/lib/intake/intakeTypes'

export function InvoiceDiscountingVintageFields({
  form,
  onChange,
  disabled = false,
}: {
  form: Pick<IntakeFormState, 'dependencyVintagePercent' | 'anchorRelationshipVintageMonths'>
  onChange: (patch: Partial<Pick<IntakeFormState, 'dependencyVintagePercent' | 'anchorRelationshipVintageMonths'>>) => void
  disabled?: boolean
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">
          Dependency vintage (%) *
        </span>
        <input
          type="number"
          step="0.01"
          min={0}
          className="bt-input w-full"
          value={form.dependencyVintagePercent}
          onChange={(e) => onChange({ dependencyVintagePercent: e.target.value })}
          placeholder="e.g. 12.00"
          disabled={disabled}
          required
        />
        <span className="mt-1 block text-xs text-slate-500">
          Share of your business revenue or purchases dependent on this anchor.
        </span>
      </label>
      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">
          Anchor relationship vintage (months) *
        </span>
        <input
          type="number"
          step="1"
          min={0}
          className="bt-input w-full"
          value={form.anchorRelationshipVintageMonths}
          onChange={(e) => onChange({ anchorRelationshipVintageMonths: e.target.value })}
          placeholder="e.g. 20"
          disabled={disabled}
          required
        />
        <span className="mt-1 block text-xs text-slate-500">
          How long you have been transacting with this anchor.
        </span>
      </label>
    </div>
  )
}
