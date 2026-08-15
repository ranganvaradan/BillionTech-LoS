import { useEffect, useState } from 'react'
import type {
  WorkflowCodedOption,
  WorkflowCustomFieldConfig,
  WorkflowIntakeConfig,
  WorkflowMandatoryFieldGroup,
  WorkflowStandaloneDocument,
} from '@/types/workflow'
import type { VisualWorkflowStep } from '@/lib/workflowVisual'
import { KYC_IDENTITY_WORKFLOW_STEPS } from '@/lib/workflowVisual'
import { DEFAULT_LOAN_PURPOSE_OPTIONS, DEFAULT_OCCUPATION_OPTIONS } from '@/lib/intake/intakeOptionCatalogs'
import { defaultWorkflowDrivenIntakeConfig, newMandatoryGroup, newStandaloneDocument } from '@/lib/workflow/workflowIntakeRules'
import { ensureGeoStatesLoaded } from '@/lib/intake/masterGeoClientCache'
import type { GeoStateRow } from '@/api/geoMaster'

function CodedOptionsEditor({
  title,
  description,
  options,
  onChange,
}: {
  title: string
  description: string
  options: WorkflowCodedOption[]
  onChange: (options: WorkflowCodedOption[]) => void
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
      <h3 className="text-sm font-medium text-slate-800">{title}</h3>
      <p className="mt-1 text-xs text-slate-500">{description}</p>
      <div className="mt-2 space-y-2">
        {options.map((opt, idx) => (
          <div key={`${opt.value}-${idx}`} className="flex flex-wrap items-end gap-2">
            <label className="block text-xs text-slate-600">
              Label
              <input
                className="mt-0.5 block w-44 rounded border border-slate-300 px-2 py-1 text-sm"
                value={opt.label}
                onChange={(e) => {
                  const next = [...options]
                  next[idx] = { ...opt, label: e.target.value }
                  onChange(next)
                }}
              />
            </label>
            <label className="block text-xs text-slate-600">
              Code
              <input
                className="mt-0.5 block w-40 rounded border border-slate-300 px-2 py-1 text-sm font-mono"
                value={opt.value}
                onChange={(e) => {
                  const next = [...options]
                  next[idx] = { ...opt, value: e.target.value }
                  onChange(next)
                }}
              />
            </label>
            <button
              type="button"
              className="text-xs text-rose-700"
              onClick={() => onChange(options.filter((_, i) => i !== idx))}
            >
              Remove
            </button>
          </div>
        ))}
        <button
          type="button"
          className="rounded border border-slate-300 bg-white px-2 py-1 text-xs"
          onClick={() =>
            onChange([
              ...options,
              { value: `OPTION_${options.length + 1}`, label: 'New option' },
            ])
          }
        >
          Add option
        </button>
      </div>
    </section>
  )
}

function CustomFieldsEditor({
  fields,
  onChange,
}: {
  fields: WorkflowCustomFieldConfig[]
  onChange: (fields: WorkflowCustomFieldConfig[]) => void
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
      <h3 className="text-sm font-medium text-slate-800">Custom intake fields</h3>
      <p className="mt-1 text-xs text-slate-500">
        Add workflow-specific borrower inputs. Values are stored under personalInfo.customFields and can be reused in
        underwriting.
      </p>
      <div className="mt-3 space-y-3">
        {fields.map((field, idx) => (
          <div key={`${field.key}-${idx}`} className="rounded border border-slate-200 bg-white p-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-xs text-slate-600">
                Key
                <input
                  className="mt-0.5 block w-full rounded border border-slate-300 px-2 py-1 text-sm font-mono"
                  value={field.key}
                  onChange={(e) => {
                    const next = [...fields]
                    next[idx] = { ...field, key: e.target.value }
                    onChange(next)
                  }}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Label
                <input
                  className="mt-0.5 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
                  value={field.label}
                  onChange={(e) => {
                    const next = [...fields]
                    next[idx] = { ...field, label: e.target.value }
                    onChange(next)
                  }}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Type
                <select
                  className="mt-0.5 block w-full rounded border border-slate-300 bg-white px-2 py-1 text-sm"
                  value={field.type}
                  onChange={(e) => {
                    const next = [...fields]
                    next[idx] = { ...field, type: e.target.value as WorkflowCustomFieldConfig['type'] }
                    onChange(next)
                  }}
                >
                  <option value="TEXT">Text</option>
                  <option value="NUMBER">Number</option>
                  <option value="BOOLEAN">Boolean</option>
                </select>
              </label>
              <label className="block text-xs text-slate-600">
                Default value
                <input
                  className="mt-0.5 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
                  value={String(field.defaultValue ?? '')}
                  onChange={(e) => {
                    const next = [...fields]
                    next[idx] = { ...field, defaultValue: e.target.value }
                    onChange(next)
                  }}
                />
              </label>
            </div>
            <label className="mt-3 block text-xs text-slate-600">
              Help text
              <input
                className="mt-0.5 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
                value={field.helpText ?? ''}
                onChange={(e) => {
                  const next = [...fields]
                  next[idx] = { ...field, helpText: e.target.value }
                  onChange(next)
                }}
              />
            </label>
            <div className="mt-3 flex flex-wrap gap-4 text-xs text-slate-600">
              <label className="inline-flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={field.required === true}
                  onChange={(e) => {
                    const next = [...fields]
                    next[idx] = { ...field, required: e.target.checked }
                    onChange(next)
                  }}
                />
                Required
              </label>
              <label className="inline-flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={field.usedForUnderwriting === true}
                  onChange={(e) => {
                    const next = [...fields]
                    next[idx] = { ...field, usedForUnderwriting: e.target.checked }
                    onChange(next)
                  }}
                />
                Used for underwriting
              </label>
              <button
                type="button"
                className="text-rose-700"
                onClick={() => onChange(fields.filter((_, i) => i !== idx))}
              >
                Remove
              </button>
            </div>
          </div>
        ))}
        <button
          type="button"
          className="rounded border border-slate-300 bg-white px-2 py-1 text-xs"
          onClick={() =>
            onChange([
              ...fields,
              { key: `customField${fields.length + 1}`, label: 'New custom field', type: 'TEXT', required: false },
            ])
          }
        >
          Add custom field
        </button>
      </div>
    </section>
  )
}

function intakeSummary(config: WorkflowIntakeConfig): string {
  const parts: string[] = []
  if (config.policy === 'WORKFLOW_DRIVEN') {
    parts.push('Workflow-driven')
  } else {
    parts.push('Legacy')
  }
  if (config.ageRules?.enabled) {
    parts.push(`Age ${config.ageRules.minAge ?? '?'}-${config.ageRules.maxAge ?? '?'}`)
  }
  if (config.tenureRules?.inputMode === 'dropdown') {
    parts.push('Tenure: dropdown')
  }
  if ((config.mandatoryFieldGroups?.length ?? 0) > 0) {
    parts.push(`${config.mandatoryFieldGroups?.length} OR group(s)`)
  }
  const states = (config.allowedStates ?? []).map((s) => String(s).trim()).filter(Boolean)
  if (states.length > 0 && !(states.length === 1 && states[0].toUpperCase() === 'ALL')) {
    parts.push(`${states.length} state(s)`)
  } else {
    parts.push('All states')
  }
  return parts.join(' · ')
}

export function WorkflowIntakeRulesPanel({
  intakeConfig,
  onChange,
  bureauEnabled,
  autoPullBureauAfterKycSuccess,
  onBureauEnabledChange,
  onAutoPullBureauAfterKycSuccessChange,
  visualSteps,
  hideBureauRequirementControls = false,
}: {
  intakeConfig: WorkflowIntakeConfig
  onChange: (next: WorkflowIntakeConfig) => void
  bureauEnabled: boolean
  autoPullBureauAfterKycSuccess: boolean
  onBureauEnabledChange: (next: boolean) => void
  onAutoPullBureauAfterKycSuccessChange: (next: boolean) => void
  visualSteps: VisualWorkflowStep[]
  /** Client lender UX: Policy/W4/W6 own Bureau data requirements — not Workflow. */
  hideBureauRequirementControls?: boolean
}) {
  const configuredSteps = visualSteps.map((s) => s.step)
  const [geoStates, setGeoStates] = useState<GeoStateRow[]>([])
  const [geoStatesErr, setGeoStatesErr] = useState<string | null>(null)

  useEffect(() => {
    let cancel = false
    ensureGeoStatesLoaded()
      .then((rows) => {
        if (!cancel) setGeoStates([...rows])
      })
      .catch(() => {
        if (!cancel) setGeoStatesErr('Unable to load Indian states for configuration.')
      })
    return () => {
      cancel = true
    }
  }, [])

  function patch(partial: Partial<WorkflowIntakeConfig>) {
    onChange({ ...intakeConfig, ...partial })
  }

  const configuredStates = (intakeConfig.allowedStates ?? [])
    .map((s) => String(s).trim())
    .filter(Boolean)
  const restrictStates =
    configuredStates.length > 0 &&
    !(configuredStates.length === 1 && configuredStates[0].toUpperCase() === 'ALL')
  const selectedStateSet = new Set(configuredStates.map((s) => s.toLowerCase()))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <span
          className={
            intakeConfig.policy === 'WORKFLOW_DRIVEN'
              ? 'bt-badge bt-badge-green'
              : 'bt-badge bt-badge-gray'
          }
        >
          {intakeConfig.policy === 'WORKFLOW_DRIVEN' ? 'Workflow-driven intake' : 'Legacy intake'}
        </span>
        <span className="text-xs text-slate-500">{intakeSummary(intakeConfig)}</span>
      </div>

      {!hideBureauRequirementControls ? (
      <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
        <h3 className="text-sm font-medium text-slate-800">Bureau controls (legacy runtime)</h3>
        <p className="mt-1 text-xs text-slate-500">
          Transitional compatibility. Preferred model: Policy requires Bureau parameters → W4 plans → W6 acquires.
        </p>
        <div className="mt-3 space-y-3">
          <label className="flex items-start gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              className="mt-1 rounded border-slate-300"
              checked={bureauEnabled}
              onChange={(e) => onBureauEnabledChange(e.target.checked)}
            />
            <span>
              <span className="font-medium text-slate-800">Enable bureau step (legacy)</span>
              <span className="mt-0.5 block text-xs text-slate-500">
                When off, this workflow skips and blocks bureau pull execution entirely.
              </span>
            </span>
          </label>
          <label className="flex items-start gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              className="mt-1 rounded border-slate-300"
              checked={autoPullBureauAfterKycSuccess}
              onChange={(e) => onAutoPullBureauAfterKycSuccessChange(e.target.checked)}
              disabled={!bureauEnabled}
            />
            <span>
              <span className="font-medium text-slate-800">Auto-pull after KYC success (legacy)</span>
              <span className="mt-0.5 block text-xs text-slate-500">
                When off, users can still trigger bureau manually later in the flow.
              </span>
            </span>
          </label>
        </div>
      </section>
      ) : (
        <section className="rounded-lg border border-emerald-200 bg-emerald-50/60 p-3 text-xs text-emerald-950">
          <p className="font-medium">Bureau & automatic sources</p>
          <p className="mt-1">
            Data requirements come from Policy. The platform plans fulfilment (W4) and acquires sources (W6). This
            Workflow does not independently require Bureau.
          </p>
        </section>
      )}

      <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
        <h3 className="text-sm font-medium text-slate-800">Allowed states</h3>
        <p className="mt-1 text-xs text-slate-500">
          Controls which Indian states appear during new application creation for this workflow. Default is all states.
        </p>
        <div className="mt-3 flex flex-wrap gap-4 text-sm text-slate-700">
          <label className="inline-flex items-center gap-2">
            <input
              type="radio"
              name="workflow-allowed-states-mode"
              checked={!restrictStates}
              onChange={() => patch({ allowedStates: [] })}
            />
            All states
          </label>
          <label className="inline-flex items-center gap-2">
            <input
              type="radio"
              name="workflow-allowed-states-mode"
              checked={restrictStates}
              onChange={() =>
                patch({
                  allowedStates: restrictStates
                    ? configuredStates
                    : geoStates.slice(0, 1).map((s) => s.stateName),
                })
              }
            />
            Selected states only
          </label>
        </div>
        {restrictStates ? (
          <div className="mt-3 max-h-56 overflow-y-auto rounded border border-slate-200 bg-white p-2">
            {geoStatesErr ? <p className="text-xs text-amber-800">{geoStatesErr}</p> : null}
            {geoStates.length === 0 && !geoStatesErr ? (
              <p className="text-xs text-slate-500">Loading states…</p>
            ) : (
              <div className="grid gap-1 sm:grid-cols-2">
                {geoStates.map((st) => {
                  const checked = selectedStateSet.has(st.stateName.toLowerCase())
                  return (
                    <label key={st.id} className="flex items-center gap-2 text-xs text-slate-700">
                      <input
                        type="checkbox"
                        className="rounded border-slate-300"
                        checked={checked}
                        onChange={(e) => {
                          const next = new Set(configuredStates)
                          if (e.target.checked) {
                            next.add(st.stateName)
                          } else {
                            for (const cur of [...next]) {
                              if (cur.toLowerCase() === st.stateName.toLowerCase()) next.delete(cur)
                            }
                          }
                          patch({ allowedStates: [...next] })
                        }}
                      />
                      {st.stateName}
                    </label>
                  )
                })}
              </div>
            )}
            {restrictStates && configuredStates.length === 0 ? (
              <p className="mt-2 text-xs text-amber-800">Select at least one state, or switch back to All states.</p>
            ) : null}
          </div>
        ) : null}
      </section>

      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">Intake policy</span>
        <select
          className="bt-input w-full max-w-md"
          value={intakeConfig.policy ?? 'LEGACY'}
          onChange={(e) => {
            const policy = e.target.value as 'LEGACY' | 'WORKFLOW_DRIVEN'
            if (policy === 'WORKFLOW_DRIVEN') {
              onChange({
                ...intakeConfig,
                ...defaultWorkflowDrivenIntakeConfig(),
                policy: 'WORKFLOW_DRIVEN',
                allowedStates: intakeConfig.allowedStates,
              })
            } else {
              onChange({ ...intakeConfig, policy: 'LEGACY' })
            }
          }}
        >
          <option value="LEGACY">Legacy (existing hardcoded intake rules)</option>
          <option value="WORKFLOW_DRIVEN">Workflow-driven (KYC steps gate fields & documents)</option>
        </select>
      </label>

      {intakeConfig.policy !== 'WORKFLOW_DRIVEN' ? (
        <p className="text-sm text-slate-600">
          Legacy workflows keep today&apos;s intake behavior. Switch to workflow-driven when you are ready to configure
          fields, documents, age, and tenure from this workflow.
        </p>
      ) : (
        <>
          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <h3 className="text-sm font-medium text-slate-800">Personal fields</h3>
            <div className="mt-2 grid gap-3 sm:grid-cols-2">
              {(['dateOfBirth', 'gender', 'occupation', 'loanPurpose'] as const).map((field) => {
                const cfg = intakeConfig.personalFields?.[field] ?? {}
                const fieldLabel =
                  field === 'dateOfBirth'
                    ? 'Date of birth'
                    : field === 'gender'
                      ? 'Gender'
                      : field === 'occupation'
                        ? 'Occupation'
                        : 'Loan purpose'
                return (
                  <div key={field} className="rounded border border-slate-200 bg-white p-2">
                    <p className="text-xs font-medium capitalize text-slate-700">{fieldLabel}</p>
                    <label className="mt-1 flex items-center gap-2 text-xs text-slate-600">
                      <input
                        type="checkbox"
                        className="rounded border-slate-300"
                        checked={cfg.collect === true}
                        onChange={(e) =>
                          patch({
                            personalFields: {
                              ...intakeConfig.personalFields,
                              [field]: { ...cfg, collect: e.target.checked },
                            },
                          })
                        }
                      />
                      Collect at intake
                    </label>
                    <label className="mt-1 flex items-center gap-2 text-xs text-slate-600">
                      <input
                        type="checkbox"
                        className="rounded border-slate-300"
                        checked={cfg.required === true}
                        disabled={!cfg.collect}
                        onChange={(e) =>
                          patch({
                            personalFields: {
                              ...intakeConfig.personalFields,
                              [field]: { ...cfg, required: e.target.checked },
                            },
                          })
                        }
                      />
                      Required
                    </label>
                  </div>
                )
              })}
            </div>
          </section>

          <CustomFieldsEditor
            fields={intakeConfig.customFields ?? []}
            onChange={(customFields) => patch({ customFields })}
          />

          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <h3 className="text-sm font-medium text-slate-800">Age rules</h3>
            <label className="mt-2 flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                className="rounded border-slate-300"
                checked={intakeConfig.ageRules?.enabled === true}
                onChange={(e) =>
                  patch({
                    ageRules: {
                      ...intakeConfig.ageRules,
                      enabled: e.target.checked,
                      minAge: intakeConfig.ageRules?.minAge ?? 21,
                      maxAge: intakeConfig.ageRules?.maxAge ?? 65,
                    },
                  })
                }
              />
              Validate min / max age from date of birth
            </label>
            {intakeConfig.ageRules?.enabled ? (
              <div className="mt-2 flex flex-wrap gap-3">
                <label className="block text-xs text-slate-600">
                  Min age
                  <input
                    type="number"
                    className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                    value={intakeConfig.ageRules?.minAge ?? 21}
                    onChange={(e) =>
                      patch({
                        ageRules: {
                          ...intakeConfig.ageRules,
                          enabled: true,
                          minAge: Number.parseInt(e.target.value, 10) || 0,
                        },
                      })
                    }
                  />
                </label>
                <label className="block text-xs text-slate-600">
                  Max age
                  <input
                    type="number"
                    className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                    value={intakeConfig.ageRules?.maxAge ?? 65}
                    onChange={(e) =>
                      patch({
                        ageRules: {
                          ...intakeConfig.ageRules,
                          enabled: true,
                          maxAge: Number.parseInt(e.target.value, 10) || 0,
                        },
                      })
                    }
                  />
                </label>
              </div>
            ) : null}
          </section>

          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <h3 className="text-sm font-medium text-slate-800">Tenure</h3>
            <label className="mt-2 block text-xs text-slate-600">
              Input mode
              <select
                className="mt-0.5 block w-full max-w-xs rounded border border-slate-300 bg-white px-2 py-1 text-sm"
                value={intakeConfig.tenureRules?.inputMode ?? 'numeric'}
                onChange={(e) =>
                  patch({
                    tenureRules: {
                      ...intakeConfig.tenureRules,
                      inputMode: e.target.value as 'numeric' | 'dropdown',
                    },
                  })
                }
              >
                <option value="numeric">Free numeric input (min / max)</option>
                <option value="dropdown">Dropdown of preset values</option>
              </select>
            </label>
            {intakeConfig.tenureRules?.inputMode !== 'dropdown' ? (
              <div className="mt-2 flex flex-wrap gap-3">
                <label className="block text-xs text-slate-600">
                  Min
                  <input
                    type="number"
                    className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                    value={intakeConfig.tenureRules?.min ?? 1}
                    onChange={(e) =>
                      patch({
                        tenureRules: {
                          ...intakeConfig.tenureRules,
                          min: Number.parseInt(e.target.value, 10) || 0,
                        },
                      })
                    }
                  />
                </label>
                <label className="block text-xs text-slate-600">
                  Max
                  <input
                    type="number"
                    className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                    value={intakeConfig.tenureRules?.max ?? 360}
                    onChange={(e) =>
                      patch({
                        tenureRules: {
                          ...intakeConfig.tenureRules,
                          max: Number.parseInt(e.target.value, 10) || 0,
                        },
                      })
                    }
                  />
                </label>
              </div>
            ) : (
              <div className="mt-2 space-y-2">
                {(intakeConfig.tenureRules?.options ?? []).map((opt, idx) => (
                  <div key={idx} className="flex flex-wrap items-end gap-2">
                    <label className="block text-xs text-slate-600">
                      Label
                      <input
                        className="mt-0.5 block w-40 rounded border border-slate-300 px-2 py-1 text-sm"
                        value={opt.label}
                        onChange={(e) => {
                          const options = [...(intakeConfig.tenureRules?.options ?? [])]
                          options[idx] = { ...opt, label: e.target.value }
                          patch({ tenureRules: { ...intakeConfig.tenureRules, options } })
                        }}
                      />
                    </label>
                    <label className="block text-xs text-slate-600">
                      Value
                      <input
                        className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                        value={opt.value}
                        onChange={(e) => {
                          const options = [...(intakeConfig.tenureRules?.options ?? [])]
                          options[idx] = { ...opt, value: e.target.value }
                          patch({ tenureRules: { ...intakeConfig.tenureRules, options } })
                        }}
                      />
                    </label>
                    <button
                      type="button"
                      className="text-xs text-rose-700"
                      onClick={() => {
                        const options = (intakeConfig.tenureRules?.options ?? []).filter((_, i) => i !== idx)
                        patch({ tenureRules: { ...intakeConfig.tenureRules, options } })
                      }}
                    >
                      Remove
                    </button>
                  </div>
                ))}
                <button
                  type="button"
                  className="rounded border border-slate-300 bg-white px-2 py-1 text-xs"
                  onClick={() =>
                    patch({
                      tenureRules: {
                        ...intakeConfig.tenureRules,
                        inputMode: 'dropdown',
                        options: [
                          ...(intakeConfig.tenureRules?.options ?? []),
                          { value: '90', label: '90 Days', unit: 'Day' },
                        ],
                      },
                    })
                  }
                >
                  Add tenure option
                </button>
              </div>
            )}
          </section>

          <CodedOptionsEditor
            title="Occupation options"
            description="Allowed dropdown values for occupation at intake. Scores are configured on underwriting scorecards."
            options={intakeConfig.occupationRules?.options ?? DEFAULT_OCCUPATION_OPTIONS}
            onChange={(options) => patch({ occupationRules: { options } })}
          />

          <CodedOptionsEditor
            title="Loan purpose options"
            description="Allowed dropdown values for loan purpose at intake. Scores are configured on underwriting scorecards."
            options={intakeConfig.loanPurposeRules?.options ?? DEFAULT_LOAN_PURPOSE_OPTIONS}
            onChange={(options) => patch({ loanPurposeRules: { options } })}
          />

          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-medium text-slate-800">Mandatory OR groups</h3>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs"
                onClick={() =>
                  patch({
                    mandatoryFieldGroups: [...(intakeConfig.mandatoryFieldGroups ?? []), newMandatoryGroup()],
                  })
                }
              >
                Add group
              </button>
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Example: Aadhaar OR Voter ID OR Driving licence — at least one must be provided.
            </p>
            {(intakeConfig.mandatoryFieldGroups ?? []).map((group, gIdx) => (
              <MandatoryGroupRow
                key={group.id}
                group={group}
                configuredSteps={configuredSteps}
                onChange={(next) => {
                  const groups = [...(intakeConfig.mandatoryFieldGroups ?? [])]
                  groups[gIdx] = next
                  patch({ mandatoryFieldGroups: groups })
                }}
                onRemove={() => {
                  const groups = (intakeConfig.mandatoryFieldGroups ?? []).filter((_, i) => i !== gIdx)
                  patch({ mandatoryFieldGroups: groups })
                }}
              />
            ))}
          </section>

          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-medium text-slate-800">Standalone documents</h3>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs"
                onClick={() =>
                  patch({
                    standaloneDocuments: [...(intakeConfig.standaloneDocuments ?? []), newStandaloneDocument()],
                  })
                }
              >
                Add document
              </button>
            </div>
            {(intakeConfig.standaloneDocuments ?? []).map((doc, dIdx) => (
              <StandaloneDocRow
                key={`${doc.documentType}-${dIdx}`}
                doc={doc}
                onChange={(next) => {
                  const docs = [...(intakeConfig.standaloneDocuments ?? [])]
                  docs[dIdx] = next
                  patch({ standaloneDocuments: docs })
                }}
                onRemove={() => {
                  const docs = (intakeConfig.standaloneDocuments ?? []).filter((_, i) => i !== dIdx)
                  patch({ standaloneDocuments: docs })
                }}
              />
            ))}
          </section>
        </>
      )}
    </div>
  )
}

function MandatoryGroupRow({
  group,
  configuredSteps,
  onChange,
  onRemove,
}: {
  group: WorkflowMandatoryFieldGroup
  configuredSteps: string[]
  onChange: (g: WorkflowMandatoryFieldGroup) => void
  onRemove: () => void
}) {
  const pool = [...new Set([...configuredSteps, ...group.steps])].filter(Boolean)
  return (
    <div className="mt-2 rounded border border-slate-200 bg-white p-2">
      <div className="flex flex-wrap items-center gap-2">
        <input
          className="min-w-0 flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
          placeholder="Group label (e.g. Government ID — any one)"
          value={group.label}
          onChange={(e) => onChange({ ...group, label: e.target.value })}
        />
        <button type="button" className="text-xs text-rose-700" onClick={onRemove}>
          Remove
        </button>
      </div>
      <div className="mt-2 flex flex-wrap gap-2">
        {pool.map((step) => {
          const checked = group.steps.includes(step)
          if (!KYC_IDENTITY_WORKFLOW_STEPS.includes(step as (typeof KYC_IDENTITY_WORKFLOW_STEPS)[number])) {
            return null
          }
          return (
            <label key={step} className="flex items-center gap-1 rounded border border-slate-200 bg-slate-50 px-2 py-1 text-xs">
              <input
                type="checkbox"
                className="rounded border-slate-300"
                checked={checked}
                onChange={(e) => {
                  const steps = e.target.checked
                    ? [...group.steps, step]
                    : group.steps.filter((s) => s !== step)
                  onChange({ ...group, steps })
                }}
              />
              {step}
            </label>
          )
        })}
      </div>
    </div>
  )
}

function StandaloneDocRow({
  doc,
  onChange,
  onRemove,
}: {
  doc: WorkflowStandaloneDocument
  onChange: (d: WorkflowStandaloneDocument) => void
  onRemove: () => void
}) {
  return (
    <div className="mt-2 flex flex-wrap items-center gap-2 rounded border border-slate-200 bg-white p-2">
      <input
        className="w-36 rounded border border-slate-300 px-2 py-1 text-sm"
        value={doc.documentType}
        onChange={(e) => onChange({ ...doc, documentType: e.target.value.toUpperCase() })}
      />
      <input
        className="min-w-0 flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
        placeholder="Label"
        value={doc.label ?? ''}
        onChange={(e) => onChange({ ...doc, label: e.target.value })}
      />
      <label className="flex items-center gap-1 text-xs text-slate-600">
        <input
          type="checkbox"
          className="rounded border-slate-300"
          checked={doc.required === true}
          onChange={(e) => onChange({ ...doc, required: e.target.checked })}
        />
        Required
      </label>
      <button type="button" className="text-xs text-rose-700" onClick={onRemove}>
        Remove
      </button>
    </div>
  )
}
