export interface StaffIntakeStepIndices {
  product: number
  plp: number
  borrower: number
  collateral: number
  kyc: number
  consent: number
  documents: number
  review: number
  last: number
}

export function staffIntakeStepIndices(needColl: boolean, needPlp: boolean): StaffIntakeStepIndices {
  let n = 0
  const product = n++
  const plp = needPlp ? n++ : -1
  const borrower = n++
  const collateral = needColl ? n++ : -1
  const kyc = n++
  const consent = n++
  const documents = n++
  const review = n++
  return { product, plp, borrower, collateral, kyc, consent, documents, review, last: review }
}

export function buildStaffStepLabels(needColl: boolean, needPlp: boolean): readonly string[] {
  const labels: string[] = ['Product']
  if (needPlp) labels.push('Anchor program')
  labels.push('Borrower')
  if (needColl) labels.push('Collateral')
  labels.push('KYC', 'Consent', 'Documents', 'Review')
  return labels
}
