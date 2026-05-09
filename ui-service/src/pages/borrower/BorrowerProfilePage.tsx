import { useAuth } from '@/auth/useAuth'

export function BorrowerProfilePage() {
  const { user } = useAuth()
  if (!user) {
    return null
  }
  return (
    <div>
      <h1 className="text-2xl font-semibold text-slate-900">Profile &amp; settings</h1>
      <p className="mt-1 text-sm text-slate-600">Basic profile information from your sign-in. Password changes can use the forgot-password flow in this demo build.</p>
      <div className="mt-4 rounded border border-slate-200 bg-white p-4 text-sm">
        <p>
          <span className="text-slate-500">Name: </span>
          {user.name}
        </p>
        <p>
          <span className="text-slate-500">Email: </span>
          {user.email}
        </p>
        <p>
          <span className="text-slate-500">Role: </span>
          {user.role}
        </p>
      </div>
    </div>
  )
}
