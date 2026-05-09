import { useMemo } from 'react'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS } from '@/catalog/loanProducts'

/**
 * Structured editor for the workflow's `vkycTriggerCondition` JSON. Renders one
 * row per rule with Field + Operator + Value pickers, while emitting the exact
 * JSON shape consumed by the backend evaluator
 * ({@code VkycWorkflowService.evaluateRule}). Rules are AND-combined (the
 * existing engine does AND across rules); a friendly note documents this so
 * business users do not expect OR semantics until the engine itself supports
 * them.
 *
 * Backend payload shape (unchanged):
 *   [{ "field": "loan_amount", "operator": ">", "value": 500000 }, ...]
 *
 * Why fields are scoped:
 *   The backend `extractField` switch supports a fixed set of canonical fields
 *   plus a free-form `financialInfo` lookup for everything else. The builder
 *   exposes the canonical set as labelled options and provides a "Custom field
 *   (financialInfo key)" option so power users can still target anything that
 *   exists in `financialInfo`. Operator + value-input types are bound to the
 *   field type so an invalid combination cannot be created from the UI.
 */

type FieldType = 'number' | 'borrowerType' | 'loanProduct' | 'string' | 'custom'

type FieldDef = {
  /** Canonical field name as understood by the backend evaluator. */
  value: string
  label: string
  /** Drives operator + value input rendering and validation. */
  type: FieldType
  /** Helper text rendered under the row. */
  hint?: string
}

const FIELD_DEFS: FieldDef[] = [
  {
    value: 'loan_amount',
    label: 'Requested loan amount',
    type: 'number',
    hint: 'Compares against the application\u2019s requestedAmount.',
  },
  {
    value: 'bureau_score',
    label: 'Bureau score',
    type: 'number',
    hint: 'Compares against the latest pulled bureau score on the application.',
  },
  {
    value: 'borrower_type',
    label: 'Borrower type',
    type: 'borrowerType',
  },
  {
    value: 'loan_product',
    label: 'Loan product',
    type: 'loanProduct',
  },
  {
    value: 'risk_grade',
    label: 'Risk grade',
    type: 'string',
    hint: 'Reads financialInfo.riskGrade. Use the value your underwriting writes (e.g. A, B, C).',
  },
  {
    value: '__custom__',
    label: 'Custom field (financialInfo key)',
    type: 'custom',
    hint: 'Reads any key from the application\u2019s financialInfo JSON. Use exact casing.',
  },
]

const NUMERIC_OPERATORS = ['==', '!=', '>', '<', '>=', '<='] as const
const SET_OPERATORS = ['IN', 'NOT_IN'] as const
const STRING_OPERATORS = ['==', '!='] as const

const OPERATOR_LABELS: Record<string, string> = {
  '==': 'equals (=)',
  '!=': 'not equals (≠)',
  '>': 'greater than (>)',
  '<': 'less than (<)',
  '>=': 'greater than or equal (≥)',
  '<=': 'less than or equal (≤)',
  IN: 'is one of (IN)',
  NOT_IN: 'is not one of (NOT IN)',
}

function operatorsForFieldType(type: FieldType): readonly string[] {
  if (type === 'number') return [...NUMERIC_OPERATORS, ...SET_OPERATORS]
  if (type === 'borrowerType' || type === 'loanProduct') return [...STRING_OPERATORS, ...SET_OPERATORS]
  if (type === 'string') return [...STRING_OPERATORS, ...SET_OPERATORS]
  return [...NUMERIC_OPERATORS, ...STRING_OPERATORS, ...SET_OPERATORS]
}

/**
 * Visual-only row used by the builder. The `field` here is either a known
 * canonical key from {@link FIELD_DEFS} or, when {@code field === '__custom__'},
 * the actual financialInfo key is held in {@code customField}.
 */
export type VkycConditionRow = {
  /** Stable id used as a React key — never persisted. */
  id: string
  field: string
  customField: string
  operator: string
  /** Single-value text. Used when operator is not IN/NOT_IN. */
  value: string
  /** Comma-separated values. Used when operator is IN or NOT_IN. */
  values: string
}

function defForField(field: string): FieldDef {
  return FIELD_DEFS.find((f) => f.value === field) ?? FIELD_DEFS[0]!
}

function newRow(): VkycConditionRow {
  return {
    id: cryptoRandomId(),
    field: 'loan_amount',
    customField: '',
    operator: '==',
    value: '',
    values: '',
  }
}

function cryptoRandomId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `vk-${Math.random().toString(36).slice(2)}-${Date.now().toString(36)}`
}

function coerceNumber(raw: string): number | string {
  const t = raw.trim()
  if (t === '') return raw
  const n = Number(t)
  return Number.isFinite(n) ? n : raw
}

function splitListValues(raw: string, type: FieldType): Array<string | number> {
  const parts = raw
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
  if (type === 'number') {
    return parts.map((p) => {
      const n = Number(p)
      return Number.isFinite(n) ? n : p
    })
  }
  return parts
}

/**
 * Convert visual rows into the JSON array the backend evaluator expects.
 * Rows that fail validation are skipped silently — the page-level Save flow
 * surfaces validation errors before calling this serializer.
 */
export function rowsToJson(rows: VkycConditionRow[]): Array<Record<string, unknown>> {
  const out: Array<Record<string, unknown>> = []
  for (const r of rows) {
    const def = defForField(r.field)
    const fieldName = def.type === 'custom' ? r.customField.trim() : r.field
    if (!fieldName) continue
    if (!r.operator) continue
    const isSetOp = r.operator === 'IN' || r.operator === 'NOT_IN'
    if (isSetOp) {
      const list = splitListValues(r.values, def.type)
      if (list.length === 0) continue
      out.push({ field: fieldName, operator: r.operator, value: list })
    } else {
      const trimmed = r.value.trim()
      if (trimmed === '') continue
      const value = def.type === 'number' ? coerceNumber(trimmed) : trimmed
      out.push({ field: fieldName, operator: r.operator, value })
    }
  }
  return out
}

/** Returns a per-row validation error or {@code null} if the row is valid. */
export function validateRow(r: VkycConditionRow): string | null {
  const def = defForField(r.field)
  const fieldName = def.type === 'custom' ? r.customField.trim() : r.field
  if (!fieldName) return 'Pick a field (or enter a custom field key).'
  if (!r.operator) return 'Pick an operator.'
  const allowed = operatorsForFieldType(def.type)
  if (!allowed.includes(r.operator)) return `Operator ${r.operator} is not valid for ${def.label}.`
  const isSetOp = r.operator === 'IN' || r.operator === 'NOT_IN'
  if (isSetOp) {
    const list = splitListValues(r.values, def.type)
    if (list.length === 0) return 'Provide a comma-separated value list.'
    if (def.type === 'number' && list.some((v) => typeof v !== 'number')) {
      return 'All values for a numeric field must be numbers.'
    }
  } else {
    const trimmed = r.value.trim()
    if (trimmed === '') return 'Provide a value.'
    if (def.type === 'number' && !Number.isFinite(Number(trimmed))) {
      return 'Value must be a number for this field.'
    }
  }
  return null
}

/** Returns aggregated row errors keyed by row id. Empty object means all valid. */
export function validateRows(rows: VkycConditionRow[]): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const r of rows) {
    const e = validateRow(r)
    if (e) errors[r.id] = e
  }
  return errors
}

/**
 * Parses an existing backend payload into editor rows. Tolerant to legacy /
 * partial shapes — values are stringified back into the simple text inputs so
 * users can fix them in place. {@code IN}/{@code NOT_IN} arrays are
 * comma-joined.
 */
export function jsonToRows(value: unknown): VkycConditionRow[] {
  if (!Array.isArray(value)) return []
  const out: VkycConditionRow[] = []
  for (const raw of value) {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) continue
    const obj = raw as Record<string, unknown>
    const fieldRaw = String(obj.field ?? '').trim()
    if (!fieldRaw) continue
    const knownField = FIELD_DEFS.find((d) => d.value === fieldRaw && d.type !== 'custom')
    const isCustom = !knownField
    const operator = String(obj.operator ?? '==').toUpperCase()
    const isSetOp = operator === 'IN' || operator === 'NOT_IN'
    const row: VkycConditionRow = {
      id: cryptoRandomId(),
      field: isCustom ? '__custom__' : fieldRaw,
      customField: isCustom ? fieldRaw : '',
      operator,
      value: isSetOp ? '' : stringifyValue(obj.value),
      values: isSetOp ? stringifyListValue(obj.value) : '',
    }
    out.push(row)
  }
  return out
}

function stringifyValue(v: unknown): string {
  if (v == null) return ''
  if (Array.isArray(v)) return v.map((x) => String(x)).join(', ')
  return String(v)
}

function stringifyListValue(v: unknown): string {
  if (Array.isArray(v)) return v.map((x) => String(x)).join(', ')
  if (v == null || v === '') return ''
  return String(v)
}

export function VkycConditionBuilder({
  rows,
  onChange,
}: {
  rows: VkycConditionRow[]
  onChange: (next: VkycConditionRow[]) => void
}) {
  const errors = useMemo(() => validateRows(rows), [rows])

  function update(id: string, patch: Partial<VkycConditionRow>): void {
    onChange(rows.map((r) => (r.id === id ? { ...r, ...patch } : r)))
  }

  function onFieldChange(id: string, nextField: string): void {
    const def = defForField(nextField)
    const allowed = operatorsForFieldType(def.type)
    onChange(
      rows.map((r) => {
        if (r.id !== id) return r
        const operator = allowed.includes(r.operator) ? r.operator : (allowed[0] ?? '==')
        return {
          ...r,
          field: nextField,
          customField: nextField === '__custom__' ? r.customField : '',
          operator,
        }
      }),
    )
  }

  function onAdd(): void {
    onChange([...rows, newRow()])
  }
  function onRemove(id: string): void {
    onChange(rows.filter((r) => r.id !== id))
  }

  return (
    <div className="space-y-2">
      {rows.length === 0 ? (
        <p className="text-xs text-slate-500">
          No trigger conditions configured. VKYC will run for every application that reaches this workflow position.
        </p>
      ) : null}
      <ul className="space-y-2">
        {rows.map((r, idx) => {
          const def = defForField(r.field)
          const operators = operatorsForFieldType(def.type)
          const isSetOp = r.operator === 'IN' || r.operator === 'NOT_IN'
          const error = errors[r.id]
          return (
            <li key={r.id} className="rounded-md border border-slate-200 bg-white p-2">
              <div className="grid gap-2 sm:grid-cols-12 sm:items-end">
                <div className="sm:col-span-1 sm:pb-2">
                  <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-700">
                    {idx === 0 ? 'WHERE' : 'AND'}
                  </span>
                </div>
                <label className="block sm:col-span-3">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Field</span>
                  <select
                    className="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
                    value={r.field}
                    onChange={(e) => onFieldChange(r.id, e.target.value)}
                  >
                    {FIELD_DEFS.map((d) => (
                      <option key={d.value} value={d.value}>
                        {d.label}
                      </option>
                    ))}
                  </select>
                </label>
                {def.type === 'custom' ? (
                  <label className="block sm:col-span-3">
                    <span className="mb-1 block text-xs font-medium text-slate-500">financialInfo key</span>
                    <input
                      className="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
                      value={r.customField}
                      onChange={(e) => update(r.id, { customField: e.target.value })}
                      placeholder="e.g. employmentType"
                    />
                  </label>
                ) : null}
                <label className={`block ${def.type === 'custom' ? 'sm:col-span-2' : 'sm:col-span-3'}`}>
                  <span className="mb-1 block text-xs font-medium text-slate-500">Operator</span>
                  <select
                    className="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
                    value={r.operator}
                    onChange={(e) => update(r.id, { operator: e.target.value })}
                  >
                    {operators.map((op) => (
                      <option key={op} value={op}>
                        {OPERATOR_LABELS[op] ?? op}
                      </option>
                    ))}
                  </select>
                </label>
                <div className={`block ${def.type === 'custom' ? 'sm:col-span-2' : 'sm:col-span-4'}`}>
                  {isSetOp ? (
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Values (comma-separated)</span>
                      <ValuesInput
                        type={def.type}
                        value={r.values}
                        onChange={(next) => update(r.id, { values: next })}
                      />
                    </label>
                  ) : (
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Value</span>
                      <ValueInput
                        type={def.type}
                        value={r.value}
                        onChange={(next) => update(r.id, { value: next })}
                      />
                    </label>
                  )}
                </div>
                <div className="sm:col-span-1 sm:pb-1">
                  <button
                    type="button"
                    onClick={() => onRemove(r.id)}
                    className="rounded border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700 hover:bg-slate-50"
                    aria-label="Remove condition"
                  >
                    Remove
                  </button>
                </div>
              </div>
              {def.hint ? <p className="mt-1 text-xs text-slate-500">{def.hint}</p> : null}
              {error ? (
                <p className="mt-1 text-xs text-amber-800" role="alert">
                  {error}
                </p>
              ) : null}
            </li>
          )
        })}
      </ul>
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={onAdd}
          className="rounded border border-slate-300 bg-white px-2 py-1 text-sm text-slate-800 hover:bg-slate-50"
        >
          + Add condition
        </button>
        <span className="text-xs text-slate-500">
          All conditions must match (AND). Leave empty to trigger VKYC for every application that reaches this position.
        </span>
      </div>
    </div>
  )
}

function ValueInput({
  type,
  value,
  onChange,
}: {
  type: FieldType
  value: string
  onChange: (next: string) => void
}) {
  if (type === 'borrowerType') {
    return (
      <select
        className="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">Select borrower type…</option>
        {BORROWER_TYPE_ORDER.map((bt) => (
          <option key={bt} value={bt}>
            {BORROWER_TYPE_LABELS[bt]}
          </option>
        ))}
      </select>
    )
  }
  if (type === 'loanProduct') {
    return (
      <select
        className="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">Select loan product…</option>
        {LOAN_PRODUCT_CODES.map((c) => (
          <option key={c} value={c}>
            {LOAN_PRODUCT_LABELS[c]}
          </option>
        ))}
      </select>
    )
  }
  if (type === 'number') {
    return (
      <input
        type="number"
        inputMode="decimal"
        className="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="e.g. 500000"
      />
    )
  }
  return (
    <input
      className="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder="value"
    />
  )
}

function ValuesInput({
  type,
  value,
  onChange,
}: {
  type: FieldType
  value: string
  onChange: (next: string) => void
}) {
  let placeholder = 'value1, value2'
  if (type === 'number') placeholder = '100000, 250000, 500000'
  else if (type === 'borrowerType') placeholder = `${BORROWER_TYPE_ORDER[0]}, ${BORROWER_TYPE_ORDER[1]}`
  else if (type === 'loanProduct') placeholder = `${LOAN_PRODUCT_CODES[0]}, ${LOAN_PRODUCT_CODES[1]}`
  return (
    <input
      className="w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
    />
  )
}

/** Convenience: generates a default starting row when initialising a new form. */
export function defaultConditionRow(): VkycConditionRow {
  return newRow()
}
