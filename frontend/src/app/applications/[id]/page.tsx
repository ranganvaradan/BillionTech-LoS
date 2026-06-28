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
import type { LoanApplication, KycStepResult, DocumentInfo, ManualKycReview, AuditEvent, Transaction, CreditDecisionResult, ApplicationStatus, KycOutcomeResponse, KycStepType, ManualKycDecision } from '@/types';

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
  const [manualReviews, setManualReviews] = useState<ManualKycReview[]>([]);
  const [manualError, setManualError] = useState<string>('');
  const [kycDocuments, setKycDocuments] = useState<DocumentInfo[]>([]);
  const [manualDraft, setManualDraft] = useState<Record<string, { data: Record<string, unknown>; remarks: string; decision: ManualKycDecision | '' }>>({});
  const [userRoles, setUserRoles] = useState<string[]>([]);
  const [documents, setDocuments] = useState<DocumentInfo[]>([]);
  const [documentsError, setDocumentsError] = useState<string>('');
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [auditError, setAuditError] = useState<string>('');
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [transactionsError, setTransactionsError] = useState<string>('');
  const [creditResult, setCreditResult] = useState<CreditDecisionResult | null>(null);
  const [workflowBusy, setWorkflowBusy] = useState(false);

  const [manualBureauScore, setManualBureauScore] = useState<string>('');
  const [manualBureauRemarks, setManualBureauRemarks] = useState<string>('');
  const [manualBureauDocId, setManualBureauDocId] = useState<string>('');

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
    try {
      const raw = localStorage.getItem('los_user');
      if (raw) {
        const parsed = JSON.parse(raw) as { roles?: string[] };
        setUserRoles(Array.isArray(parsed.roles) ? parsed.roles : []);
      }
    } catch {
      setUserRoles([]);
    }
  }, []);

  useEffect(() => {
    if (!app) return;
    if (activeTab === 'kyc') {
      setManualError('');
      kycApi
        .getManualReviews(applicationId)
        .then((rows) => {
          setManualReviews(rows);
          setManualDraft((prev) => {
            const next = { ...prev };
            for (const r of rows) {
              next[r.stepType] = {
                data: (r.data || {}) as Record<string, unknown>,
                remarks: r.remarks || '',
                decision: (r.decision as ManualKycDecision | undefined) || '',
              };
            }
            return next;
          });
        })
        .catch((e: unknown) => {
          const msg = e instanceof Error ? e.message : 'Failed to load manual KYC reviews.';
          setManualError(msg);
          setManualReviews([]);
        });

      documentApi
        .list(applicationId)
        .then(setKycDocuments)
        .catch((e: unknown) => {
          const msg = e instanceof Error ? e.message : 'Failed to load documents.';
          setManualError(msg);
          setKycDocuments([]);
        });
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
      const result = await flowApi.runKyc(applicationId, buildWorkflowPayload());
      setKycResults(result.results || []);
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

  const canManualKyc = userRoles.some((r) => ['ADMIN', 'CREDIT_MANAGER', 'CREDIT_OFFICER', 'CREDIT_ANALYST'].includes(r));
  const canManualBureau = canManualKyc;

  const phase1ManualSteps: Array<{ stepType: KycStepType; label: string; docType: string; fields: Array<{ key: string; label: string }> }> = [
    {
      stepType: 'PAN_VERIFY',
      label: 'PAN',
      docType: 'PAN_CARD',
      fields: [
        { key: 'panNumber', label: 'PAN Number' },
        { key: 'fullName', label: 'Full Name' },
        { key: 'dateOfBirth', label: 'Date of Birth' },
      ],
    },
    {
      stepType: 'AADHAAR_OTP',
      label: 'Aadhaar',
      docType: 'AADHAAR',
      fields: [
        { key: 'aadhaarNumber', label: 'Aadhaar Number' },
        { key: 'fullName', label: 'Full Name' },
        { key: 'dateOfBirth', label: 'Date of Birth' },
      ],
    },
    {
      stepType: 'BANK_PENNY_DROP',
      label: 'Bank Account',
      docType: 'BANK_STATEMENT',
      fields: [
        { key: 'accountNumber', label: 'Account Number' },
        { key: 'ifscCode', label: 'IFSC Code' },
        { key: 'accountHolderName', label: 'Account Holder Name' },
      ],
    },
    {
      stepType: 'CKYC_DOWNLOAD',
      label: 'Address Proof',
      docType: 'ADDRESS_PROOF',
      fields: [
        { key: 'addressLine1', label: 'Address Line 1' },
        { key: 'city', label: 'City' },
        { key: 'pincode', label: 'Pincode' },
      ],
    },
  ];

  const updateManualDraft = (stepType: KycStepType, key: string, value: string) => {
    setManualDraft((prev) => {
      const existing = prev[stepType] || { data: {}, remarks: '', decision: '' };
      return {
        ...prev,
        [stepType]: {
          ...existing,
          data: {
            ...existing.data,
            [key]: value,
          },
        },
      };
    });
  };

  const updateManualRemarks = (stepType: KycStepType, value: string) => {
    setManualDraft((prev) => {
      const existing = prev[stepType] || { data: {}, remarks: '', decision: '' };
      return {
        ...prev,
        [stepType]: {
          ...existing,
          remarks: value,
        },
      };
    });
  };

  const updateManualDecision = (stepType: KycStepType, value: ManualKycDecision | '') => {
    setManualDraft((prev) => {
      const existing = prev[stepType] || { data: {}, remarks: '', decision: '' };
      return {
        ...prev,
        [stepType]: {
          ...existing,
          decision: value,
        },
      };
    });
  };

  const validateDob = (value: string): string | null => {
    if (!value) return 'Date of birth is required';

    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return 'Invalid date';

    const today = new Date();
    const todayYmd = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
    const inputYmd = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    if (inputYmd > todayYmd) return 'Date of birth cannot be in the future';

    return null;
  };

  const handleUploadEvidence = async (stepType: KycStepType, docType: string, file: File) => {
    try {
      await documentApi.upload(applicationId, docType, file, stepType);
      const docs = await documentApi.list(applicationId);
      setKycDocuments(docs);
    } catch {
      alert('Failed to upload evidence document');
    }
  };

  const handleSaveManual = async (stepType: KycStepType) => {
    const draft = manualDraft[stepType] || { data: {}, remarks: '', decision: '' };
    try {
      await kycApi.saveManualReview(applicationId, stepType, {
        data: draft.data,
        remarks: draft.remarks,
        decision: draft.decision || undefined,
      });
      const rows = await kycApi.getManualReviews(applicationId);
      setManualReviews(rows);
      alert('Manual KYC saved');
    } catch {
      alert('Failed to save manual KYC');
    }
  };

  const handleOpenDocument = (documentId: string) => {
    window.open(`/api/v1/documents/download/${documentId}`, '_blank', 'noopener,noreferrer');
  };

  const handleUploadBureauReport = async (file: File) => {
    try {
      const uploaded = await documentApi.upload(applicationId, 'BUREAU_REPORT', file);
      setManualBureauDocId(uploaded.id);
      alert('Bureau report uploaded');
    } catch {
      alert('Failed to upload bureau report');
    }
  };

  const handleSaveManualBureau = async () => {
    const score = manualBureauScore ? Number(manualBureauScore) : undefined;
    if (!score || Number.isNaN(score) || score <= 0) {
      alert('Enter a valid bureau score');
      return;
    }

    try {
      const updated = await applicationApi.saveManualBureau(applicationId, {
        manualBureauScore: score,
        manualBureauRemarks: manualBureauRemarks || undefined,
        manualBureauDocumentId: manualBureauDocId || undefined,
      });
      setApp(updated);
      alert('Manual bureau override saved');
    } catch {
      alert('Failed to save manual bureau override');
    }
  };

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

              {canManualKyc && (
                <div className="space-y-3 pt-4">
                  <h3 className="text-sm font-semibold text-slate-900">Manual KYC (Phase 1)</h3>
                  {manualError && (
                    <div className="text-xs text-red-700 bg-red-50 border border-red-200 px-3 py-2 rounded-lg">
                      {manualError}
                    </div>
                  )}
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
                    {phase1ManualSteps.map((step) => {
                      const provider = kycResults.find((r) => r.stepType === step.stepType);
                      const existing = manualReviews.find((r) => r.stepType === step.stepType);
                      const docs = kycDocuments.filter((d) => d.kycStepType === step.stepType);
                      const draft = manualDraft[step.stepType] || { data: {}, remarks: '', decision: '' };

                      const dobValue = String((draft.data?.dateOfBirth as string | undefined) || '');
                      const needsDob = step.fields.some((f) => f.key === 'dateOfBirth');
                      const dobError = needsDob ? validateDob(dobValue) : null;
                      const disableSave = !!dobError;

                      return (
                        <div key={step.stepType} className="rounded-lg border border-border p-4 space-y-3">
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="text-sm font-semibold text-slate-900">{step.label}</p>
                              <p className="text-xs text-slate-500">
                                Provider: {provider ? `${provider.provider} (${provider.outcome})` : '—'}
                              </p>
                              <p className="text-xs text-slate-500">
                                Manual: {existing?.updatedAt ? `Updated ${formatDateTime(existing.updatedAt)}` : 'Not set'}
                              </p>
                            </div>

                            <div className="shrink-0">
                              <label className="text-xs bg-slate-100 hover:bg-slate-200 px-3 py-1.5 rounded-lg cursor-pointer inline-flex items-center gap-1">
                                <Upload size={12} /> Upload
                                <input
                                  type="file"
                                  className="hidden"
                                  onChange={(e) => {
                                    const f = e.target.files?.[0];
                                    if (!f) return;
                                    void handleUploadEvidence(step.stepType, step.docType, f);
                                    e.target.value = '';
                                  }}
                                />
                              </label>
                            </div>
                          </div>

                          <div className="space-y-2">
                            {step.fields.map((field) => (
                              <div key={field.key} className="grid grid-cols-3 gap-2 items-center">
                                <label className="text-xs text-slate-600 col-span-1">{field.label}</label>
                                <div className="col-span-2">
                                  <input
                                    type={field.key === 'dateOfBirth' ? 'date' : 'text'}
                                    value={String((draft.data?.[field.key] as string | undefined) || '')}
                                    onChange={(e) => {
                                      const next = e.target.value;
                                      updateManualDraft(step.stepType, field.key, next);
                                    }}
                                    className="w-full px-3 py-2 rounded-lg border border-border text-xs focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                                  />
                                  {field.key === 'dateOfBirth' && dobError && (
                                    <p className="mt-1 text-[11px] text-red-600">{dobError}</p>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>

                          <div>
                            <label className="text-xs text-slate-600">Decision</label>
                            <div className="mt-1 flex items-center gap-2">
                              <select
                                value={draft.decision}
                                onChange={(e) => updateManualDecision(step.stepType, e.target.value as ManualKycDecision | '')}
                                className="px-3 py-2 rounded-lg border border-border text-xs focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                              >
                                <option value="">Select decision</option>
                                <option value="VERIFIED">VERIFIED</option>
                                <option value="REJECTED">REJECTED</option>
                                <option value="NEEDS_REVIEW">NEEDS_REVIEW</option>
                              </select>
                              {existing?.decision && (
                                <span className="text-[11px] text-slate-500">Saved: {existing.decision}</span>
                              )}
                            </div>
                          </div>

                          <div>
                            <label className="text-xs text-slate-600">Remarks</label>
                            <textarea
                              value={draft.remarks}
                              onChange={(e) => updateManualRemarks(step.stepType, e.target.value)}
                              rows={2}
                              className="w-full mt-1 px-3 py-2 rounded-lg border border-border text-xs focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                            />
                          </div>

                          <div className="space-y-1">
                            <p className="text-xs font-medium text-slate-700">Evidence</p>
                            {docs.length === 0 ? (
                              <p className="text-xs text-slate-500">No evidence uploaded</p>
                            ) : (
                              <div className="space-y-1">
                                {docs.map((d) => (
                                  <div key={d.id} className="flex items-center justify-between text-xs">
                                    <button
                                      type="button"
                                      onClick={() => handleOpenDocument(d.id)}
                                      className="text-slate-700 truncate hover:underline text-left"
                                      title="Open document"
                                    >
                                      {d.fileName}
                                    </button>
                                    <span className="text-slate-400">{formatDate(d.createdAt)}</span>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>

                          <div className="flex items-center justify-end">
                            <button
                              onClick={() => void handleSaveManual(step.stepType)}
                              disabled={disableSave}
                              className="text-xs bg-primary text-white px-3 py-1.5 rounded-lg hover:bg-primary-hover disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                              Save
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
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
                          <button
                            type="button"
                            onClick={() => handleOpenDocument(doc.id)}
                            className="text-xs text-slate-500 hover:underline text-left"
                            title="Open document"
                          >
                            {doc.fileName} &middot; {(doc.fileSize / 1024).toFixed(0)} KB
                          </button>
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

              {canManualBureau && (
                <div className="rounded-lg border border-border p-4 space-y-3">
                  <h4 className="text-sm font-semibold text-slate-900">Manual Bureau Upload / Override</h4>
                  <p className="text-xs text-slate-500">
                    Current provider score: {typeof app.bureauScore === 'number' ? app.bureauScore : '—'} | Manual score:{' '}
                    {typeof app.manualBureauScore === 'number' ? app.manualBureauScore : '—'}
                  </p>

                  <div className="flex items-center gap-3">
                    <label className="text-xs bg-slate-100 hover:bg-slate-200 px-3 py-1.5 rounded-lg cursor-pointer inline-flex items-center gap-1">
                      <Upload size={12} /> Upload Bureau Report
                      <input
                        type="file"
                        className="hidden"
                        onChange={(e) => {
                          const f = e.target.files?.[0];
                          if (!f) return;
                          void handleUploadBureauReport(f);
                          e.target.value = '';
                        }}
                      />
                    </label>
                    {manualBureauDocId && (
                      <button
                        type="button"
                        className="text-xs text-slate-600 hover:underline"
                        onClick={() => handleOpenDocument(manualBureauDocId)}
                      >
                        View uploaded report
                      </button>
                    )}
                    {app.manualBureauDocumentId && !manualBureauDocId && (
                      <button
                        type="button"
                        className="text-xs text-slate-600 hover:underline"
                        onClick={() => handleOpenDocument(app.manualBureauDocumentId!)}
                      >
                        View saved report
                      </button>
                    )}
                  </div>

                  <div className="grid grid-cols-3 gap-2 items-center">
                    <label className="text-xs text-slate-600 col-span-1">Bureau Score</label>
                    <input
                      type="number"
                      value={manualBureauScore}
                      onChange={(e) => setManualBureauScore(e.target.value)}
                      className="col-span-2 px-3 py-2 rounded-lg border border-border text-xs focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                      placeholder="e.g. 720"
                      min={1}
                    />
                  </div>

                  <div>
                    <label className="text-xs text-slate-600">Remarks</label>
                    <textarea
                      value={manualBureauRemarks}
                      onChange={(e) => setManualBureauRemarks(e.target.value)}
                      rows={2}
                      className="w-full mt-1 px-3 py-2 rounded-lg border border-border text-xs focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                      placeholder="Reason for manual override"
                    />
                  </div>

                  <div className="flex items-center justify-end">
                    <button
                      type="button"
                      onClick={() => void handleSaveManualBureau()}
                      className="text-xs bg-primary text-white px-3 py-1.5 rounded-lg hover:bg-primary-hover"
                    >
                      Save Manual Bureau
                    </button>
                  </div>
                </div>
              )}

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
