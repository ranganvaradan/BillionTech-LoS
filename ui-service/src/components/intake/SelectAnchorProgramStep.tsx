import { useEffect, useMemo, useState } from 'react'
import { listSelectablePlpPrograms } from '@/api/plp'
import { ApiError } from '@/api/http'
import type { PlpProgramSummary } from '@/types/plp'
import { intakeStepSectionClass } from '@/lib/intake/intakeStepLayout'

export function SelectAnchorProgramStep({
  selectedSubProgramId,
  onSelect,
}: {
  selectedSubProgramId: string
  onSelect: (subProgramId: string, summary: PlpProgramSummary) => void
}) {
  const [programs, setPrograms] = useState<PlpProgramSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [anchorFilter, setAnchorFilter] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    void listSelectablePlpPrograms()
      .then((data) => {
        if (!cancelled) setPrograms(data)
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof ApiError ? e.message : 'Could not load PLP programs.')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const anchors = useMemo(() => {
    const map = new Map<string, string>()
    for (const p of programs) {
      if (p.anchorId && p.anchorName) {
        map.set(p.anchorId, p.anchorName)
      }
    }
    return [...map.entries()].map(([id, name]) => ({ id, name }))
  }, [programs])

  const filteredPrograms = useMemo(() => {
    if (!anchorFilter) return programs
    return programs.filter((p) => p.anchorId === anchorFilter)
  }, [programs, anchorFilter])

  return (
    <section className={intakeStepSectionClass}>
      <h2 className="text-lg font-semibold text-slate-900">Select anchor program</h2>
      <p className="mt-1 text-sm text-slate-600">
        Choose the anchor and lending program for this invoice discounting borrower application.
        Only programs synced successfully to PLP are shown.
      </p>

      {loading ? <p className="mt-4 text-sm text-slate-500">Loading programs…</p> : null}
      {error ? <p className="mt-4 text-sm text-red-600">{error}</p> : null}

      {!loading && !error && programs.length === 0 ? (
        <p className="mt-4 text-sm text-amber-800">
          No synced PLP programs are available. Create and sync a program for an anchor first (Sanction
          tab or PLP Programs).
        </p>
      ) : null}

      {!loading && programs.length > 0 ? (
        <ProgramSelectFields
          anchors={anchors}
          anchorFilter={anchorFilter}
          onAnchorFilter={setAnchorFilter}
          programs={filteredPrograms}
          selectedSubProgramId={selectedSubProgramId}
          onSelect={onSelect}
        />
      ) : null}
    </section>
  )
}

function ProgramSelectFields({
  anchors,
  anchorFilter,
  onAnchorFilter,
  programs,
  selectedSubProgramId,
  onSelect,
}: {
  anchors: { id: string; name: string }[]
  anchorFilter: string
  onAnchorFilter: (id: string) => void
  programs: PlpProgramSummary[]
  selectedSubProgramId: string
  onSelect: (subProgramId: string, summary: PlpProgramSummary) => void
}) {
  return (
    <div className="mt-6 grid gap-4">
      <label className="block text-sm font-medium text-slate-700">
        Anchor
        <select
          className="mt-1 bt-input w-full text-sm"
          value={anchorFilter}
          onChange={(e) => onAnchorFilter(e.target.value)}
        >
          <option value="">All anchors</option>
          {anchors.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </select>
      </label>

      <label className="block text-sm font-medium text-slate-700">
        Program
        <select
          className="mt-1 bt-input w-full text-sm"
          value={selectedSubProgramId}
          onChange={(e) => {
            const sp = programs.find((p) => p.subProgramId === e.target.value)
            if (sp?.subProgramId) onSelect(sp.subProgramId, sp)
          }}
        >
          <option value="">Select a program…</option>
          {programs.map((p) => (
            <option key={p.subProgramId ?? p.programId} value={p.subProgramId ?? ''}>
              {p.programName} — {p.anchorName ?? 'Anchor'} (
              {p.creditLimit != null ? `₹${p.creditLimit}` : 'limit n/a'})
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}
