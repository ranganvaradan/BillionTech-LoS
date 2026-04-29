import axios from 'axios';

const api = axios.create({
  baseURL: '/api/v1/bank-statements',
  headers: { 'Content-Type': 'application/json' },
});

// Attach API key if stored
api.interceptors.request.use((config) => {
  const apiKey = localStorage.getItem('bsa_api_key');
  if (apiKey) {
    config.headers['X-API-Key'] = apiKey;
  }
  return config;
});

export interface UploadResponse {
  statementId: number;
  fileName: string;
  status: string;
  message: string;
}

export interface StatementResponse {
  id: number;
  fileName: string;
  accountHolderName: string | null;
  accountNumberMasked: string | null;
  bankName: string | null;
  bankCode: string | null;
  ifscCode: string | null;
  branchName: string | null;
  accountType: string | null;
  statementFromDate: string | null;
  statementToDate: string | null;
  openingBalance: number | null;
  closingBalance: number | null;
  totalTransactions: number;
  totalCreditAmount: number | null;
  totalDebitAmount: number | null;
  parsingStatus: string;
  parsingError: string | null;
  tamperCheckStatus: string | null;
  applicationId: string | null;
  batchId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionResponse {
  id: number;
  transactionDate: string;
  valueDate: string | null;
  narration: string;
  referenceNumber: string | null;
  debitAmount: number;
  creditAmount: number;
  runningBalance: number | null;
  category: string | null;
  subCategory: string | null;
  channel: string | null;
  counterpartyName: string | null;
  isBounce: boolean;
  isReversal: boolean;
  isCircular: boolean;
}

export interface AnalysisResponse {
  id: number;
  statementId: number;
  avgBankBalance: number | null;
  minBalance: number | null;
  maxBalance: number | null;
  balanceVolatility: number | null;
  detectedSalaryAmount: number | null;
  salaryFrequency: string | null;
  salaryDayOfMonth: number | null;
  salaryConfidence: number | null;
  totalIncome: number | null;
  nonSalaryIncome: number | null;
  imputedIncome: number | null;
  incomeStabilityScore: number | null;
  emiCount: number;
  totalEmiAmount: number | null;
  foir: number | null;
  totalObligations: number | null;
  rentAmount: number | null;
  insuranceAmount: number | null;
  totalCredits: number | null;
  totalDebits: number | null;
  netCashFlow: number | null;
  creditDebitRatio: number | null;
  cashFlowStability: number | null;
  bounceCount: number;
  bounceAmount: number | null;
  circularTxnCount: number;
  cashDepositRatio: number | null;
  cashWithdrawalRatio: number | null;
  redFlags: Array<{ type: string; severity: string; description: string }>;
  creditworthinessScore: number | null;
  incomeConfidenceScore: number | null;
  repaymentCapacityScore: number | null;
  topCreditSources: Array<{ name: string; amount: number }>;
  topDebitDestinations: Array<{ name: string; amount: number }>;
  analysisCompletedAt: string | null;
  analysisVersion: string;
}

export interface SummaryResponse {
  statementId: number;
  bankName: string | null;
  accountHolderName: string | null;
  accountNumberMasked: string | null;
  statementPeriod: string;
  avgBankBalance: number | null;
  detectedSalaryAmount: number | null;
  totalIncome: number | null;
  foir: number | null;
  creditworthinessScore: number | null;
  repaymentCapacityScore: number | null;
  totalTransactions: number;
  totalCredits: number | null;
  totalDebits: number | null;
  bounceCount: number;
  redFlagCount: number;
  monthlySummaries: MonthlySummaryResponse[];
  categoryBreakdown: Record<string, number>;
}

export interface MonthlySummaryResponse {
  id: number;
  year: number;
  month: number;
  monthLabel: string;
  openingBalance: number | null;
  closingBalance: number | null;
  avgEodBalance: number | null;
  totalCredits: number | null;
  totalDebits: number | null;
  netCashFlow: number | null;
  creditCount: number;
  debitCount: number;
  salaryAmount: number | null;
  emiAmount: number | null;
  bounceCount: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const bankStatementApi = {
  upload: (file: File, password?: string, applicationId?: string) => {
    const formData = new FormData();
    formData.append('file', file);
    if (password) formData.append('password', password);
    if (applicationId) formData.append('applicationId', applicationId);
    return api.post<UploadResponse>('/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  list: (page = 0, size = 20) =>
    api.get<PageResponse<StatementResponse>>('', { params: { page, size } }),

  get: (id: number) => api.get<StatementResponse>(`/${id}`),

  getTransactions: (id: number, page = 0, size = 100) =>
    api.get<PageResponse<TransactionResponse>>(`/${id}/transactions`, { params: { page, size } }),

  getAnalysis: (id: number) => api.get<AnalysisResponse>(`/${id}/analysis`),

  getSummary: (id: number) => api.get<SummaryResponse>(`/${id}/summary`),

  getMonthly: (id: number) => api.get<MonthlySummaryResponse[]>(`/${id}/monthly`),

  getRedFlags: (id: number) => api.get<Array<{ type: string; severity: string; description: string }>>(`/${id}/red-flags`),

  delete: (id: number) => api.delete(`/${id}`),

  getSupportedBanks: () => api.get<Array<{ code: string; name: string; formats: string[] }>>('/supported-banks'),
};

export default api;
