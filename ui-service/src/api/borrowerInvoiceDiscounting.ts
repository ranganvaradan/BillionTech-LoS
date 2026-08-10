import { http } from './http'

export interface BorrowerInvoiceItem {
  invoiceId: string
  invoiceNumber: string | null
  invoiceDate: string | null
  dueDate: string | null
  invoiceAmount: number | null
  netAmount: number | null
  eligibleAmount: number | null
  availableAmount: number | null
  status: string | null
  friendlyStatus: string
  programId: string | null
  anchorId: string | null
  flowType: string | null
  acceptable: boolean
  financeable: boolean
  maxFinanceableAmount: number | null
  suggestedFinanceAmount: number | null
  pipAmount?: number | null
  subProgramId?: string | null
  isEarlyPayAllowed?: string | null
  showEarlyPay?: string | null
  balDueAmount?: number | null
  earlyPayable?: boolean
  digitalInvoiceFileName?: string | null
  digitalInvoiceContentType?: string | null
  deletable?: boolean
  enablePaymentForBorrower?: boolean
}

export interface BorrowerInvoiceRepayment {
  repaymentId: string | null
  paidAt: string | null
  amount: number | null
  reference: string | null
  source: string | null
  status: string | null
  paymentMode: string | null
  friendlySource: string | null
}

export interface BorrowerInvoiceLoan {
  loanId: string
  loanNumber: string | null
  invoiceId: string | null
  status: string | null
  friendlyStatus: string
  requestedAmount: number | null
  sanctionedAmount: number | null
  disbursedAmount: number | null
  outstandingAmount: number | null
  /** LMS interest due when available (e.g. totalNormalInterestDue). */
  interestAmount?: number | null
  totalRepayable: number | null
  totalRepaid: number | null
  dueDate: string | null
  repayable: boolean
}

export interface BorrowerInvoiceDiscounting {
  available: boolean
  message: string | null
  paymentMethod?: string | null
  paymentEnabled?: boolean
  hdfcAccountNo?: string | null
  hdfcIfscCode?: string | null
  paymentEligibility?: Array<Record<string, unknown>>
  createdInvoiceId?: string | null
  invoices: BorrowerInvoiceItem[]
  loans: BorrowerInvoiceLoan[]
}

export async function getInvoiceDiscounting(flowType?: string): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.get<BorrowerInvoiceDiscounting>('/borrower/invoice-discounting', {
    params: flowType ? { flowType } : undefined,
  })
  return data
}

export async function createInvoiceDiscountingInvoice(
  body: Record<string, unknown>,
): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.post<BorrowerInvoiceDiscounting>(
    '/borrower/invoice-discounting/invoices',
    body,
  )
  return data
}

export async function uploadInvoiceDigitalCopy(invoiceId: string, file: File): Promise<void> {
  const formData = new FormData()
  formData.append('file', file)
  await http.post(`/borrower/invoice-discounting/invoices/${invoiceId}/digital-invoice`, formData)
}

export async function acceptInvoice(invoiceId: string): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.post<BorrowerInvoiceDiscounting>(
    `/borrower/invoice-discounting/invoices/${invoiceId}/accept`,
  )
  return data
}

export async function deleteInvoiceDiscountingInvoice(
  invoiceId: string,
): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.delete<BorrowerInvoiceDiscounting>(
    `/borrower/invoice-discounting/invoices/${invoiceId}`,
  )
  return data
}

export async function requestInvoiceFinance(
  invoiceId: string,
  amount: number,
): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.post<BorrowerInvoiceDiscounting>(
    `/borrower/invoice-discounting/invoices/${invoiceId}/finance`,
    { amount },
  )
  return data
}

export async function getEarlyPayTodayParameter(
  subProgramId: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    '/borrower/invoice-discounting/early-pay/parameters/today',
    { params: { subProgramId } },
  )
  return data
}

export async function createEarlyPayRequest(body: {
  invoiceId: string
  epParameterId: string
  requestedAmount?: number
}): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.post<BorrowerInvoiceDiscounting>(
    '/borrower/invoice-discounting/early-pay/requests',
    body,
  )
  return data
}

export async function getLoanRepayments(loanId: string): Promise<BorrowerInvoiceRepayment[]> {
  const { data } = await http.get<BorrowerInvoiceRepayment[]>(
    `/borrower/invoice-discounting/loans/${loanId}/repayments`,
  )
  return data
}

export async function getInvoiceUnfinancedRepayments(
  invoiceId: string,
): Promise<BorrowerInvoiceRepayment[]> {
  const { data } = await http.get<BorrowerInvoiceRepayment[]>(
    `/borrower/invoice-discounting/invoices/${invoiceId}/unfinanced-repayments`,
  )
  return data
}

export interface BorrowerCreditNoteRow {
  id: string
  noteRefNo: string
  noteIssuedDate: string
  amountReceived: number
  amountUnallocated: number
  status: string
  remarks?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface BorrowerCreditNoteAllocationRow {
  id: string
  invoiceId: string
  allocatedAmount: number
  allocationType: string
  createdAt?: string | null
  invoiceNumber?: string | null
  balanceDueAmount?: number | null
  invoiceStatus?: string | null
  dueDate?: string | null
}

export async function listBorrowerCreditNotes(status?: string): Promise<BorrowerCreditNoteRow[]> {
  const { data } = await http.get<BorrowerCreditNoteRow[]>('/borrower/invoice-discounting/credit-notes', {
    params: status ? { status } : undefined,
  })
  return data
}

export async function getBorrowerCreditNote(
  id: string,
): Promise<{ creditNote: BorrowerCreditNoteRow; allocations: BorrowerCreditNoteAllocationRow[] }> {
  const { data } = await http.get<{
    creditNote: BorrowerCreditNoteRow
    allocations: BorrowerCreditNoteAllocationRow[]
  }>(`/borrower/invoice-discounting/credit-notes/${id}`)
  return data
}

export async function repayInvoiceLoan(
  loanId: string,
  amount: number,
): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.post<BorrowerInvoiceDiscounting>(
    `/borrower/invoice-discounting/loans/${loanId}/repay`,
    { amount },
  )
  return data
}

export interface PaymentCartLine {
  id: string
  invoiceId: string
  invoiceNumber?: string | null
  amountToPay: number
  /** LMS interest due when available (display only). */
  interestAmount?: number | null
}

export interface PayuInitiatePayload {
  baseUrl: string
  key: string
  txnid: string
  amount: string
  productinfo: string
  firstname: string
  email: string
  phone?: string
  udf1?: string
  hash: string
  surl: string
  furl: string
}

export async function getPaymentCart(): Promise<PaymentCartLine[]> {
  const { data } = await http.get<PaymentCartLine[]>('/borrower/invoice-discounting/payments/cart')
  return data
}

export async function getPaymentCartCount(): Promise<number> {
  const { data } = await http.get<number>('/borrower/invoice-discounting/payments/cart/count')
  return data
}

export async function addPaymentCartLine(invoiceId: string): Promise<void> {
  await http.post('/borrower/invoice-discounting/payments/cart/lines', { invoiceId })
}

export async function addPaymentCartBulk(invoiceIds: string[]): Promise<void> {
  await http.post('/borrower/invoice-discounting/payments/cart/lines/bulk', { invoiceIds })
}

export async function removePaymentCartLine(lineId: string): Promise<void> {
  await http.delete(`/borrower/invoice-discounting/payments/cart/lines/${lineId}`)
}

export async function initiatePayuPayment(): Promise<PayuInitiatePayload> {
  const { data } = await http.post<PayuInitiatePayload>(
    '/borrower/invoice-discounting/payments/payu/initiate',
  )
  return data
}
