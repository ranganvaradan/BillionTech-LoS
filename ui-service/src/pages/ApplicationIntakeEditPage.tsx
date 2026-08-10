import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getApplication } from '@/api/applications'
import { ApiError } from '@/api/http'
import { useAuth } from '@/auth/useAuth'
import { ApplicationIntakeWizard } from '@/components/intake/ApplicationIntakeWizard'
import { AnchorIntakeWizard } from '@/components/intake/AnchorIntakeWizard'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { staffCanContinueIntake } from '@/lib/intake/intakeResume'
import { isUuid } from '@/lib/format'
import type { ApplicationResponse } from '@/types/application'

/** Staff continues intake / edits details on an editable borrower or anchor application. */
export function ApplicationIntakeEditPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const [app, setApp] = useState<ApplicationResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id || !isUuid(id)) {
      setLoading(false)
      return
    }
    let cancelled = false
    void (async () => {
      setLoading(true)
      setError(null)
      try {
        const loaded = await getApplication(id)
        if (cancelled) return
        if (!staffCanContinueIntake(loaded, user?.role)) {
          setError(
            'This application cannot be edited in Continue intake right now. It may already be with Credit Officer, or is not in an RM-editable status.',
          )
          setApp(null)
          return
        }
        setApp(loaded)
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiError ? e.message : 'Could not load application.')
          setApp(null)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [id, user?.role])

  if (!id || !isUuid(id)) {
    return (
      <div>
        <PageHeader title="Continue application" />
        <ErrorState message="Invalid application id." />
        <p className="mt-4 text-sm">
          <Link to="/applications" className="text-slate-800 underline">
            Back to applications
          </Link>
        </p>
      </div>
    )
  }

  if (loading) {
    return (
      <div>
        <PageHeader title="Continue application" />
        <LoadingState label="Loading application…" />
      </div>
    )
  }

  if (error || !app) {
    return (
      <div>
        <PageHeader title="Continue application" />
        <ErrorState message={error ?? 'Application not available for intake edit.'} />
        <p className="mt-4 text-sm">
          <Link to={`/applications/${id}`} className="text-slate-800 underline">
            Back to application details
          </Link>
        </p>
      </div>
    )
  }

  if (app.intakeSegment === 'ANCHOR') {
    return <AnchorIntakeWizard variant="standalone" editApplicationId={id} />
  }

  return <ApplicationIntakeWizard mode="ADMIN_INTERNAL" variant="staff" editApplicationId={id} />
}
