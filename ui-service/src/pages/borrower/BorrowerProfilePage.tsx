import { useAuth } from '@/auth/useAuth'
import { PageHeader } from '@/components/PageHeader'

export function BorrowerProfilePage() {
  const { user } = useAuth()
  if (!user) {
    return null
  }
  return (
    <div>
      <PageHeader
        title="Profile & settings"
        description="Basic profile information from your sign-in. Password changes can use the forgot-password flow in this demo build."
      />
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm shadow-sm">
        <dl className="space-y-4">
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Name</dt>
            <dd className="mt-1 text-slate-900">{user.name}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Email</dt>
            <dd className="mt-1 text-slate-900">{user.email}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Role</dt>
            <dd className="mt-1 text-slate-900">{user.role}</dd>
          </div>
        </dl>
      </div>
    </div>
  )
}
