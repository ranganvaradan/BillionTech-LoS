import { type FormEvent, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  createInvoiceDiscountingInvoice,
  uploadInvoiceDigitalCopy,
} from '@/api/borrowerInvoiceDiscounting'
import { getBorrowerPrograms } from '@/api/borrowerPrograms'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'

type Props = {
  flowType: 'SALES_BILL_DISCOUNTING' | 'PURCHASE_ORDER_DISCOUNTING'
  title: string
  backPath: string
}

function invoiceDueDateError(invoiceDate: string, dueDate: string): string | null {
  if (!invoiceDate || !dueDate) return null
  if (dueDate < invoiceDate) return 'Due date cannot be before invoice date'
  return null
}

export function BorrowerSellerInitiatedInvoiceCreatePage({ flowType, title, backPath }: Props) {
  const navigate = useNavigate()
  const digitalInvoiceFileRef = useRef<HTMLInputElement>(null)
  const [enrollments, setEnrollments] = useState<
    { subProgramId: string; programId: string | null; anchorId: string | null; subProgramName: string | null }[]
  >([])
  const [selectedSubProgramId, setSelectedSubProgramId] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    invoiceNumber: '',
    invoiceDate: '',
    dueDate: '',
    invoiceAmount: '',
    taxAmount: '0',
  })

  useEffect(() => {
    void (async () => {
      try {
        const res = await getBorrowerPrograms()
        const rows =
          res.enrollments
            ?.filter((e) => (e.flowType ?? '').trim() === flowType)
            .map((e) => ({
              subProgramId: e.subProgramId,
              programId: e.programId,
              anchorId: null,
              subProgramName: e.subProgramName,
            })) ?? []
        setEnrollments(rows)
        if (rows.length === 1) setSelectedSubProgramId(rows[0]!.subProgramId)
      } catch {
        setEnrollments([])
      }
    })()
  }, [flowType])

  const selected = enrollments.find((e) => e.subProgramId === selectedSubProgramId)
  const dateErr = invoiceDueDateError(form.invoiceDate, form.dueDate)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!selected?.programId) {
      setError('Select a sub-program you are enrolled in.')
      return
    }
    if (dateErr) {
      setError(dateErr)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const result = await createInvoiceDiscountingInvoice({
        invoiceNumber: form.invoiceNumber.trim(),
        programId: selected.programId,
        subProgramId: selected.subProgramId,
        flowType,
        invoiceDate: form.invoiceDate,
        dueDate: form.dueDate,
        invoiceAmount: Number(form.invoiceAmount),
        taxAmount: Number(form.taxAmount || 0),
      })
      const digitalFile = digitalInvoiceFileRef.current?.files?.[0]
      if (result.createdInvoiceId && digitalFile) {
        try {
          await uploadInvoiceDigitalCopy(result.createdInvoiceId, digitalFile)
        } catch (uploadEx) {
          setError(
            uploadEx instanceof ApiError
              ? `Invoice submitted but copy upload failed: ${uploadEx.message}`
              : 'Invoice submitted but copy upload failed.',
          )
          navigate(backPath)
          return
        }
      }
      navigate(backPath)
    } catch (ex) {
      setError(ex instanceof ApiError ? ex.message : 'Could not create invoice.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <PageHeader
        title={title}
        description="Your anchor will review and approve or reject this submission."
        actions={
          <Link to={backPath} className="text-sm font-semibold text-sky-700 hover:underline">
            ← Back
          </Link>
        }
      />
      {error ? <p className="text-sm text-rose-700">{error}</p> : null}
      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
        <div>
          <label className="bt-label">Sub-program</label>
          <select
            className="bt-input w-full"
            value={selectedSubProgramId}
            onChange={(e) => setSelectedSubProgramId(e.target.value)}
            required
          >
            <option value="">Select</option>
            {enrollments.map((row) => (
              <option key={row.subProgramId} value={row.subProgramId}>
                {row.subProgramName ?? row.subProgramId}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="bt-label">Number</label>
          <input
            className="bt-input w-full"
            value={form.invoiceNumber}
            onChange={(e) => setForm((f) => ({ ...f, invoiceNumber: e.target.value }))}
            required
          />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="bt-label">Invoice date</label>
            <input
              type="date"
              className="bt-input w-full"
              value={form.invoiceDate}
              onChange={(e) => setForm((f) => ({ ...f, invoiceDate: e.target.value }))}
              required
            />
          </div>
          <div>
            <label className="bt-label">Due date</label>
            <input
              type="date"
              className="bt-input w-full"
              value={form.dueDate}
              min={form.invoiceDate || undefined}
              onChange={(e) => setForm((f) => ({ ...f, dueDate: e.target.value }))}
              required
            />
            {dateErr ? <p className="text-xs text-rose-700 mt-1">{dateErr}</p> : null}
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="bt-label">Amount</label>
            <input
              type="number"
              min="0"
              step="0.01"
              className="bt-input w-full"
              value={form.invoiceAmount}
              onChange={(e) => setForm((f) => ({ ...f, invoiceAmount: e.target.value }))}
              required
            />
          </div>
          <div>
            <label className="bt-label">Tax</label>
            <input
              type="number"
              min="0"
              step="0.01"
              className="bt-input w-full"
              value={form.taxAmount}
              onChange={(e) => setForm((f) => ({ ...f, taxAmount: e.target.value }))}
            />
          </div>
        </div>
        <div>
          <label className="bt-label">Invoice copy (optional)</label>
          <div className="mt-1 rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center">
            <input
              ref={digitalInvoiceFileRef}
              type="file"
              accept=".pdf,image/*"
              className="mx-auto block text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-sky-600 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-white"
            />
            <p className="mt-2 text-xs text-slate-500">PDF or image, max 10 MB.</p>
          </div>
        </div>
        <button type="submit" className="bt-btn bt-btn-primary" disabled={submitting || !!dateErr}>
          {submitting ? 'Submitting…' : 'Submit for anchor review'}
        </button>
      </form>
    </div>
  )
}
