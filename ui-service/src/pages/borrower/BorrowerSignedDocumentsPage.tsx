import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listBorrowerApplications } from '@/api/borrowerPortal'
import { BorrowerDocumentsPanel } from '@/components/borrower/BorrowerDocumentsPanel'
import { PageHeader } from '@/components/PageHeader'
import { loanProductLabel } from '@/catalog/loanProducts'

export function BorrowerSignedDocumentsPage() {
  const [applications, setApplications] = useState<Awaited<ReturnType<typeof listBorrowerApplications>>['content']>(
    [],
  )
  const [selectedId, setSelectedId] = useState<string>('')
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let c = true
    void listBorrowerApplications(0, 50)
      .then(({ content }) => {
        if (!c) return
        setApplications(content)
        if (content.length > 0 && content[0]?.applicationId) {
          setSelectedId(content[0].applicationId)
        }
      })
      .catch((e) => {
        if (c) setLoadError(e instanceof Error ? e.message : 'Could not load applications')
      })
    return () => {
      c = false
    }
  }, [])

  const selected = applications.find((a) => a.applicationId === selectedId)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Documents"
        description="Review KYC documents you submitted and signed agreements for your loan applications."
      />
      {loadError ? <p className="text-sm text-rose-700">{loadError}</p> : null}
      {applications.length === 0 && !loadError ? (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-700 shadow-sm">
          <p>You do not have any applications yet.</p>
          <Link to="/borrower/apply" className="mt-3 inline-block font-medium text-bl-primary underline">
            Apply for a loan
          </Link>
        </div>
      ) : null}
      {applications.length > 0 ? (
        <>
          <label className="block max-w-md text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Application</span>
            <select
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
              value={selectedId}
              onChange={(e) => setSelectedId(e.target.value)}
            >
              {applications.map((a) => (
                <option key={a.applicationId} value={a.applicationId}>
                  {a.applicationNumber} · {loanProductLabel(a.product)} · {a.friendlyStatus}
                </option>
              ))}
            </select>
          </label>
          {selected ? (
            <p className="text-xs text-slate-500">
              <Link to={`/borrower/applications/${selected.applicationId}`} className="underline">
                Open application details
              </Link>
            </p>
          ) : null}
          {selectedId ? <BorrowerDocumentsPanel applicationId={selectedId} /> : null}
        </>
      ) : null}
    </div>
  )
}
