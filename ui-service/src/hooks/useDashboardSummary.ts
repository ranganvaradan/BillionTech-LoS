import { useCallback, useEffect, useState } from 'react'
import { getDashboardSummary } from '@/api/applications'
import type { DashboardSummary } from '@/types/api'

export function useDashboardSummary() {
  const [data, setData] = useState<DashboardSummary | null>(null)
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
        const d = await getDashboardSummary()
        if (!cancelled) {
          setData(d)
          setLoading(false)
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setData(null)
          setError(e instanceof Error ? e.message : 'Failed to load dashboard')
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [refreshKey])

  return { data, loading, error, refetch }
}
