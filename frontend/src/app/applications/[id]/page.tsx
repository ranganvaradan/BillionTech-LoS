'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  ArrowLeft,
  FileText,
  Shield,
  CreditCard,
  Clock,
  Upload,
  Play,
  CheckCircle,
  XCircle,
  AlertTriangle,
} from 'lucide-react';
import { StatusBadge, LoadingSpinner } from '@/components/ui/StatusBadge';
import { applicationApi, kycApi, documentApi, auditApi, transactionApi, creditApi } from '@/lib/api';
import { formatCurrency, formatDate, formatDateTime, getBorrowerLabel, STATUS_CONFIG } from '@/lib/utils';
import type { LoanApplication, KycStepResult, DocumentInfo, AuditEvent, Transaction, CreditDecisionResult, ApplicationStatus } from '@/types';

type TabType = 'info' | 'kyc' | 'documents' | 'credit' | 'transactions' | 'audit';

const MOCK_APP: LoanApplication = {
  id: '1', applicationNumber: 'LOS-IND-20260413-00001', customerId: 'c1',
  borrowerType: 'INDIVIDUAL', loanProduct: 'Term Loan', status: 'KYC_IN_PROGRESS',
  requestedAmount: 500000, interestRate: 12.5, tenureMonths: 36,
  personalInfo: { fullName: 'Rahul Sharma', email: 'rahul@example.com', mobile: '9876543210', panNumber: 'ABCDE1234F' },
  financialInfo: { monthlyIncome: 75000, existingEmi: 10000, employmentType: 'SALARIED' },
  createdAt: '2026-04-13T08:30:00Z', updatedAt: '2026-04-13T09:15:00Z',
};

const VALID_TRANSITIONS: Record<string, ApplicationStatus[]> = {
  DRAFT: ['CONSENT_PENDING', 'WITHDRAWN'],
  CONSENT_PENDING: ['KYC_IN_PROGRESS', 'WITHDRAWN'],
  KYC_IN_PROGRESS: ['UNDERWRITING', 'ON_HOLD', 'WITHDRAWN'],
  KYC_FAILED: ['KYC_IN_PROGRESS', 'WITHDRAWN'],
  UNDERWRITING: ['APPROVED', 'REJECTED', 'ON_HOLD'],
  APPROVED: ['SANCTION_ISSUED', 'REJECTED', 'ON_HOLD'],
  SANCTION_ISSUED: ['ESIGN_PENDING', 'ON_HOLD'],
  ESIGN_PENDING: ['DISBURSEMENT_PENDING', 'ON_HOLD'],
  DISBURSEMENT_PENDING: ['DISBURSED', 'ON_HOLD'],
  ON_HOLD: ['KYC_IN_PROGRESS', 'UNDERWRITING', 'APPROVED', 'SANCTION_ISSUED', 'ESIGN_PENDING', 'DISBURSEMENT_PENDING', 'WITHDRAWN'],
};

export default function ApplicationDetailPage() {
  const params = useParams();
  const router = useRouter();
  const applicationId = params.id as string;

  const [app, setApp] = useState<LoanApplication | null>(null);
  const [activeTab, setActiveTab] = useState<TabType>('info');
  const [loading, setLoading] = useState(true);
  const [kycResults, setKycResults] = useState<KycStepResult[]>([]);
  const [documents, setDocuments] = useState<DocumentInfo[]>([]);
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [creditResult, setCreditResult] = useState<CreditDecisionResult | null>(null);
  const [transitioning, setTransitioning] = useState(false);

  useEffect(() => {
    async function fetchApp() {
      try {
        const data = await applicationApi.get(applicationId);
        setApp(data);
      } catch {
        setApp(MOCK_APP);
      } finally {
        setLoading(false);
      }
    }
    fetchApp();
  }, [applicationId]);

  useEffect(() => {
    if (!app) return;
    if (activeTab === 'kyc') {
      kycApi.getResults(applicationId).then(setKycResults).catch(() => setKycResults([]));
    } else if (activeTab === 'documents') {
      documentApi.list(applicationId).then(setDocuments).catch(() => setDocuments([]));
    } else if (activeTab === 'audit') {
      auditApi.getTrail(applicationId).then((d) => setAuditEvents(d.content)).catch(() => setAuditEvents([]));
    } else if (activeTab === 'transactions') {
      transactionApi.getHistory(applicationId).then((d) => setTransactions(d.content)).catch(() => setTransactions([]));
    }
  }, [activeTab, app, applicationId]);

  const handleTransition = async (newStatus: ApplicationStatus) => {
    setTransitioning(true);
    try {
      const updated = await applicationApi.transition(applicationId, newStatus);
      setApp(updated);
    } catch {
      alert('Failed to transition status');
    } finally {
      setTransitioning(false);
    }
  };

  const handleCreditEvaluation = async () => {
    try {
      const result = await creditApi.evaluate(applicationId);
      setCreditResult(result);
    } catch {
      alert('Failed to run credit evaluation');
    }
  };

  if (loading) return <LoadingSpinner />;
  if (!app) return <p>Application not found</p>;

  const nextStatuses = VALID_TRANSITIONS[app.status] || [];
  const personalInfo = (app.personalInfo || {}) as Record<string, unknown>;
  const financialInfo = (app.financialInfo || {}) as Record<string, unknown>;

  const TABS: { id: TabType; label: string; icon: React.ReactNode }[] = [
    { id: 'info', label: 'Application Info', icon: <FileText size={14} /> },
    { id: 'kyc', label: 'KYC', icon: <Shield size={14} /> },
    { id: 'documents', label: 'Documents', icon: <Upload size={14} /> },
    { id: 'credit', label: 'Credit Decision', icon: <CreditCard size={14} /> },
    { id: 'transactions', label: 'Transactions', icon: <CreditCard size={14} /> },
    { id: 'audit', label: 'Audit Trail', icon: <Clock size={14} /> },
  ];

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-3">
          <button onClick={() => router.back()} className="p-2 text-slate-400 hover:text-slate-600">
            <ArrowLeft size={18} />
          </button>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold text-slate-900">{app.applicationNumber}</h1>
              <StatusBadge status={app.status} />
            </div>
            <p className="text-sm text-slate-500 mt-0.5">
              {getBorrowerLabel(app.borrowerType)} &middot; {app.loanProduct} &middot; Created {formatDate(app.createdAt)}
            </p>
          </div>
        </div>
        {/* Status Transitions */}
        {nextStatuses.length > 0 && (
          <div className="flex items-center gap-2">
            {nextStatuses.map((status) => {
              const config = STATUS_CONFIG[status];
              return (
                <button
                  key={status}
                  onClick={() => handleTransition(status)}
                  disabled={transitioning}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors disabled:opacity-50 ${config.bg} ${config.color} border-current/20 hover:opacity-80`}
                >
                  {config.label}
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-card-bg rounded-xl border border-border p-4">
          <p className="text-xs text-slate-500">Requested</p>
          <p className="text-lg font-bold text-slate-900 mt-1">{formatCurrency(app.requestedAmount)}</p>
        </div>
        <div className="bg-card-bg rounded-xl border border-border p-4">
          <p className="text-xs text-slate-500">Approved</p>
          <p className="text-lg font-bold text-green-700 mt-1">{app.approvedAmount ? formatCurrency(app.approvedAmount) : '—'}</p>
        </div>
        <div className="bg-card-bg rounded-xl border border-border p-4">
          <p className="text-xs text-slate-500">Interest Rate</p>
          <p className="text-lg font-bold text-slate-900 mt-1">{app.interestRate ? `${app.interestRate}%` : '—'}</p>
        </div>
        <div className="bg-card-bg rounded-xl border border-border p-4">
          <p className="text-xs text-slate-500">Tenure</p>
          <p className="text-lg font-bold text-slate-900 mt-1">{app.tenureMonths ? `${app.tenureMonths} months` : '—'}</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="bg-card-bg rounded-xl border border-border">
        <div className="flex border-b border-border overflow-x-auto">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-1.5 px-5 py-3 text-sm font-medium whitespace-nowrap transition-colors ${
                activeTab === tab.id
                  ? 'text-primary border-b-2 border-primary'
                  : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {tab.icon} {tab.label}
            </button>
          ))}
        </div>

        <div className="p-5">
          {activeTab === 'info' && (
            <div className="grid grid-cols-2 gap-6">
              <div>
                <h3 className="text-sm font-semibold text-slate-900 mb-3">Personal Information</h3>
                <dl className="space-y-2 text-sm">
                  {Object.entries(personalInfo).map(([key, val]) => (
                    <div key={key} className="flex">
                      <dt className="w-36 text-slate-500 capitalize">{key.replace(/([A-Z])/g, ' $1').trim()}</dt>
                      <dd className="text-slate-900 font-medium">{String(val ?? '—')}</dd>
                    </div>
                  ))}
                </dl>
              </div>
              <div>
                <h3 className="text-sm font-semibold text-slate-900 mb-3">Financial Information</h3>
                <dl className="space-y-2 text-sm">
                  {Object.entries(financialInfo).map(([key, val]) => (
                    <div key={key} className="flex">
                      <dt className="w-36 text-slate-500 capitalize">{key.replace(/([A-Z])/g, ' $1').trim()}</dt>
                      <dd className="text-slate-900 font-medium">{typeof val === 'number' ? formatCurrency(val) : String(val ?? '—')}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            </div>
          )}

          {activeTab === 'kyc' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-slate-900">KYC Verification Steps</h3>
                <button className="text-xs bg-primary text-white px-3 py-1.5 rounded-lg hover:bg-primary-hover flex items-center gap-1">
                  <Play size={12} /> Execute Workflow
                </button>
              </div>
              {kycResults.length === 0 ? (
                <p className="text-sm text-slate-500 py-6 text-center">No KYC steps executed yet</p>
              ) : (
                <div className="space-y-2">
                  {kycResults.map((result) => (
                    <div key={result.id} className="flex items-center justify-between p-3 rounded-lg border border-border">
                      <div className="flex items-center gap-3">
                        {result.outcome === 'SUCCESS' ? (
                          <CheckCircle size={16} className="text-green-600" />
                        ) : result.outcome === 'FAILURE' ? (
                          <XCircle size={16} className="text-red-600" />
                        ) : (
                          <AlertTriangle size={16} className="text-amber-500" />
                        )}
                        <div>
                          <p className="text-sm font-medium text-slate-900">{result.stepType.replace(/_/g, ' ')}</p>
                          <p className="text-xs text-slate-500">Provider: {result.provider} &middot; Attempt #{result.attemptNumber}</p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p className={`text-xs font-medium ${result.outcome === 'SUCCESS' ? 'text-green-600' : result.outcome === 'FAILURE' ? 'text-red-600' : 'text-amber-600'}`}>
                          {result.outcome}{result.overridden ? ' (Overridden)' : ''}
                        </p>
                        {result.confidenceScore > 0 && (
                          <p className="text-xs text-slate-500">Score: {(result.confidenceScore * 100).toFixed(0)}%</p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {activeTab === 'documents' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-slate-900">Documents</h3>
                <button className="text-xs bg-primary text-white px-3 py-1.5 rounded-lg hover:bg-primary-hover flex items-center gap-1">
                  <Upload size={12} /> Upload Document
                </button>
              </div>
              {documents.length === 0 ? (
                <p className="text-sm text-slate-500 py-6 text-center">No documents uploaded yet</p>
              ) : (
                <div className="space-y-2">
                  {documents.map((doc) => (
                    <div key={doc.id} className="flex items-center justify-between p-3 rounded-lg border border-border">
                      <div className="flex items-center gap-3">
                        <FileText size={16} className="text-slate-400" />
                        <div>
                          <p className="text-sm font-medium text-slate-900">{doc.documentType.replace(/_/g, ' ')}</p>
                          <p className="text-xs text-slate-500">{doc.fileName} &middot; {(doc.fileSize / 1024).toFixed(0)} KB</p>
                        </div>
                      </div>
                      <p className="text-xs text-slate-500">{formatDate(doc.createdAt)}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {activeTab === 'credit' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-slate-900">Credit Decision</h3>
                <button onClick={handleCreditEvaluation} className="text-xs bg-primary text-white px-3 py-1.5 rounded-lg hover:bg-primary-hover">
                  Run Evaluation
                </button>
              </div>
              {creditResult ? (
                <div className="space-y-4">
                  <div className={`p-4 rounded-lg border-2 ${
                    creditResult.decision === 'APPROVED' ? 'border-green-200 bg-green-50' :
                    creditResult.decision === 'REJECTED' ? 'border-red-200 bg-red-50' :
                    'border-amber-200 bg-amber-50'
                  }`}>
                    <p className={`text-lg font-bold ${
                      creditResult.decision === 'APPROVED' ? 'text-green-700' :
                      creditResult.decision === 'REJECTED' ? 'text-red-700' : 'text-amber-700'
                    }`}>{creditResult.decision}</p>
                    <div className="grid grid-cols-3 gap-4 mt-3 text-sm">
                      <div><span className="text-slate-500">Risk Score:</span> <span className="font-medium">{creditResult.riskScore}/100</span></div>
                      <div><span className="text-slate-500">Credit Score:</span> <span className="font-medium">{creditResult.creditScore}</span></div>
                      <div><span className="text-slate-500">Recommended Rate:</span> <span className="font-medium">{creditResult.recommendedRate}%</span></div>
                    </div>
                  </div>
                  {creditResult.reasons.length > 0 && (
                    <div>
                      <h4 className="text-xs font-semibold text-slate-700 mb-2">Reasons</h4>
                      <ul className="space-y-1">
                        {creditResult.reasons.map((r, i) => (
                          <li key={i} className="text-sm text-red-700 flex items-start gap-2">
                            <XCircle size={14} className="mt-0.5 shrink-0" /> {r}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {creditResult.conditions.length > 0 && (
                    <div>
                      <h4 className="text-xs font-semibold text-slate-700 mb-2">Conditions</h4>
                      <ul className="space-y-1">
                        {creditResult.conditions.map((c, i) => (
                          <li key={i} className="text-sm text-amber-700 flex items-start gap-2">
                            <AlertTriangle size={14} className="mt-0.5 shrink-0" /> {c}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              ) : (
                <p className="text-sm text-slate-500 py-6 text-center">Click &ldquo;Run Evaluation&rdquo; to get the credit decision</p>
              )}
            </div>
          )}

          {activeTab === 'transactions' && (
            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-slate-900">Transaction History</h3>
              {transactions.length === 0 ? (
                <p className="text-sm text-slate-500 py-6 text-center">No transactions yet</p>
              ) : (
                <div className="space-y-2">
                  {transactions.map((txn) => (
                    <div key={txn.id} className="flex items-center justify-between p-3 rounded-lg border border-border">
                      <div>
                        <p className="text-sm font-medium text-slate-900">{txn.transactionType}</p>
                        <p className="text-xs text-slate-500">Ref: {txn.referenceNumber} {txn.utrNumber && `| UTR: ${txn.utrNumber}`}</p>
                      </div>
                      <div className="text-right">
                        <p className="text-sm font-bold text-slate-900">{formatCurrency(txn.amount)}</p>
                        <p className={`text-xs ${txn.status === 'COMPLETED' ? 'text-green-600' : 'text-amber-600'}`}>{txn.status}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {activeTab === 'audit' && (
            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-slate-900">Audit Trail</h3>
              {auditEvents.length === 0 ? (
                <p className="text-sm text-slate-500 py-6 text-center">No audit events recorded</p>
              ) : (
                <div className="space-y-2">
                  {auditEvents.map((event) => (
                    <div key={event.id} className="flex items-start gap-3 p-3 rounded-lg border border-border">
                      <Clock size={14} className="text-slate-400 mt-0.5 shrink-0" />
                      <div className="flex-1">
                        <div className="flex items-center justify-between">
                          <p className="text-sm font-medium text-slate-900">{event.action}</p>
                          <p className="text-xs text-slate-500">{formatDateTime(event.createdAt)}</p>
                        </div>
                        <p className="text-xs text-slate-600 mt-0.5">{event.description}</p>
                        <p className="text-xs text-slate-400 mt-0.5">Type: {event.eventType}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
