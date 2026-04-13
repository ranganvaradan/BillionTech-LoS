'use client';

import { useEffect, useState } from 'react';
import { Plus, GitBranch, CheckCircle, XCircle, Settings } from 'lucide-react';
import { workflowApi } from '@/lib/api';
import type { WorkflowConfig } from '@/types';
import { getBorrowerLabel } from '@/lib/utils';

const MOCK_WORKFLOWS: WorkflowConfig[] = [
  {
    id: 'wf1', name: 'Individual Term Loan KYC', borrowerType: 'INDIVIDUAL', loanProduct: 'Term Loan',
    active: true, version: 3,
    steps: [
      { step: 'AADHAAR_OTP', provider: 'AUTHBRIDGE', mandatory: true, order: 1 },
      { step: 'PAN_VERIFY', provider: 'AUTHBRIDGE', mandatory: true, order: 2 },
      { step: 'MOBILE_OTP', provider: 'AUTHBRIDGE', mandatory: true, order: 3 },
      { step: 'FACE_MATCH', provider: 'HYPERVERGE', mandatory: true, order: 4 },
      { step: 'BANK_PENNY_DROP', provider: 'AUTHBRIDGE', mandatory: true, order: 5 },
      { step: 'CKYC_DOWNLOAD', provider: 'AUTHBRIDGE', mandatory: false, order: 6 },
      { step: 'AML_SCREENING', provider: 'AUTHBRIDGE', mandatory: true, order: 7 },
    ],
    createdAt: '2026-04-01T00:00:00Z', updatedAt: '2026-04-10T00:00:00Z',
  },
  {
    id: 'wf2', name: 'Business Loan KYC (Company)', borrowerType: 'COMPANY', loanProduct: 'Business Loan',
    active: true, version: 2,
    steps: [
      { step: 'PAN_VERIFY', provider: 'AUTHBRIDGE', mandatory: true, order: 1 },
      { step: 'GSTIN_VERIFY', provider: 'AUTHBRIDGE', mandatory: true, order: 2 },
      { step: 'CIN_MCA21', provider: 'AUTHBRIDGE', mandatory: true, order: 3 },
      { step: 'BANK_PENNY_DROP', provider: 'AUTHBRIDGE', mandatory: true, order: 4 },
      { step: 'AML_SCREENING', provider: 'AUTHBRIDGE', mandatory: true, order: 5 },
      { step: 'FACE_MATCH', provider: 'HYPERVERGE', mandatory: true, order: 6 },
    ],
    createdAt: '2026-04-01T00:00:00Z', updatedAt: '2026-04-08T00:00:00Z',
  },
  {
    id: 'wf3', name: 'Proprietor Loan KYC', borrowerType: 'PROPRIETOR', loanProduct: 'Business Loan',
    active: false, version: 1,
    steps: [
      { step: 'AADHAAR_OTP', provider: 'AUTHBRIDGE', mandatory: true, order: 1 },
      { step: 'PAN_VERIFY', provider: 'AUTHBRIDGE', mandatory: true, order: 2 },
      { step: 'UDYAM_VERIFY', provider: 'AUTHBRIDGE', mandatory: true, order: 3 },
      { step: 'GSTIN_VERIFY', provider: 'AUTHBRIDGE', mandatory: false, order: 4 },
      { step: 'BANK_PENNY_DROP', provider: 'AUTHBRIDGE', mandatory: true, order: 5 },
    ],
    createdAt: '2026-04-01T00:00:00Z', updatedAt: '2026-04-05T00:00:00Z',
  },
];

export default function WorkflowsPage() {
  const [workflows, setWorkflows] = useState<WorkflowConfig[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    workflowApi.list()
      .then(setWorkflows)
      .catch(() => setWorkflows(MOCK_WORKFLOWS))
      .finally(() => setLoading(false));
  }, []);

  const wfs = workflows.length > 0 ? workflows : MOCK_WORKFLOWS;

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">KYC Workflows</h1>
          <p className="text-sm text-slate-500 mt-0.5">Configure verification step sequences for different loan types</p>
        </div>
        <button className="bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-primary-hover flex items-center gap-2">
          <Plus size={16} /> New Workflow
        </button>
      </div>

      <div className="grid gap-4">
        {wfs.map((wf) => (
          <div key={wf.id} className="bg-card-bg rounded-xl border border-border p-5">
            <div className="flex items-start justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${wf.active ? 'bg-green-100' : 'bg-slate-100'}`}>
                  <GitBranch size={18} className={wf.active ? 'text-green-600' : 'text-slate-400'} />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-semibold text-slate-900">{wf.name}</h3>
                    {wf.active ? (
                      <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full font-medium">Active</span>
                    ) : (
                      <span className="text-xs bg-slate-100 text-slate-500 px-2 py-0.5 rounded-full font-medium">Inactive</span>
                    )}
                  </div>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {getBorrowerLabel(wf.borrowerType)} &middot; {wf.loanProduct} &middot; v{wf.version}
                  </p>
                </div>
              </div>
              <button className="p-2 text-slate-400 hover:text-slate-600">
                <Settings size={16} />
              </button>
            </div>

            {/* Steps pipeline */}
            <div className="flex items-center gap-1 overflow-x-auto pb-1">
              {wf.steps.map((step, i) => (
                <div key={i} className="flex items-center gap-1 shrink-0">
                  <div className={`px-3 py-1.5 rounded-lg text-xs font-medium ${
                    step.mandatory ? 'bg-blue-50 text-blue-700 border border-blue-200' : 'bg-slate-50 text-slate-600 border border-slate-200'
                  }`}>
                    {step.step.replace(/_/g, ' ')}
                    <span className="text-[10px] ml-1 opacity-60">({step.provider})</span>
                  </div>
                  {i < wf.steps.length - 1 && (
                    <svg width="16" height="8" className="text-slate-300 shrink-0">
                      <path d="M0 4 L12 4 M8 0 L12 4 L8 8" fill="none" stroke="currentColor" strokeWidth="1.5" />
                    </svg>
                  )}
                </div>
              ))}
            </div>

            <p className="text-xs text-slate-400 mt-3">
              {wf.steps.length} steps &middot; {wf.steps.filter((s) => s.mandatory).length} mandatory
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
