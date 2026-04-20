import apiClient from './api-client';
import type {
  LoanApplication,
  CreateApplicationRequest,
  KycStepResult,
  DocumentInfo,
  WorkflowConfig,
  Transaction,
  AuditEvent,
  CreditDecisionResult,
  DashboardSummary,
  KycWorkflowResponse,
  BureauPullResponse,
  UnderwritingResponse,
  Page,
  LoginRequest,
  LoginResponse,
  KycOutcomeResponse,
} from '@/types';

// ─── Auth ────────────────────────────────────────────────
export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<LoginResponse>('/api/v1/auth/login', data).then((r) => r.data),

  verify2fa: (token: string, code: string) =>
    apiClient
      .post<LoginResponse>('/api/v1/auth/verify-2fa', { token, totpCode: code })
      .then((r) => r.data),
};

// ─── Applications ────────────────────────────────────────
export const applicationApi = {
  create: (data: CreateApplicationRequest) =>
    apiClient.post<LoanApplication>('/api/v1/applications', data).then((r) => r.data),

  get: (id: string) =>
    apiClient.get<LoanApplication>(`/api/v1/applications/${id}`).then((r) => r.data),

  list: (params?: { status?: string; borrowerType?: string; page?: number; size?: number }) =>
    apiClient.get<Page<LoanApplication>>('/api/v1/applications', { params }).then((r) => r.data),

  update: (id: string, data: Partial<LoanApplication>) =>
    apiClient.put<LoanApplication>(`/api/v1/applications/${id}`, data).then((r) => r.data),

  transition: (id: string, newStatus: string, remarks?: string) =>
    apiClient
      .post<LoanApplication>(`/api/v1/applications/${id}/transition`, null, {
        params: { newStatus, remarks },
      })
      .then((r) => r.data),

  getDashboard: () =>
    apiClient.get<DashboardSummary>('/api/v1/applications/dashboard/summary').then((r) => r.data),
};



export const flowApi = {
  submit: (applicationId: string) =>
    apiClient.post<LoanApplication>(`/api/v1/flow/${applicationId}/submit`).then((r) => r.data),

  runKyc: (applicationId: string, payload: Record<string, unknown>) =>
    apiClient
      .post<KycWorkflowResponse>(`/api/v1/flow/${applicationId}/kyc`, payload)
      .then((r) => r.data),

  pullBureau: (applicationId: string) =>
    apiClient
      .post<BureauPullResponse>(`/api/v1/flow/${applicationId}/bureau`)
      .then((r) => r.data),

  underwrite: (applicationId: string) =>
    apiClient
      .post<UnderwritingResponse>(`/api/v1/flow/${applicationId}/underwrite`)
      .then((r) => r.data),
};

// ─── KYC ─────────────────────────────────────────────────
export const kycApi = {
  executeStep: (applicationId: string, stepType: string, payload: Record<string, unknown>) =>
    apiClient
      .post<KycStepResult>(`/api/v1/kyc/${applicationId}/step/${stepType}`, payload)
      .then((r) => r.data),

  getResults: (applicationId: string) =>
    apiClient.get<KycStepResult[]>(`/api/v1/kyc/${applicationId}/results`).then((r) => r.data),

  getOutcome: (applicationId: string) =>
    apiClient.get<KycOutcomeResponse>(`/api/v1/kyc/${applicationId}/outcome`).then((r) => r.data),

  overrideStep: (stepResultId: string, reason: string) =>
    apiClient
      .post<KycStepResult>(`/api/v1/kyc/step/${stepResultId}/override`, null, {
        params: { reason },
      })
      .then((r) => r.data),

  executeWorkflow: (applicationId: string, payload: Record<string, unknown>) =>
    apiClient
      .post<KycStepResult[]>(`/api/v1/kyc/${applicationId}/workflow`, payload)
      .then((r) => r.data),
};

// ─── Documents ───────────────────────────────────────────
export const documentApi = {
  upload: (applicationId: string, documentType: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient
      .post<DocumentInfo>(`/api/v1/documents/${applicationId}/upload`, formData, {
        params: { documentType },
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },

  list: (applicationId: string) =>
    apiClient.get<DocumentInfo[]>(`/api/v1/documents/${applicationId}`).then((r) => r.data),

  download: (documentId: string) =>
    apiClient
      .get(`/api/v1/documents/download/${documentId}`, { responseType: 'blob' })
      .then((r) => r.data),

  delete: (documentId: string) =>
    apiClient.delete(`/api/v1/documents/${documentId}`).then((r) => r.data),

  checklistStatus: (applicationId: string) =>
    apiClient
      .get<{ applicationId: string; checklistComplete: boolean }>(
        `/api/v1/documents/${applicationId}/checklist`
      )
      .then((r) => r.data),
};

// ─── Workflows ───────────────────────────────────────────
export const workflowApi = {
  list: () => apiClient.get<WorkflowConfig[]>('/api/v1/workflows').then((r) => r.data),

  getActive: (borrowerType: string, loanProduct: string) =>
    apiClient
      .get<WorkflowConfig>('/api/v1/workflows/active', {
        params: { borrowerType, loanProduct },
      })
      .then((r) => r.data),

  create: (data: Partial<WorkflowConfig>) =>
    apiClient.post<WorkflowConfig>('/api/v1/workflows', data).then((r) => r.data),

  activate: (id: string) =>
    apiClient.post(`/api/v1/workflows/${id}/activate`).then((r) => r.data),

  deactivate: (id: string) =>
    apiClient.post(`/api/v1/workflows/${id}/deactivate`).then((r) => r.data),
};

// ─── Credit ──────────────────────────────────────────────
export const creditApi = {
  evaluate: (applicationId: string) =>
    apiClient
      .post<CreditDecisionResult>(`/api/v1/credit/${applicationId}/evaluate`)
      .then((r) => r.data),
};

// ─── Transactions ────────────────────────────────────────
export const transactionApi = {
  disburse: (applicationId: string, amount: number, metadata?: Record<string, unknown>) =>
    apiClient
      .post<Transaction>(`/api/v1/transactions/${applicationId}/disburse`, { amount, metadata })
      .then((r) => r.data),

  recordRepayment: (applicationId: string, amount: number, utrNumber: string) =>
    apiClient
      .post<Transaction>(`/api/v1/transactions/${applicationId}/repayment`, {
        amount,
        utrNumber,
      })
      .then((r) => r.data),

  getHistory: (applicationId: string, page?: number, size?: number) =>
    apiClient
      .get<Page<Transaction>>(`/api/v1/transactions/${applicationId}`, {
        params: { page, size },
      })
      .then((r) => r.data),

  getOutstanding: (applicationId: string) =>
    apiClient
      .get<{ applicationId: string; outstandingBalance: number }>(
        `/api/v1/transactions/${applicationId}/outstanding`
      )
      .then((r) => r.data),
};

// ─── Audit ───────────────────────────────────────────────
export const auditApi = {
  getTrail: (applicationId: string, page?: number, size?: number) =>
    apiClient
      .get<Page<AuditEvent>>(`/api/v1/audit/${applicationId}`, {
        params: { page, size },
      })
      .then((r) => r.data),
};

// ─── Integrations ────────────────────────────────────────
export const integrationApi = {
  initiateESign: (applicationId: string, documentKey: string, signerInfo: Record<string, unknown>) =>
    apiClient
      .post(`/api/v1/integrations/esign/${applicationId}`, { documentKey, signerInfo })
      .then((r) => r.data),

  pullBureau: (borrowerInfo: Record<string, unknown>) =>
    apiClient.post(`/api/v1/integrations/bureau`, borrowerInfo).then((r) => r.data),

  testConnectivity: (providerName: string) =>
    apiClient.get(`/api/v1/integrations/connectivity/${providerName}`).then((r) => r.data),
};
