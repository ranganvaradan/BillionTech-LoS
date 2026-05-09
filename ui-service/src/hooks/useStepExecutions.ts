import { useCallback, useEffect, useState } from 'react'
import { listStepExecutions } from '@/api/stepExecutions'
import type { StepExecutionRecordView } from '@/types/stepExecution'

export function useStepExecutions(applicationId: string | undefined) {
  const [data, setData] = useState<StepExecutionRecordView[] | null>(null)
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
        const d = await listStepExecutions(applicationId)
        if (!cancelled) {
          setData(d)
          setLoading(false)
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setData(null)
          setError(e instanceof Error ? e.message : 'Failed to load step executions')
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
