import { useCallback, useEffect, useState } from 'react'
import { getApplication } from '@/api/applications'
import type { ApplicationResponse } from '@/types/application'

export function useApplication(applicationId: string | undefined) {
  const [data, setData] = useState<ApplicationResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refetchKey, setRefetchKey] = useState(0)

  const refetch = useCallback(() => {
    setRefetchKey((k) => k + 1)
  }, [])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      if (!applicationId) {
        setData(null)
        setError('Missing application id')
        setLoading(false)
        return
      }
      setLoading(true)
      setError(null)
      try {
        const d = await getApplication(applicationId)
        if (!cancelled) {
          setData(d)
          setLoading(false)
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setData(null)
          setError(e instanceof Error ? e.message : 'Failed to load application')
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [applicationId, refetchKey])

  return { data, loading, error, refetch }
}
