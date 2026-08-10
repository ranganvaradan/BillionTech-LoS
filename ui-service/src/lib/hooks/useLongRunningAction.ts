import { useCallback, useEffect, useRef, useState } from 'react'
import type { AxiosError } from 'axios'

export type LongRunningPhase = 'idle' | 'running' | 'background' | 'success' | 'error' | 'timeout'

export type LongRunningState = {
  phase: LongRunningPhase
  /** True while HTTP request is in flight (including after UI became interactive). */
  inFlight: boolean
  /** True only during the first BACKGROUND_AFTER_MS window — disables the CTA. */
  blocking: boolean
  /** Show amber banner while request continues and controls are re-enabled. */
  showBackgroundNotice: boolean
  errorMessage: string | null
  lastOutcome: string | null
}

const DEFAULT_BACKGROUND_AFTER_MS = 60_000

function isTimeoutError(err: unknown): boolean {
  if (!err || typeof err !== 'object') return false
  const ax = err as AxiosError
  if (ax.code === 'ECONNABORTED') return true
  const msg = String(ax.message ?? '').toLowerCase()
  return msg.includes('timeout') || msg.includes('exceeded')
}

/**
 * Long provider calls (ITR / GST): block the CTA only for the first minute, then show a
 * "processing in background" notice and re-enable controls. Final success / failure / timeout
 * updates the phase so the UI can offer retry or next steps.
 */
export function useLongRunningAction(opts?: { backgroundAfterMs?: number }) {
  const backgroundAfterMs = opts?.backgroundAfterMs ?? DEFAULT_BACKGROUND_AFTER_MS
  const [phase, setPhase] = useState<LongRunningPhase>('idle')
  const [inFlight, setInFlight] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [lastOutcome, setLastOutcome] = useState<string | null>(null)
  const genRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearTimer = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  useEffect(() => () => clearTimer(), [clearTimer])

  const reset = useCallback(() => {
    clearTimer()
    genRef.current += 1
    setPhase('idle')
    setInFlight(false)
    setErrorMessage(null)
    setLastOutcome(null)
  }, [clearTimer])

  const run = useCallback(
    async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
      clearTimer()
      const gen = ++genRef.current
      setInFlight(true)
      setPhase('running')
      setErrorMessage(null)
      setLastOutcome(null)

      timerRef.current = setTimeout(() => {
        if (genRef.current === gen) {
          setPhase((p) => (p === 'running' ? 'background' : p))
        }
      }, backgroundAfterMs)

      try {
        const result = await fn()
        if (genRef.current !== gen) return result
        clearTimer()
        setInFlight(false)
        setPhase('success')
        return result
      } catch (err) {
        if (genRef.current !== gen) return undefined
        clearTimer()
        setInFlight(false)
        if (isTimeoutError(err)) {
          setPhase('timeout')
          setErrorMessage(
            'The request timed out waiting for the provider. You can retry, or check status later if it completed server-side.',
          )
        } else {
          setPhase('error')
          const ax = err as AxiosError<{ message?: string; error?: string }>
          const msg =
            ax.response?.data?.message ||
            ax.response?.data?.error ||
            (err instanceof Error ? err.message : null) ||
            'Request failed.'
          setErrorMessage(String(msg))
        }
        throw err
      }
    },
    [backgroundAfterMs, clearTimer],
  )

  const markOutcome = useCallback((ok: boolean, outcome?: string | null, err?: string | null) => {
    setLastOutcome(outcome ?? null)
    if (ok) {
      setPhase('success')
      setErrorMessage(null)
    } else {
      setPhase('error')
      if (err) setErrorMessage(err)
    }
  }, [])

  const state: LongRunningState = {
    phase,
    inFlight,
    blocking: phase === 'running',
    showBackgroundNotice: phase === 'background' && inFlight,
    errorMessage,
    lastOutcome,
  }

  return { state, run, reset, markOutcome, setErrorMessage }
}
