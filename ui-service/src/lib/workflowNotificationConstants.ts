/**
 * Canonical workflow step notification catalog keys (aligned with WorkflowStepEditorPanel and notification-service seeds).
 */
export const WORKFLOW_NOTIFICATION_EVENT_OPTIONS = [
  'KYC_SUCCESS',
  'VKYC_LINK',
  'VKYC_APPROVED',
  'VKYC_COMPLETED_VIA_PKY',
  'VKYC_REJECTED',
  'ESIGN_LINK',
  'SANCTION_APPROVED',
  'DISBURSEMENT_COMPLETED',
  'APPLICATION_REJECTED',
  'REMINDER',
] as const

export type WorkflowNotificationEventCode = (typeof WORKFLOW_NOTIFICATION_EVENT_OPTIONS)[number]

export const WORKFLOW_NOTIFICATION_CHANNELS = ['EMAIL', 'SMS', 'WHATSAPP', 'PUSH', 'WEBHOOK'] as const

export type WorkflowNotificationChannelCode = (typeof WORKFLOW_NOTIFICATION_CHANNELS)[number]
