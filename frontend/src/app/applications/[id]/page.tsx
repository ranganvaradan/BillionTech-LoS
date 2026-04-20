'use client';

import { useEffect, useState } from 'react';
import type * as React from 'react';
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
import { applicationApi, kycApi, documentApi, auditApi, transactionApi, creditApi, flowApi } from '@/lib/api';
import { formatCurrency, formatDate, formatDateTime, getBorrowerLabel } from '@/lib/utils';
import type { LoanApplication, KycStepResult, DocumentInfo, AuditEvent, Transaction, CreditDecisionResult, ApplicationStatus, KycOutcomeResponse } from '@/types';

type TabType = 'info' | 'kyc' | 'documents' | 'credit' | 'transactions' | 'audit';

type TabDef = {
  id: TabType;
  label: string;
  icon: React.ReactNode;
};

export default function ApplicationDetailPage() {
  const params = useParams();
  const router = useRouter();
  const applicationId = params.id as string;

  const [app, setApp] = useState<LoanApplication | null>(null);
  const [activeTab, setActiveTab] = useState<TabType>('info');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>('');
  const [kycResults, setKycResults] = useState<KycStepResult[]>([]);
  const [kycError, setKycError] = useState<string>('');
  const [kycOutcome, setKycOutcome] = useState<KycOutcomeResponse | null>(null);
  const [documents, setDocuments] = useState<DocumentInfo[]>([]);
  const [documentsError, setDocumentsError] = useState<string>('');
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [auditError, setAuditError] = useState<string>('');
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [transactionsError, setTransactionsError] = useState<string>('');
  const [creditResult, setCreditResult] = useState<CreditDecisionResult | null>(null);
  const [workflowBusy, setWorkflowBusy] = useState(false);

  useEffect(() => {
    async function fetchApp() {
      try {
        setLoadError('');
        const data = await applicationApi.get(applicationId);
        setApp(data);
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : 'Failed to load application.';
        setLoadError(msg);
        setApp(null);
      } finally {
        setLoading(false);
      }
    }
    fetchApp();
  }, [applicationId]);

  useEffect(() => {
    if (!app) return;
    setKycError('');
    kycApi
      .getResults(applicationId)
      .then(setKycResults)
      .catch((e: unknown) => {
        const msg = e instanceof Error ? e.message : 'Failed to load KYC results.';
        setKycError(msg);
        setKycResults([]);
      });

    kycApi
      .getOutcome(applicationId)
      .then(setKycOutcome)
      .catch((e: unknown) => {
        const msg = e instanceof Error ? e.message : 'Failed to compute KYC outcome.';
        setKycError(msg);
        setKycOutcome(null);
      });
  }, [app, applicationId]);

  useEffect(() => {
    if (!app) return;
    if (activeTab === 'kyc') {
      // KYC results are also loaded in the background for action gating.
    } else if (activeTab === 'documents') {
      setDocumentsError('');
      documentApi
        .list(applicationId)
        .then(setDocuments)
        .catch((e: unknown) => {
          const msg = e instanceof Error ? e.message : 'Failed to load documents.';
          setDocumentsError(msg);
          setDocuments([]);
        });
    } else if (activeTab === 'audit') {
      setAuditError('');
      auditApi
        .getTrail(applicationId)
        .then((d) => setAuditEvents(d.content))
        .catch((e: unknown) => {
          const msg = e instanceof Error ? e.message : 'Failed to load audit trail.';
          setAuditError(msg);
          setAuditEvents([]);
        });
    } else if (activeTab === 'transactions') {
      setTransactionsError('');
      transactionApi
        .getHistory(applicationId)
        .then((d) => setTransactions(d.content))
        .catch((e: unknown) => {
          const msg = e instanceof Error ? e.message : 'Failed to load transactions.';
          setTransactionsError(msg);
          setTransactions([]);
        });
    }
  }, [activeTab, app, applicationId]);


  const refreshApplication = async () => {
    const latest = await applicationApi.get(applicationId);
    setApp(latest);
    return latest;
  };

  const buildWorkflowPayload = () => ({
    panNumber: app?.personalInfo?.panNumber,
    aadhaarNumber: app?.personalInfo?.aadhaarNumber,
    mobile: app?.personalInfo?.mobile,
    fullName: app?.personalInfo?.fullName,
    dateOfBirth: app?.personalInfo?.dateOfBirth,
    gstin: app?.businessInfo?.gstin,
    accountNumber: app?.financialInfo?.accountNumber,
    ifscCode: app?.financialInfo?.ifscCode,
  });

  const handleCreditEvaluation = async () => {
    try {
      const result = await creditApi.evaluate(applicationId);
      setCreditResult(result);
    } catch {
      alert('Failed to run credit evaluation');
    }
  };

  const handleSubmitToWorkflow = async () => {
    setWorkflowBusy(true);
    try {
      await flowApi.submit(applicationId);
      await refreshApplication();
      alert('Application submitted and moved into KYC.');
    } catch {
      alert('Failed to submit application to workflow');
    } finally {
      setWorkflowBusy(false);
    }
  };

  const handleExecuteWorkflow = async () => {
    setWorkflowBusy(true);
    try {
      if (app?.status === 'DRAFT') throw new Error('Submit the application before running KYC.');
      const results = await kycApi.executeWorkflow(applicationId, buildWorkflowPayload());
      setKycResults(results);
      try {
        const outcome = await kycApi.getOutcome(applicationId);
        setKycOutcome(outcome);
        if (String(outcome.outcome).toUpperCase() === 'FAIL') {
          alert('KYC completed with failures. Review the failed steps before proceeding.');
        }
      } catch {
        // outcome computation failed; keep results visible
      }
      const latest = await refreshApplication();
      if (latest.status === 'KYC_FAILED') {
        alert('KYC completed with failures. Review the failed steps before proceeding.');
      }
    } catch {
      alert('Failed to execute KYC workflow');
    } finally {
      setWorkflowBusy(false);
    }
  };

  const handlePullBureau = async () => {
    setWorkflowBusy(true);
    try {
      await flowApi.pullBureau(applicationId);
      await refreshApplication();
      alert('Bureau pull completed.');
    } catch {
      alert('Failed to pull bureau report');
    } finally {
      setWorkflowBusy(false);
    }
  };

  const handleRunUnderwriting = async () => {
    setWorkflowBusy(true);
    try {
      const result = await flowApi.underwrite(applicationId);
      const mapped: CreditDecisionResult = {
        decision: result.decision,
        riskScore: result.riskScore,
        creditScore: result.creditScore,
        reasons: result.reasons,
        conditions: result.conditions,
        requestedAmount: result.requestedAmount,
        recommendedRate: result.recommendedRate,
      };
      setCreditResult(mapped);
      await refreshApplication();
    } catch {
      alert('Failed to run underwriting');
    } finally {
      setWorkflowBusy(false);
    }
  };

  if (loading) return <LoadingSpinner />;
  if (loadError) {
    return (
      <div className="bg-card-bg rounded-xl border border-border p-6">
        <h1 className="text-xl font-bold text-slate-900">Application</h1>
        <p className="text-sm text-slate-500 mt-1">This page couldn’t load.</p>
        <p className="text-sm text-red-600 mt-3">{loadError}</p>
        <div className="mt-4">
          <Link href="/applications" className="text-sm text-primary hover:underline font-medium">
            Go to Applications
          </Link>
        </div>
      </div>
    );
  }
  if (!app) return <p>Application not found</p>;

  const personalInfo = (app.personalInfo || {}) as Record<string, unknown>;
  const financialInfo = (app.financialInfo || {}) as Record<string, unknown>;

  const bureauDone = typeof app.bureauScore === 'number' && app.bureauScore > 0;
  const underwritingDone = !!app.creditDecision;
  const computedKycOutcome = kycOutcome?.outcome || 'INCOMPLETE';

  const allowedActions: Array<'SUBMIT' | 'RUN_KYC' | 'PULL_BUREAU' | 'RUN_UNDERWRITING'> = [];
  if (app.status === 'DRAFT') allowedActions.push('SUBMIT');
  if (app.status === 'KYC_IN_PROGRESS') allowedActions.push('RUN_KYC');
  if (computedKycOutcome === 'PASS' && !bureauDone) allowedActions.push('PULL_BUREAU');
  if (computedKycOutcome === 'PASS' && bureauDone && !underwritingDone) allowedActions.push('RUN_UNDERWRITING');

  const TABS: TabDef[] = [
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
      </div>

      {(allowedActions.length > 0 || computedKycOutcome === 'FAIL') && (
        <div className="bg-card-bg rounded-xl border border-border p-4 flex flex-wrap items-center gap-3">
          <span className="text-sm font-medium text-slate-700">Workflow actions</span>
          {allowedActions.includes('SUBMIT') && (
            <button
              onClick={handleSubmitToWorkflow}
              disabled={workflowBusy}
              className="text-xs bg-primary text-white px-3 py-1.5 rounded-lg hover:bg-primary-hover disabled:opacity-50 flex items-center gap-1"
            >
              <Play size={12} /> Submit Application
            </button>
          )}
          {allowedActions.includes('RUN_KYC') && (
            <button
              onClick={handleExecuteWorkflow}
              disabled={workflowBusy}
              className="text-xs bg-primary text-white px-3 py-1.5 rounded-lg hover:bg-primary-hover disabled:opacity-50 flex items-center gap-1"
            >
              <Play size={12} /> Run KYC Workflow
            </button>
          )}
          {allowedActions.includes('PULL_BUREAU') && (
            <button
              onClick={handlePullBureau}
              disabled={workflowBusy}
              className="text-xs border border-border px-3 py-1.5 rounded-lg hover:bg-slate-50 disabled:opacity-50"
            >
              Pull Bureau
            </button>
          )}
          {allowedActions.includes('RUN_UNDERWRITING') && (
            <button
              onClick={handleRunUnderwriting}
              disabled={workflowBusy}
              className="text-xs border border-border px-3 py-1.5 rounded-lg hover:bg-slate-50 disabled:opacity-50"
            >
              Run Underwriting
            </button>
          )}

          {computedKycOutcome === 'FAIL' && (
            <span className="text-xs text-red-700 bg-red-50 border border-red-200 px-2.5 py-1 rounded-lg">
              KYC failed — downstream steps are blocked.
            </span>
          )}

          {(computedKycOutcome === 'INCOMPLETE' && app.status === 'KYC_IN_PROGRESS') && (
            <span className="text-xs text-amber-700 bg-amber-50 border border-amber-200 px-2.5 py-1 rounded-lg">
              KYC incomplete — complete all mandatory steps before Bureau/Underwriting.
            </span>
          )}
        </div>
      )}

      <div className="bg-slate-50 rounded-xl border border-border p-4">
        <h3 className="text-xs font-semibold text-slate-700">Debug</h3>
        <div className="grid grid-cols-2 lg:grid-cols-3 gap-3 mt-3 text-xs">
          <div>
            <p className="text-slate-500">Application ID</p>
            <p className="font-mono text-slate-900 break-all">{app.id}</p>
          </div>
          <div>
            <p className="text-slate-500">Status</p>
            <p className="text-slate-900 font-medium">{app.status}</p>
          </div>
          <div>
            <p className="text-slate-500">KYC Outcome (computed)</p>
            <p className="text-slate-900 font-medium">{computedKycOutcome}</p>
          </div>
          <div>
            <p className="text-slate-500">Bureau</p>
            <p className="text-slate-900 font-medium">{bureauDone ? `DONE (score ${app.bureauScore})` : 'NOT_AVAILABLE'}</p>
          </div>
          <div>
            <p className="text-slate-500">Underwriting</p>
            <p className="text-slate-900 font-medium">{underwritingDone ? `DONE (${app.creditDecision})` : 'NOT_RUN'}</p>
          </div>
          <div>
            <p className="text-slate-500">Allowed next actions</p>
            <p className="text-slate-900 font-medium">{allowedActions.join(', ') || 'NONE'}</p>
          </div>
          <div>
            <p className="text-slate-500">Live data</p>
            <p className="text-slate-900 font-medium">YES (no mock fallback)</p>
          </div>
        </div>
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
                {allowedActions.includes('RUN_KYC') && (
                  <button onClick={handleExecuteWorkflow} disabled={workflowBusy} className="text-xs bg-primary text-white px-3 py-1.5 rounded-lg hover:bg-primary-hover disabled:opacity-50 flex items-center gap-1">
                    <Play size={12} /> {workflowBusy ? "Working..." : "Execute Workflow"}
                  </button>
                )}
              </div>
              {kycError && (
                <div className="text-xs text-red-700 bg-red-50 border border-red-200 px-3 py-2 rounded-lg">
                  {kycError}
                </div>
              )}
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
              {documentsError && (
                <div className="text-xs text-red-700 bg-red-50 border border-red-200 px-3 py-2 rounded-lg">
                  {documentsError}
                </div>
              )}
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
              {transactionsError && (
                <div className="text-xs text-red-700 bg-red-50 border border-red-200 px-3 py-2 rounded-lg">
                  {transactionsError}
                </div>
              )}
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
              {auditError && (
                <div className="text-xs text-red-700 bg-red-50 border border-red-200 px-3 py-2 rounded-lg">
                  {auditError}
                </div>
              )}
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
