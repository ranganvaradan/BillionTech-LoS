import { useMemo, useState } from 'react'
import type { IntakeDocumentSlot } from '@/lib/intake/intakeDocumentSlots'

function DocumentSlotRow({
  slot,
  uploaded,
  busy,
  onUploadFile,
}: {
  slot: IntakeDocumentSlot
  uploaded: boolean
  busy: boolean
  onUploadFile: (documentType: string, file: File | null) => void
}) {
  return (
    <li className="rounded-md border border-slate-100 bg-slate-50/80 p-4">
      <div className="mb-2 text-sm font-medium text-slate-900">
        {slot.label}
        {slot.required ? <span className="ml-1 text-xs text-red-600">Required</span> : null}
      </div>
      {slot.reason ? <p className="mb-2 text-xs text-slate-600">{slot.reason}</p> : null}
      <div className="flex flex-wrap items-center gap-3">
        <input
          type="file"
          accept=".pdf,image/*"
          className="text-sm"
          onChange={(e) => {
            const file = e.target.files?.[0] ?? null
            if (e.target) e.target.value = ''
            onUploadFile(slot.documentType, file)
          }}
          disabled={busy}
        />
        {uploaded ? (
          <span className="text-xs font-medium text-emerald-800">Received</span>
        ) : (
          <span className="text-xs text-amber-800">Not uploaded</span>
        )}
      </div>
    </li>
  )
}

/**
 * Groups intake document slots into mandatory + optional sections.
 * Optional section stays collapsed by default to reduce noise when many docs are configured.
 */
export function IntakeDocumentUploadList({
  slots,
  documentUploaded,
  busy,
  onUploadFile,
}: {
  slots: IntakeDocumentSlot[]
  documentUploaded: Record<string, boolean | undefined>
  busy?: boolean
  onUploadFile: (documentType: string, file: File | null) => void
}) {
  const [optionalOpen, setOptionalOpen] = useState(false)
  const { mandatory, optional } = useMemo(() => {
    const mandatorySlots: IntakeDocumentSlot[] = []
    const optionalSlots: IntakeDocumentSlot[] = []
    for (const slot of slots) {
      if (slot.required) mandatorySlots.push(slot)
      else optionalSlots.push(slot)
    }
    // If nothing is marked required (legacy / all-optional), keep a single flat list in the
    // mandatory section so we do not hide every upload behind a collapsed panel.
    if (mandatorySlots.length === 0 && optionalSlots.length > 0) {
      return { mandatory: optionalSlots, optional: [] as IntakeDocumentSlot[] }
    }
    return { mandatory: mandatorySlots, optional: optionalSlots }
  }, [slots])

  const optionalReceived = optional.filter((s) => documentUploaded[s.documentType]).length

  return (
    <div className="space-y-4">
      {mandatory.length > 0 ? (
        <div className="space-y-2">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-600">
            Mandatory documents
            <span className="ml-1 font-normal normal-case text-slate-500">({mandatory.length})</span>
          </h3>
          <ul className="space-y-4">
            {mandatory.map((slot) => (
              <DocumentSlotRow
                key={slot.documentType}
                slot={slot}
                uploaded={Boolean(documentUploaded[slot.documentType])}
                busy={Boolean(busy)}
                onUploadFile={onUploadFile}
              />
            ))}
          </ul>
        </div>
      ) : null}

      {optional.length > 0 ? (
        <div className="rounded-md border border-slate-200 bg-white">
          <button
            type="button"
            className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left"
            onClick={() => setOptionalOpen((v) => !v)}
            aria-expanded={optionalOpen}
          >
            <span>
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-600">
                Optional documents
              </span>
              <span className="ml-2 text-xs text-slate-500">
                {optionalReceived}/{optional.length} uploaded
              </span>
            </span>
            <span className="text-xs font-medium text-slate-700">{optionalOpen ? 'Hide' : 'Show'}</span>
          </button>
          {optionalOpen ? (
            <ul className="space-y-4 border-t border-slate-100 px-4 py-4">
              {optional.map((slot) => (
                <DocumentSlotRow
                  key={slot.documentType}
                  slot={slot}
                  uploaded={Boolean(documentUploaded[slot.documentType])}
                  busy={Boolean(busy)}
                  onUploadFile={onUploadFile}
                />
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
