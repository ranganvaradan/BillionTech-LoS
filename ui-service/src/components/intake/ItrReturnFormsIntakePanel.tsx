import { useEffect, useState } from 'react'

type Props = {
  variant: 'borrower' | 'staff'
  username: string
  password: string
  consent: boolean
  success: boolean
  statusLabel: string | null
  error: string | null
  /** First-minute hard block (provider call just started). */
  busy: boolean
  /** After ~1 min: request still running, form re-enabled. */
  processingBackground?: boolean
  /** Soft outcome after long run: timeout or terminal error from client. */
  terminalPhase?: 'idle' | 'success' | 'error' | 'timeout' | 'running' | 'background' | null
  disabled?: boolean
  onUsernameChange: (v: string) => void
  onPasswordChange: (v: string) => void
  onConsentChange: (v: boolean) => void
  onVerify: () => void
}

export function ItrReturnFormsIntakePanel({
  variant,
  username,
  password,
  consent,
  success,
  statusLabel,
  error,
  busy,
  processingBackground,
  terminalPhase,
  disabled,
  onUsernameChange,
  onPasswordChange,
  onConsentChange,
  onVerify,
}: Props) {
  /** After success, re-verify form stays collapsed unless the borrower opens it. */
  const [retryOpen, setRetryOpen] = useState(false)

  // Collapse optional re-verify when a run newly succeeds.
  useEffect(() => {
    if (success) setRetryOpen(false)
  }, [success])

  // If verification fails after a prior success, surface the form again.
  useEffect(() => {
    if (!success && (error || terminalPhase === 'error' || terminalPhase === 'timeout')) {
      setRetryOpen(true)
    }
  }, [success, error, terminalPhase])

  if (variant === 'staff') {
    return (
      <div className="bt-section-card mt-4">
        <div className="bt-section-card__header">
          <div className="bt-section-card__header-text">
            <h3 className="bt-section-card__title">ITR return forms</h3>
            <p className="bt-section-card__subtitle">
              Income Tax portal credentials are entered by the borrower only.
            </p>
          </div>
          <span className={`bt-section-card__chip ${success ? 'bt-section-card__chip--success' : 'bt-section-card__chip--warning'}`}>
            {success ? 'Verified' : statusLabel || 'Pending borrower'}
          </span>
        </div>
        <div className="bt-section-card__body">
          <p className="text-sm text-slate-600">
            Borrower must complete ITR login on the portal before submission
            {statusLabel ? ` · current: ${statusLabel}` : ''}.
          </p>
        </div>
      </div>
    )
  }

  const formLocked = Boolean(disabled || busy)
  const ctaDisabled = Boolean(disabled || busy)
  const isFailed =
    !success &&
    (Boolean(error) || terminalPhase === 'error' || terminalPhase === 'timeout')
  const showCredentialForm = !success || retryOpen || processingBackground || isFailed

  const verifyLabel = busy
    ? 'Verifying…'
    : processingBackground
      ? 'Still processing…'
      : success
        ? 'Re-verify ITR'
        : isFailed || terminalPhase === 'timeout' || terminalPhase === 'error'
          ? 'Retry ITR verification'
          : 'Verify ITR'

  return (
    <div className={`bt-section-card mt-4 ${success ? 'bt-section-card--success' : isFailed ? 'bt-section-card--warning' : ''}`}>
      <div className="bt-section-card__header">
        <div className="bt-section-card__header-text">
          <h3 className="bt-section-card__title">ITR return forms</h3>
          <p className="bt-section-card__subtitle">
            Username and password are sent only to the ITR provider for this request. We never store the password.
          </p>
        </div>
        {success ? (
          <span className="bt-section-card__chip bt-section-card__chip--success">Verified</span>
        ) : processingBackground ? (
          <span className="bt-section-card__chip bt-section-card__chip--warning">Processing</span>
        ) : isFailed ? (
          <span className="bt-section-card__chip bt-section-card__chip--danger">Failed</span>
        ) : (
          <span className="bt-section-card__chip bt-section-card__chip--warning">Required</span>
        )}
      </div>
      <div className="bt-section-card__body space-y-3">
        {success ? (
          <div className="rounded-md border border-emerald-200 bg-emerald-50/70 px-3 py-2 text-sm text-emerald-950">
            <p className="font-medium">
              ITR verified{statusLabel ? ` (${statusLabel})` : ''}. You can continue with the application.
            </p>
            <p className="mt-0.5 text-xs text-emerald-900/90">
              Credentials are not shown after a successful pull. Expand below only if you need to verify again.
            </p>
          </div>
        ) : null}

        {processingBackground ? (
          <div role="status" className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
            <p className="font-medium">ITR verification is processing in the background</p>
            <p className="mt-0.5 text-xs text-amber-900/90">
              The provider can take up to 5 minutes. When the result arrives, you will see success, failure, or timeout
              with the next action.
            </p>
          </div>
        ) : null}

        {terminalPhase === 'timeout' && !processingBackground && !success ? (
          <div className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-950">
            Request timed out. Update credentials if needed, then use <strong>Retry ITR verification</strong>.
          </div>
        ) : null}

        {isFailed && error && !success ? (
          <div className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-950">
            <p className="font-medium">ITR verification failed</p>
            <p className="mt-0.5 text-xs text-rose-900/90">{error}</p>
            <p className="mt-1 text-xs text-rose-900/90">
              Correct username/password if needed, confirm consent, then retry.
            </p>
          </div>
        ) : null}

        {/* Success: optional re-verify (collapsed). Failure / first-time: form always visible. */}
        {success && !processingBackground ? (
          <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <button
              type="button"
              className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left text-sm hover:bg-slate-50"
              aria-expanded={retryOpen}
              onClick={() => setRetryOpen((o) => !o)}
            >
              <span className="font-medium text-slate-800">Re-verify ITR (optional)</span>
              <span className={`text-slate-400 transition-transform ${retryOpen ? 'rotate-90' : ''}`} aria-hidden>
                ›
              </span>
            </button>
            {retryOpen ? (
              <div className="border-t border-slate-100 px-3 py-3">
                <ItrCredentialFields
                  username={username}
                  password={password}
                  consent={consent}
                  formLocked={formLocked}
                  ctaDisabled={ctaDisabled}
                  verifyLabel={verifyLabel}
                  hideInlineError
                  onUsernameChange={onUsernameChange}
                  onPasswordChange={onPasswordChange}
                  onConsentChange={onConsentChange}
                  onVerify={onVerify}
                />
              </div>
            ) : null}
          </div>
        ) : showCredentialForm ? (
          <div className="space-y-2">
            {isFailed ? (
              <p className="text-sm font-medium text-slate-800">Retry ITR verification</p>
            ) : null}
            <ItrCredentialFields
              username={username}
              password={password}
              consent={consent}
              formLocked={formLocked}
              ctaDisabled={ctaDisabled}
              verifyLabel={verifyLabel}
              error={error && !success ? error : null}
              hideInlineError={Boolean(isFailed && error)}
              onUsernameChange={onUsernameChange}
              onPasswordChange={onPasswordChange}
              onConsentChange={onConsentChange}
              onVerify={onVerify}
            />
          </div>
        ) : null}
      </div>
    </div>
  )
}

function ItrCredentialFields({
  username,
  password,
  consent,
  formLocked,
  ctaDisabled,
  verifyLabel,
  error,
  hideInlineError,
  onUsernameChange,
  onPasswordChange,
  onConsentChange,
  onVerify,
}: {
  username: string
  password: string
  consent: boolean
  formLocked: boolean
  ctaDisabled: boolean
  verifyLabel: string
  error?: string | null
  hideInlineError?: boolean
  onUsernameChange: (v: string) => void
  onPasswordChange: (v: string) => void
  onConsentChange: (v: boolean) => void
  onVerify: () => void
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">ITD username (usually PAN) *</span>
        <input
          className="bt-input w-full font-mono uppercase"
          value={username}
          onChange={(e) => onUsernameChange(e.target.value.toUpperCase())}
          autoComplete="username"
          disabled={formLocked}
        />
      </label>
      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">ITD password *</span>
        <input
          type="password"
          className="bt-input w-full"
          value={password}
          onChange={(e) => onPasswordChange(e.target.value)}
          autoComplete="current-password"
          disabled={formLocked}
        />
      </label>
      <label className="flex items-start gap-2 text-sm text-slate-800 sm:col-span-2">
        <input
          type="checkbox"
          className="mt-1"
          checked={consent}
          disabled={formLocked}
          onChange={(e) => onConsentChange(e.target.checked)}
        />
        <span>I consent to pull my ITR return forms from the Income Tax portal for this loan application.</span>
      </label>
      {!hideInlineError && error ? <p className="text-sm text-rose-700 sm:col-span-2">{error}</p> : null}
      <div className="sm:col-span-2">
        <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" disabled={ctaDisabled} onClick={onVerify}>
          {verifyLabel}
        </button>
      </div>
    </div>
  )
}
