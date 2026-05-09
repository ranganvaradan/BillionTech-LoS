import { useCallback, useEffect, useState } from 'react'
import { listApplications } from '@/api/applications'
import type { ApplicationPage } from '@/types/api'
import type { ApplicationStatus } from '@/types/application'

export function useApplications(params: { status?: ApplicationStatus; page?: number; size?: number }) {
  const { status, page = 0, size = 20 } = params
  const [data, setData] = useState<ApplicationPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const refetch = useCallback(() => {
    setRefreshKey((k) => k + 1)
  }, [])

  useEffect(() => {
    const onGlobalClear = () => refetch()
    window.addEventListener('los:demo-data-cleared', onGlobalClear)
    return () => window.removeEventListener('los:demo-data-cleared', onGlobalClear)
  }, [refetch])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      setLoading(true)
      setError(null)
      try {
        const d = await listApplications({ status, page, size })
        if (!cancelled) {
          setData(d)
          setLoading(false)
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setData(null)
          setError(e instanceof Error ? e.message : 'Failed to load applications')
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status, page, size, refreshKey])

  return { data, loading, error, refetch }
}
