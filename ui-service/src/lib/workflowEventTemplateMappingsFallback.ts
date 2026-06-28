import type { WorkflowEventTemplateMappingDto } from '@/api/workflowEventTemplateMappings'
import {
  WORKFLOW_NOTIFICATION_CHANNELS,
  WORKFLOW_NOTIFICATION_EVENT_OPTIONS,
  type WorkflowNotificationChannelCode,
  type WorkflowNotificationEventCode,
} from '@/lib/workflowNotificationConstants'

/**
 * Mirrors notification-service V4 defaults when GET /workflow-event-template-mappings is unreachable.
 * Keeps editors usable in local/dev without cross-service wiring.
 */
const PRIMARY_BY_EVENT: Record<
  WorkflowNotificationEventCode,
  Record<WorkflowNotificationChannelCode, string>
> = {
  KYC_SUCCESS: {
    EMAIL: 'KYC_SUCCESS_EMAIL',
    SMS: 'KYC_SUCCESS_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
  VKYC_LINK: {
    EMAIL: 'VKYC_LINK_EMAIL',
    SMS: 'VKYC_LINK_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
  VKYC_APPROVED: {
    EMAIL: 'VKYC_APPROVED_EMAIL',
    SMS: 'VKYC_APPROVED_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
  VKYC_COMPLETED_VIA_PKY: {
    EMAIL: 'VKYC_COMPLETED_VIA_PKY_EMAIL',
    SMS: 'VKYC_COMPLETED_VIA_PKY_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
  VKYC_REJECTED: {
    EMAIL: 'VKYC_REJECTED_EMAIL',
    SMS: 'VKYC_REJECTED_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
  ESIGN_LINK: {
    EMAIL: 'ESIGN_PENDING_EMAIL',
    SMS: 'ESIGN_PENDING_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
  SANCTION_APPROVED: {
    EMAIL: 'SANCTION_APPROVED_EMAIL',
    SMS: 'SANCTION_APPROVED_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
  DISBURSEMENT_COMPLETED: {
    EMAIL: 'DISBURSEMENT_SUCCESS_EMAIL',
    SMS: 'DISBURSEMENT_SUCCESS_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
  APPLICATION_REJECTED: {
    EMAIL: 'APPLICATION_REJECTED_EMAIL',
    SMS: 'APPLICATION_REJECTED_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
  REMINDER: {
    EMAIL: 'WORKFLOW_PAYMENT_REMINDER_EMAIL',
    SMS: 'WORKFLOW_PAYMENT_REMINDER_SMS',
    WHATSAPP: 'WORKFLOW_STANDARD_WHATSAPP',
    PUSH: 'WORKFLOW_STANDARD_PUSH',
    WEBHOOK: 'WORKFLOW_STANDARD_WEBHOOK',
  },
}

/** Extra options when API is down (subset of DB alternates). */
const ALTERNATES: Array<{
  workflowEvent: WorkflowNotificationEventCode
  channel: WorkflowNotificationChannelCode
  templateCode: string
  sortOrder: number
}> = [
  { workflowEvent: 'KYC_SUCCESS', channel: 'EMAIL', templateCode: 'KYC_COMPLETED', sortOrder: 10 },
  { workflowEvent: 'VKYC_LINK', channel: 'EMAIL', templateCode: 'VKYC_LINK', sortOrder: 10 },
  { workflowEvent: 'ESIGN_LINK', channel: 'EMAIL', templateCode: 'ESIGN_PENDING', sortOrder: 10 },
  { workflowEvent: 'DISBURSEMENT_COMPLETED', channel: 'EMAIL', templateCode: 'DISBURSEMENT_COMPLETED', sortOrder: 10 },
]

export function staticWorkflowEventTemplateMappings(): WorkflowEventTemplateMappingDto[] {
  const out: WorkflowEventTemplateMappingDto[] = []
  let n = 0
  for (const workflowEvent of WORKFLOW_NOTIFICATION_EVENT_OPTIONS) {
    for (const channel of WORKFLOW_NOTIFICATION_CHANNELS) {
      const templateCode = PRIMARY_BY_EVENT[workflowEvent][channel]
      out.push({
        id: `static-${n++}`,
        workflowEvent,
        channel,
        templateCode,
        defaultMapping: true,
        active: true,
        sortOrder: 0,
      })
    }
  }
  for (const a of ALTERNATES) {
    out.push({
      id: `static-${n++}`,
      workflowEvent: a.workflowEvent,
      channel: a.channel,
      templateCode: a.templateCode,
      defaultMapping: false,
      active: true,
      sortOrder: a.sortOrder,
    })
  }
  return out
}

export function defaultTemplateCodeForEventChannel(
  eventType: string,
  channel: string,
  mappings: WorkflowEventTemplateMappingDto[],
): string {
  const ev = eventType.trim().toUpperCase()
  const ch = channel.trim().toUpperCase()
  const rows = mappings.filter(
    (m) => m.workflowEvent === ev && m.channel === ch && m.active !== false,
  )
  const preferred = rows.find((m) => m.defaultMapping) ?? rows[0]
  if (preferred) return preferred.templateCode
  const fe = ev as WorkflowNotificationEventCode
  const fc = ch as WorkflowNotificationChannelCode
  if (PRIMARY_BY_EVENT[fe]?.[fc]) return PRIMARY_BY_EVENT[fe][fc]
  return ''
}
