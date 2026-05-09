import type { ReactNode } from 'react'

/**
 * Wraps a downstream-tab section (CAM, Sanction, eSign, Disbursement) with a
 * notice + a {@code <fieldset disabled>} so all interactive form controls and
 * action buttons inside the section are blocked until VKYC has been
 * auditor-approved.
 *
 * The wrapper does NOT redesign the section layout — it only adds:
 *   - a clear amber notice explaining why actions are disabled
 *   - native HTML {@code disabled} propagation through {@code <fieldset>}, which
 *     disables all descendant {@code button}, {@code input}, {@code select} and
 *     {@code textarea} elements
 *
 * Read-only displays (links, anchors, copy actions, generated PDFs) remain
 * accessible because anchors are not form controls. Backend safety is provided
 * by {@code VkycWorkflowService.assertVkycCleared}; this gate is purely a UX
 * layer mirroring the same rules.
 */
export function VkycDownstreamGate({
  blocked,
  vkycStatus,
  workflowPosition,
  children,
}: {
  blocked: boolean
  vkycStatus?: string
  workflowPosition?: string
  children: ReactNode
}) {
  if (!blocked) {
    return <>{children}</>
  }
  const status = (vkycStatus ?? 'NOT_STARTED').toUpperCase()
  const position = (workflowPosition ?? '').toUpperCase()
  return (
    <div>
      <div
        role="status"
        className="mb-3 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900"
      >
        <strong className="font-semibold">VKYC auditor approval pending.</strong>{' '}
        Actions in this step are disabled until the VKYC auditor approves the video
        KYC for this application. The tab stays open for review and reference.
        <span className="ml-1 text-xs text-amber-800">
          (VKYC status: {status}
          {position ? ` · workflow position: ${position}` : ''})
        </span>
      </div>
      {/*
        Tailwind's preflight resets fieldset margin/padding/border so this does
        not affect internal layout. The native `disabled` attribute propagates
        to all descendant form controls (button, input, select, textarea).
      */}
      <fieldset disabled aria-busy="true" className="m-0 min-w-0 border-0 p-0">
        {children}
      </fieldset>
    </div>
  )
}
