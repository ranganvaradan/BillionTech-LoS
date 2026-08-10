/** Staff intake: add / remove co-applicant rows when the active workflow enables joint applications. */
export interface CoApplicantRow {
  /** Existing `ApplicationParty` id when this co-applicant was already saved. */
  id?: string
  fullName: string
  mobile: string
  email: string
  relationship: string
  /** Extended fields when RM fills co-applicant details themselves. */
  dateOfBirth?: string
  gender?: string
  occupation?: string
  panNumber?: string
}

export type StaffMultiPartyPath = 'notify' | 'staff_fill'

function newCoApplicantRow(): CoApplicantRow {
  return { fullName: '', mobile: '', email: '', relationship: '' }
}

export function CoApplicantsSection({
  coApplicants,
  onChange,
  min,
  max,
  completionPath,
  onCompletionPathChange,
  showCompletionPathChooser = false,
}: {
  coApplicants: CoApplicantRow[]
  onChange: (rows: CoApplicantRow[]) => void
  min: number
  max: number
  completionPath?: StaffMultiPartyPath | null
  onCompletionPathChange?: (path: StaffMultiPartyPath) => void
  showCompletionPathChooser?: boolean
}) {
  function updateRow(idx: number, patch: Partial<CoApplicantRow>) {
    const next = [...coApplicants]
    next[idx] = { ...next[idx]!, ...patch }
    onChange(next)
  }

  function removeRow(idx: number) {
    onChange(coApplicants.filter((_, i) => i !== idx))
  }

  return (
    <section className="space-y-4 bt-card p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="bt-card-title">Co-applicants</h2>
        <button
          type="button"
          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
          onClick={() => onChange([...coApplicants, newCoApplicantRow()])}
          disabled={coApplicants.length >= max}
        >
          Add co-applicant
        </button>
      </div>
      <p className="text-xs text-slate-500">
        {min > 0 ? `At least ${min} co-applicant(s) required. ` : ''}
        Up to {max} co-applicant(s) allowed. Email and mobile must be different for every applicant (including the
        primary borrower).
      </p>
      {coApplicants.length === 0 ? (
        <p className="rounded border border-dashed border-slate-300 bg-slate-50 p-3 text-sm text-slate-600">
          No co-applicants added yet.
        </p>
      ) : null}
      <div className="space-y-3">
        {coApplicants.map((row, idx) => (
          <div key={row.id ?? idx} className="rounded-lg border border-slate-200 bg-white p-3">
            <div className="mb-2 flex items-center justify-between">
              <p className="text-xs font-medium uppercase text-slate-500">Co-applicant {idx + 1}</p>
              <button type="button" className="text-xs text-rose-700" onClick={() => removeRow(idx)}>
                Remove
              </button>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Full name *</span>
                <input
                  className="bt-input w-full"
                  value={row.fullName}
                  onChange={(e) => updateRow(idx, { fullName: e.target.value })}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Mobile *</span>
                <input
                  type="tel"
                  className="bt-input w-full"
                  value={row.mobile}
                  onChange={(e) => updateRow(idx, { mobile: e.target.value })}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Email *</span>
                <input
                  type="email"
                  className="bt-input w-full"
                  value={row.email}
                  onChange={(e) => updateRow(idx, { email: e.target.value })}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Relationship (optional)</span>
                <input
                  className="bt-input w-full"
                  placeholder="e.g. Spouse, Parent, Business partner"
                  value={row.relationship}
                  onChange={(e) => updateRow(idx, { relationship: e.target.value })}
                />
              </label>
            </div>
          </div>
        ))}
      </div>

      {showCompletionPathChooser && coApplicants.length > 0 && onCompletionPathChange ? (
        <div className="rounded-lg border border-indigo-200 bg-indigo-50/60 p-4 space-y-3">
          <p className="text-sm font-medium text-slate-900">How should applicants complete their details?</p>
          <p className="text-xs text-slate-600">
            Choose one path before continuing. Email and mobile must be unique across the primary borrower and every
            co-applicant.
          </p>
          <label
            className={`flex cursor-pointer items-start gap-2 rounded-md border p-3 text-sm text-slate-800 ${
              completionPath === 'notify' ? 'border-indigo-400 bg-indigo-50' : 'border-slate-200 bg-white'
            }`}
          >
            <input
              type="radio"
              className="mt-1"
              name="staff-multi-party-path"
              checked={completionPath === 'notify'}
              onChange={() => onCompletionPathChange('notify')}
            />
            <span>
              <span className="font-medium">Notify applicants (portal invite)</span>
              <span className="mt-0.5 block text-xs text-slate-500">
                After primary basics, use <strong>Save draft &amp; notify all applicants</strong>. Each person gets a
                login email and completes their own details. You do not fill co-applicant KYC here.
              </span>
            </span>
          </label>
          <label
            className={`flex cursor-pointer items-start gap-2 rounded-md border p-3 text-sm text-slate-800 ${
              completionPath === 'staff_fill' ? 'border-indigo-400 bg-indigo-50' : 'border-slate-200 bg-white'
            }`}
          >
            <input
              type="radio"
              className="mt-1"
              name="staff-multi-party-path"
              checked={completionPath === 'staff_fill'}
              onChange={() => onCompletionPathChange('staff_fill')}
            />
            <span>
              <span className="font-medium">I will fill all applicant details</span>
              <span className="mt-0.5 block text-xs text-slate-500">
                Continue the wizard for the primary borrower, then enter each co-applicant&apos;s details one by one
                before submitting. No portal invite is sent.
              </span>
            </span>
          </label>
          {!completionPath ? (
            <p className="text-xs font-medium text-amber-800">Select a completion path to enable Continue / Notify.</p>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}

/** Staff-only form to capture one co-applicant's extended details (staff_fill path). */
export function StaffCoApplicantDetailForm({
  index,
  total,
  row,
  onChange,
  collectDob = true,
  collectGender = true,
  collectOccupation = false,
  requireDob = false,
}: {
  index: number
  total: number
  row: CoApplicantRow
  onChange: (patch: Partial<CoApplicantRow>) => void
  collectDob?: boolean
  collectGender?: boolean
  collectOccupation?: boolean
  requireDob?: boolean
}) {
  return (
    <section className="space-y-4 bt-card p-5">
      <div>
        <h2 className="bt-card-title">
          Co-applicant {index + 1} of {total}
        </h2>
        <p className="mt-1 text-xs text-slate-500">
          Enter full details for this co-applicant. Contact details must stay different from the primary borrower and
          other co-applicants.
        </p>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm text-slate-700">
          <span className="mb-1 block text-xs font-medium text-slate-500">Full name *</span>
          <input
            className="bt-input w-full"
            value={row.fullName}
            onChange={(e) => onChange({ fullName: e.target.value })}
          />
        </label>
        <label className="block text-sm text-slate-700">
          <span className="mb-1 block text-xs font-medium text-slate-500">Mobile *</span>
          <input
            type="tel"
            className="bt-input w-full"
            value={row.mobile}
            onChange={(e) => onChange({ mobile: e.target.value })}
          />
        </label>
        <label className="block text-sm text-slate-700">
          <span className="mb-1 block text-xs font-medium text-slate-500">Email *</span>
          <input
            type="email"
            className="bt-input w-full"
            value={row.email}
            onChange={(e) => onChange({ email: e.target.value })}
          />
        </label>
        <label className="block text-sm text-slate-700">
          <span className="mb-1 block text-xs font-medium text-slate-500">Relationship</span>
          <input
            className="bt-input w-full"
            value={row.relationship}
            onChange={(e) => onChange({ relationship: e.target.value })}
          />
        </label>
        {collectDob ? (
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">
              Date of birth{requireDob ? ' *' : ''}
            </span>
            <input
              type="date"
              className="bt-input w-full"
              value={row.dateOfBirth ?? ''}
              onChange={(e) => onChange({ dateOfBirth: e.target.value })}
            />
          </label>
        ) : null}
        {collectGender ? (
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Gender</span>
            <select
              className="bt-input w-full"
              value={row.gender ?? ''}
              onChange={(e) => onChange({ gender: e.target.value })}
            >
              <option value="">Select</option>
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
              <option value="PREFER_NOT_TO_SAY">Prefer not to say</option>
            </select>
          </label>
        ) : null}
        {collectOccupation ? (
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Occupation</span>
            <input
              className="bt-input w-full"
              value={row.occupation ?? ''}
              onChange={(e) => onChange({ occupation: e.target.value })}
            />
          </label>
        ) : null}
        <label className="block text-sm text-slate-700">
          <span className="mb-1 block text-xs font-medium text-slate-500">PAN (optional)</span>
          <input
            className="bt-input w-full uppercase"
            value={row.panNumber ?? ''}
            onChange={(e) => onChange({ panNumber: e.target.value.toUpperCase() })}
          />
        </label>
      </div>
    </section>
  )
}
