import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApplication } from '@/api/applications'
import { listApplicationParties, submitApplicationParty, updateApplicationPartyPersonalInfo } from '@/api/workflow'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { validateApplicationIdentity } from '@/lib/intake/checkIntakeIdentity'
import { notifyError, notifySuccess } from '@/lib/notify'
import type { ApplicationPartyResponse } from '@/types/application'

const SUBMITTED_INTAKE_STATUSES = new Set(['SUBMITTED', 'KYC_COMPLETE', 'ESIGN_PENDING', 'ESIGN_COMPLETE'])

function str(v: unknown): string {
  return typeof v === 'string' ? v : v == null ? '' : String(v)
}

/**
 * Slim borrower-portal form for a co-applicant (`?resume={appId}&partyId={partyId}`). Only captures the
 * co-applicant's own personal fields — the primary applicant's full wizard is untouched by this component.
 */
export function CoApplicantPortal({ applicationId, partyId }: { applicationId: string; partyId: string }) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [party, setParty] = useState<ApplicationPartyResponse | null>(null)
  const [applicationNumber, setApplicationNumber] = useState('')
  const [fullName, setFullName] = useState('')
  const [mobile, setMobile] = useState('')
  const [email, setEmail] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [panNumber, setPanNumber] = useState('')
  const [busy, setBusy] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      setLoading(true)
      setError(null)
      try {
        const [app, parties] = await Promise.all([
          getApplication(applicationId),
          listApplicationParties(applicationId),
        ])
        if (cancelled) return
        const p = parties.find((x) => x.id === partyId)
        if (!p) {
          setError('Co-applicant record not found for this application.')
          return
        }
        if (p.role !== 'CO_APPLICANT') {
          setError('This link is not a co-applicant link.')
          return
        }
        setApplicationNumber(app.applicationNumber)
        setParty(p)
        const pi = p.personalInfo ?? {}
        setFullName(p.displayName ?? str(pi.fullName))
        setMobile(p.mobile ?? str(pi.mobile))
        setEmail(p.email ?? str(pi.email))
        setDateOfBirth(str(pi.dateOfBirth))
        setPanNumber(str(pi.panNumber))
        setSubmitted(SUBMITTED_INTAKE_STATUSES.has(String(p.intakeStatus ?? '')))
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load your co-applicant details.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [applicationId, partyId])

  async function onSubmit() {
    if (!fullName.trim()) {
      setError('Enter your full name.')
      return
    }
    if (mobile.replace(/\D/g, '').length < 10) {
      setError('Enter a valid mobile number.')
      return
    }
    if (!email.trim()) {
      setError('Enter your email.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await validateApplicationIdentity({
        applicationId,
        asCoApplicant: true,
        email: email.trim() || undefined,
        mobile: mobile.replace(/\D/g, '') || undefined,
        panNumber: panNumber.trim() || undefined,
      })
      await updateApplicationPartyPersonalInfo(applicationId, partyId, {
        fullName: fullName.trim(),
        mobile: mobile.trim(),
        email: email.trim(),
        ...(dateOfBirth.trim() ? { dateOfBirth: dateOfBirth.trim() } : {}),
        ...(panNumber.trim() ? { panNumber: panNumber.trim().toUpperCase() } : {}),
      })
      await submitApplicationParty(applicationId, partyId)
      setSubmitted(true)
      notifySuccess('Your details have been submitted.')
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Could not submit your details.'
      setError(msg)
      notifyError(e, 'Could not submit your details.')
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-xl">
        <LoadingState label="Loading your co-applicant details…" />
      </div>
    )
  }

  if (error && !party) {
    return (
      <div className="mx-auto max-w-xl">
        <ErrorState message={error} />
      </div>
    )
  }

  if (submitted) {
    return (
      <div className="mx-auto max-w-xl space-y-4">
        <h1 className="text-xl font-semibold text-slate-900">Thank you</h1>
        <p className="text-sm text-slate-700">
          Your details for application <span className="font-medium">{applicationNumber}</span> have been submitted.
          The lender will reach out if anything else is needed.
        </p>
        <Link to="/borrower/dashboard" className="text-sm font-medium text-slate-800 underline">
          Go to dashboard
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">You&apos;ve been added as a co-applicant</h1>
        <p className="mt-1 text-sm text-slate-600">
          Application <span className="font-medium">{applicationNumber}</span> — please confirm your details below
          and submit. The primary applicant has already started this application; you only need to complete your own
          information.
        </p>
      </div>
      {error ? <ErrorState message={error} /> : null}
      <section className="space-y-4 bt-card p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Full name *</span>
            <input className="bt-input w-full" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Mobile *</span>
            <input
              type="tel"
              className="bt-input w-full"
              value={mobile}
              onChange={(e) => setMobile(e.target.value)}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Email *</span>
            <input
              type="email"
              className="bt-input w-full"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Date of birth</span>
            <input
              type="date"
              className="bt-input w-full"
              value={dateOfBirth}
              onChange={(e) => setDateOfBirth(e.target.value)}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">PAN</span>
            <input
              className="bt-input w-full font-mono uppercase"
              maxLength={10}
              value={panNumber}
              onChange={(e) => setPanNumber(e.target.value.toUpperCase())}
            />
          </label>
        </div>
      </section>
      <button
        type="button"
        onClick={() => void onSubmit()}
        disabled={busy}
        className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
      >
        {busy ? 'Submitting…' : 'Submit my details'}
      </button>
    </div>
  )
}
