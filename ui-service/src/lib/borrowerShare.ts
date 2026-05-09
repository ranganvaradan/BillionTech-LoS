/**
 * Public borrower status page path (use with site origin in browser).
 * @param applicationId - UUID from application record
 * @param origin - e.g. window.location.origin, or empty to get path-only
 */
export function borrowerStatusPath(applicationId: string, origin: string = ''): string {
  const path = `/borrower/status/${applicationId}`
  if (!origin) return path
  return `${origin.replace(/\/$/, '')}${path}`
}

/**
 * Open WhatsApp (demo) with pre-filled message containing the shareable link.
 * No WhatsApp Business API; opens wa.me in a new tab.
 */
export function buildWhatsAppStatusShareUrl(phoneE164: string, statusPageUrl: string): string {
  const digits = phoneE164.replace(/\D/g, '')
  if (!digits) {
    return `https://wa.me/?text=${encodeURIComponent(`Track your application: ${statusPageUrl}`)}`
  }
  const text = `Track your application: ${statusPageUrl}`
  return `https://wa.me/${digits}?text=${encodeURIComponent(text)}`
}
