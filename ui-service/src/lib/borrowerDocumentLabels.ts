/** Borrower-visible document type labels (KYC + signed). */
const DOC_TYPE_LABEL: Record<string, string> = {
  PAN_CARD: 'PAN card',
  AADHAAR: 'Aadhaar',
  BANK_STATEMENT: 'Bank statement',
  PHOTOGRAPH: 'Photograph',
  INCOME_PROOF: 'Income proof (salary / ITR)',
  BORROWER_KYC: 'KYC document',
  PAYSLIP: 'Payslip',
  SALARY_SLIP: 'Salary slip',
  GST_RETURNS: 'GST returns',
  GST_RETURN: 'GST return (GSTR)',
  BUSINESS_PROOF: 'Business proof (Udyam / license)',
  ITR: 'ITR / tax return',
  BOARD_RESOLUTION: 'Board resolution',
  SIGNED_AGREEMENT: 'Signed agreement',
  PROPERTY_DOCUMENT: 'Property document (title / deed)',
  PROPERTY_VALUATION: 'Property valuation',
  SHARE_HOLDING_STATEMENT: 'Share / demat holding statement',
  GOLD_PHOTO: 'Gold / security photo',
  GOLD_VALUATION: 'Gold valuation',
  COLLATERAL_OTHER: 'Other collateral document',
  OTHER: 'Other',
  KFS_AGREEMENT: 'Signed KFS & loan agreement',
  ESIGN_KFS: 'Signed Key Fact Statement',
  ESIGN_AGREEMENT: 'Signed loan agreement',
}

export function borrowerDocumentTypeLabel(documentType: string, source?: string): string {
  if (DOC_TYPE_LABEL[documentType]) return DOC_TYPE_LABEL[documentType]
  if (source === 'ESIGN') {
    return documentType.replaceAll('_', ' ').replace(/\b\w/g, (c) => c.toUpperCase())
  }
  if (documentType.startsWith('OTHER_')) return `Other (${documentType.replace(/^OTHER_/, '')})`
  if (documentType.startsWith('SIGNED_')) return documentType.replace(/^SIGNED_/, 'Signed ').replaceAll('_', ' ')
  return documentType.replaceAll('_', ' ')
}

export function formatDocumentBytes(n: number): string {
  if (n <= 0) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function isPdfContentType(ct: string): boolean {
  return ct.toLowerCase().includes('pdf')
}

function isImageContentType(ct: string): boolean {
  return ct.toLowerCase().startsWith('image/')
}

export function inferPreviewContentType(blob: Blob, contentType: string, fileName: string): string {
  if (blob.type && blob.type !== 'application/octet-stream') return blob.type
  if (contentType && contentType !== 'application/octet-stream') return contentType
  const name = fileName.toLowerCase()
  if (name.endsWith('.pdf')) return 'application/pdf'
  if (name.endsWith('.png')) return 'image/png'
  if (name.endsWith('.jpg') || name.endsWith('.jpeg')) return 'image/jpeg'
  if (name.endsWith('.webp')) return 'image/webp'
  if (name.endsWith('.gif')) return 'image/gif'
  return blob.type || contentType || 'application/octet-stream'
}

export { isPdfContentType, isImageContentType }
