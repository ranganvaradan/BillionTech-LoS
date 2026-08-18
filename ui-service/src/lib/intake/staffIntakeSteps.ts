export interface StaffIntakeStepIndices {
  product: number
  plp: number
  borrower: number
  category: number
  collateral: number
  documents: number
  consent: number
  kyc: number
  review: number
  last: number
}

/**
 * Staff/borrower intake order: Category pin after Borrower, then Documents before Consent before KYC.
 * Collateral (if needed) stays after Category and before Documents.
 */
export function staffIntakeStepIndices(needColl: boolean, needPlp: boolean): StaffIntakeStepIndices {
  let n = 0
  const product = n++
  const plp = needPlp ? n++ : -1
  const borrower = n++
  const category = n++
  const collateral = needColl ? n++ : -1
  const documents = n++
  const consent = n++
  const kyc = n++
  const review = n++
  return { product, plp, borrower, category, collateral, documents, consent, kyc, review, last: review }
}

export function buildStaffStepLabels(needColl: boolean, needPlp: boolean): readonly string[] {
  const labels: string[] = ['Product']
  if (needPlp) labels.push('Anchor program')
  labels.push('Borrower', 'Category')
  if (needColl) labels.push('Collateral')
  labels.push('Documents', 'Consent', 'KYC', 'Review')
  return labels
}

