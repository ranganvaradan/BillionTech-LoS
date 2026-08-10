import { resolveIntakeSegment } from '@/lib/applicationPartyLabels'
import type { ApplicationIntakeSegment, ApplicationResponse } from '@/types/application'

function pickStr(rec: Record<string, unknown> | null | undefined, key: string): string {
  if (!rec) return ''
  const v = rec[key]
  return v == null ? '' : String(v).trim()
}

/** Client-side signer payload aligned with backend {@link ApplicationPartyResolver}. */
export function esignSignerFromApplication(app: ApplicationResponse): Record<string, unknown> {
  const isAnchor = resolveIntakeSegment(app.intakeSegment) === 'ANCHOR'
  const bi = (app.businessInfo ?? null) as Record<string, unknown> | null
  const pi = (app.personalInfo ?? null) as Record<string, unknown> | null

  if (isAnchor) {
    const name =
      pickStr(bi, 'corporateName') ||
      pickStr(bi, 'businessName') ||
      pickStr(bi, 'accountHolderName') ||
      'Anchor'
    const email = pickStr(bi, 'email') || pickStr(pi, 'borrowerEmail') || pickStr(pi, 'email')
    return {
      name,
      borrowerName: name,
      fullName: name,
      borrowerEmail: email,
      email,
      phone: pickStr(bi, 'mobile') || pickStr(pi, 'phone') || pickStr(pi, 'mobile'),
    }
  }

  if (!pi) {
    return { name: 'Borrower' }
  }
  const first = pickStr(pi, 'firstName')
  const last = pickStr(pi, 'lastName')
  const name = pickStr(pi, 'fullName') || pickStr(pi, 'name') || [first, last].filter(Boolean).join(' ').trim() || 'Borrower'
  const email = pickStr(pi, 'borrowerEmail') || pickStr(pi, 'email')
  return {
    name,
    firstName: first,
    lastName: last,
    borrowerEmail: email,
    email,
    phone: pickStr(pi, 'phone') || pickStr(pi, 'mobile'),
  }
}

export function isAnchorApplication(segment?: ApplicationIntakeSegment | null): boolean {
  return resolveIntakeSegment(segment) === 'ANCHOR'
}

/**
 * Safe borrower/anchor display name for lists. Returns "—" when unknown — never raw JSON.
 */
export function displayBorrowerName(app: ApplicationResponse): string {
  const isAnchor = resolveIntakeSegment(app.intakeSegment) === 'ANCHOR'
  const bi = (app.businessInfo ?? null) as Record<string, unknown> | null
  const pi = (app.personalInfo ?? null) as Record<string, unknown> | null

  if (isAnchor) {
    const name =
      pickStr(bi, 'corporateName') ||
      pickStr(bi, 'businessName') ||
      pickStr(bi, 'accountHolderName') ||
      pickStr(pi, 'fullName') ||
      pickStr(pi, 'name')
    return name || '—'
  }

  const first = pickStr(pi, 'firstName')
  const last = pickStr(pi, 'lastName')
  const name =
    pickStr(pi, 'fullName') ||
    pickStr(pi, 'name') ||
    [first, last].filter(Boolean).join(' ').trim() ||
    pickStr(bi, 'contactPersonName') ||
    pickStr(bi, 'businessName')
  return name || '—'
}
