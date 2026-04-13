// LOS Platform TypeScript Types

export type ApplicationStatus =
  | 'DRAFT'
  | 'CONSENT_PENDING'
  | 'KYC_IN_PROGRESS'
  | 'KYC_FAILED'
  | 'UNDERWRITING'
  | 'APPROVED'
  | 'REJECTED'
  | 'SANCTION_ISSUED'
  | 'ESIGN_PENDING'
  | 'DISBURSEMENT_PENDING'
  | 'DISBURSED'
  | 'WITHDRAWN'
  | 'ON_HOLD';

export type BorrowerType = 'INDIVIDUAL' | 'PROPRIETOR' | 'PARTNERSHIP' | 'COMPANY';

export type KycStepType =
  | 'AADHAAR_OTP'
  | 'PAN_VERIFY'
  | 'GSTIN_VERIFY'
  | 'UDYAM_VERIFY'
  | 'CIN_MCA21'
  | 'BANK_PENNY_DROP'
  | 'MOBILE_OTP'
  | 'CKYC_DOWNLOAD'
  | 'AML_SCREENING'
  | 'FACE_MATCH'
  | 'BUREAU_PULL';

export type StepOutcome = 'PENDING' | 'SUCCESS' | 'FAILURE' | 'MANUAL_REVIEW' | 'SKIPPED' | 'ERROR';

export interface LoanApplication {
  id: string;
  applicationNumber: string;
  customerId: string;
  borrowerType: BorrowerType;
  loanProduct: string;
  status: ApplicationStatus;
  requestedAmount: number;
  approvedAmount?: number;
  interestRate?: number;
  tenureMonths?: number;
  personalInfo?: Record<string, unknown>;
  businessInfo?: Record<string, unknown>;
  financialInfo?: Record<string, unknown>;
  collateralInfo?: Record<string, unknown>;
  remarks?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateApplicationRequest {
  borrowerType: BorrowerType;
  loanProduct: string;
  requestedAmount: number;
  interestRate?: number;
  tenureMonths?: number;
  personalInfo?: Record<string, unknown>;
  businessInfo?: Record<string, unknown>;
  financialInfo?: Record<string, unknown>;
}

export interface KycStepResult {
  id: string;
  applicationId: string;
  stepType: KycStepType;
  provider: string;
  outcome: StepOutcome;
  confidenceScore: number;
  parsedData?: Record<string, unknown>;
  transactionId?: string;
  errorMessage?: string;
  overridden: boolean;
  overrideReason?: string;
  attemptNumber: number;
  createdAt: string;
  completedAt?: string;
}

export interface DocumentInfo {
  id: string;
  applicationId: string;
  documentType: string;
  fileName: string;
  fileSize: number;
  contentType: string;
  checksum: string;
  storageKey: string;
  createdAt: string;
}

export interface WorkflowConfig {
  id: string;
  name: string;
  borrowerType: BorrowerType;
  loanProduct: string;
  active: boolean;
  version: number;
  steps: WorkflowStep[];
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowStep {
  step: string;
  provider: string;
  mandatory: boolean;
  order: number;
  timeout?: number;
}

export interface Transaction {
  id: string;
  applicationId: string;
  transactionType: string;
  amount: number;
  status: string;
  referenceNumber: string;
  utrNumber?: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
  completedAt?: string;
}

export interface AuditEvent {
  id: string;
  applicationId: string;
  eventType: string;
  action: string;
  performedBy?: string;
  previousState?: Record<string, unknown>;
  newState?: Record<string, unknown>;
  description: string;
  createdAt: string;
}

export interface CreditDecisionResult {
  decision: string;
  riskScore: number;
  creditScore: number;
  reasons: string[];
  conditions: string[];
  requestedAmount: number;
  recommendedRate: number;
}

export interface DashboardSummary {
  totalApplications: number;
  statusCounts: Record<ApplicationStatus, number>;
  todayCount: number;
  thisMonthCount: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginUser {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  enabled: boolean;
  twoFactorEnabled: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: LoginUser;
  requiresTwoFactor?: boolean;
}
