'use client';

import { useState } from 'react';
import {
  GitBranch,
  Plus,
  Edit2,
  Trash2,
  CheckCircle,
  XCircle,
  Copy,
  ChevronDown,
  ChevronUp,
  Save,
  ArrowRight,
  Clock,
  AlertTriangle,
} from 'lucide-react';

type WorkflowStep = {
  stepType: string;
  label: string;
  provider: string;
  mandatory: boolean;
  slaHours: number;
  order: number;
  condition?: string;
};

type Workflow = {
  id: string;
  name: string;
  borrowerType: string;
  loanProduct: string;
  active: boolean;
  version: number;
  steps: WorkflowStep[];
  conditionalRules: { field: string; operator: string; value: string; action: string; targetStep: string }[];
  createdAt: string;
  updatedAt: string;
};

const STEP_TYPES = [
  'AADHAAR_OTP', 'PAN_VERIFY', 'GSTIN_VERIFY', 'BANK_PENNY_DROP',
  'FACE_MATCH', 'LIVENESS', 'VIDEO_KYC', 'UDYAM_VERIFY',
  'CIN_MCA21', 'AML_SCREENING', 'CERSAI_CHECK', 'CREDIT_PULL',
];

const MOCK_WORKFLOWS: Workflow[] = [
  {
    id: '1',
    name: 'Individual Personal Loan KYC',
    borrowerType: 'INDIVIDUAL',
    loanProduct: 'Personal Loan',
    active: true,
    version: 3,
    steps: [
      { stepType: 'AADHAAR_OTP', label: 'Aadhaar Verification', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 1 },
      { stepType: 'PAN_VERIFY', label: 'PAN Verification', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 2 },
      { stepType: 'FACE_MATCH', label: 'Face Match', provider: 'Hyperverge', mandatory: true, slaHours: 8, order: 3 },
      { stepType: 'LIVENESS', label: 'Liveness Check', provider: 'Hyperverge', mandatory: true, slaHours: 8, order: 4 },
      { stepType: 'BANK_PENNY_DROP', label: 'Bank Verification', provider: 'Authbridge', mandatory: true, slaHours: 12, order: 5 },
      { stepType: 'AML_SCREENING', label: 'AML Screening', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 6 },
    ],
    conditionalRules: [
      { field: 'loanAmount', operator: '>', value: '1000000', action: 'ADD_STEP', targetStep: 'VIDEO_KYC' },
    ],
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-04-10T14:30:00Z',
  },
  {
    id: '2',
    name: 'Company Business Loan KYC',
    borrowerType: 'COMPANY',
    loanProduct: 'Business Loan',
    active: true,
    version: 2,
    steps: [
      { stepType: 'CIN_MCA21', label: 'CIN / MCA21 Check', provider: 'Authbridge', mandatory: true, slaHours: 8, order: 1 },
      { stepType: 'GSTIN_VERIFY', label: 'GSTIN Verification', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 2 },
      { stepType: 'PAN_VERIFY', label: 'Director PAN Verify', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 3 },
      { stepType: 'AADHAAR_OTP', label: 'Director Aadhaar', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 4 },
      { stepType: 'FACE_MATCH', label: 'Director Face Match', provider: 'Hyperverge', mandatory: true, slaHours: 8, order: 5 },
      { stepType: 'AML_SCREENING', label: 'AML Screening', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 6 },
      { stepType: 'CERSAI_CHECK', label: 'CERSAI Check', provider: 'Authbridge', mandatory: false, slaHours: 24, order: 7 },
      { stepType: 'BANK_PENNY_DROP', label: 'Bank Verification', provider: 'Authbridge', mandatory: true, slaHours: 12, order: 8 },
    ],
    conditionalRules: [
      { field: 'loanAmount', operator: '>', value: '5000000', action: 'ADD_STEP', targetStep: 'VIDEO_KYC' },
      { field: 'borrowerAge', operator: '<', value: '25', action: 'ADD_STEP', targetStep: 'VIDEO_KYC' },
    ],
    createdAt: '2026-03-15T00:00:00Z',
    updatedAt: '2026-04-08T09:00:00Z',
  },
  {
    id: '3',
    name: 'Proprietor MSME Loan KYC',
    borrowerType: 'PROPRIETOR',
    loanProduct: 'MSME Loan',
    active: true,
    version: 1,
    steps: [
      { stepType: 'AADHAAR_OTP', label: 'Aadhaar Verification', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 1 },
      { stepType: 'PAN_VERIFY', label: 'PAN Verification', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 2 },
      { stepType: 'UDYAM_VERIFY', label: 'Udyam Registration', provider: 'Authbridge', mandatory: true, slaHours: 8, order: 3 },
      { stepType: 'GSTIN_VERIFY', label: 'GSTIN Verification', provider: 'Authbridge', mandatory: false, slaHours: 4, order: 4 },
      { stepType: 'FACE_MATCH', label: 'Face Match', provider: 'Hyperverge', mandatory: true, slaHours: 8, order: 5 },
      { stepType: 'BANK_PENNY_DROP', label: 'Bank Verification', provider: 'Authbridge', mandatory: true, slaHours: 12, order: 6 },
      { stepType: 'AML_SCREENING', label: 'AML Screening', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 7 },
    ],
    conditionalRules: [],
    createdAt: '2026-04-01T00:00:00Z',
    updatedAt: '2026-04-01T00:00:00Z',
  },
  {
    id: '4',
    name: 'Individual Home Loan KYC (Archived)',
    borrowerType: 'INDIVIDUAL',
    loanProduct: 'Home Loan',
    active: false,
    version: 1,
    steps: [
      { stepType: 'AADHAAR_OTP', label: 'Aadhaar Verification', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 1 },
      { stepType: 'PAN_VERIFY', label: 'PAN Verification', provider: 'Authbridge', mandatory: true, slaHours: 4, order: 2 },
    ],
    conditionalRules: [],
    createdAt: '2026-02-15T00:00:00Z',
    updatedAt: '2026-03-20T00:00:00Z',
  },
];

export default function WorkflowAdminPage() {
  const [workflows, setWorkflows] = useState<Workflow[]>(MOCK_WORKFLOWS);
  const [expandedId, setExpandedId] = useState<string | null>('1');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);

  const handleToggleActive = (id: string) => {
    setWorkflows(
      workflows.map((w) => (w.id === id ? { ...w, active: !w.active } : w))
    );
  };

  const handleDuplicate = (wf: Workflow) => {
    const dup: Workflow = {
      ...wf,
      id: String(workflows.length + 1),
      name: `${wf.name} (Copy)`,
      active: false,
      version: 1,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    setWorkflows([...workflows, dup]);
  };

  const totalSlaHours = (steps: WorkflowStep[]) =>
    steps.reduce((sum, s) => sum + s.slaHours, 0);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Workflow Configuration</h1>
          <p className="text-sm text-gray-500 mt-1">
            {workflows.length} workflows &middot;{' '}
            {workflows.filter((w) => w.active).length} active
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 flex items-center gap-2"
        >
          <Plus size={16} /> New Workflow
        </button>
      </div>

      {/* Workflow List */}
      <div className="space-y-4">
        {workflows.map((wf) => (
          <div
            key={wf.id}
            className={`bg-white rounded-xl border overflow-hidden transition-shadow ${
              wf.active ? 'border-gray-200' : 'border-gray-200 opacity-60'
            } ${expandedId === wf.id ? 'shadow-md' : 'shadow-sm'}`}
          >
            {/* Workflow Header */}
            <div
              className="px-5 py-4 flex items-center justify-between cursor-pointer hover:bg-gray-50"
              onClick={() => setExpandedId(expandedId === wf.id ? null : wf.id)}
            >
              <div className="flex items-center gap-4">
                <GitBranch size={20} className={wf.active ? 'text-blue-600' : 'text-gray-400'} />
                <div>
                  <h3 className="text-sm font-semibold text-gray-900">{wf.name}</h3>
                  <div className="flex items-center gap-3 mt-0.5">
                    <span className="text-xs text-gray-500">
                      {wf.borrowerType} &middot; {wf.loanProduct}
                    </span>
                    <span className="text-xs text-gray-400">v{wf.version}</span>
                    <span className="text-xs text-gray-400">{wf.steps.length} steps</span>
                    <span className="text-xs text-gray-400 flex items-center gap-0.5">
                      <Clock size={10} /> {totalSlaHours(wf.steps)}h total SLA
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-3">
                {wf.active ? (
                  <span className="px-2.5 py-1 bg-green-100 text-green-700 rounded-full text-xs font-medium">Active</span>
                ) : (
                  <span className="px-2.5 py-1 bg-gray-100 text-gray-500 rounded-full text-xs font-medium">Inactive</span>
                )}
                {expandedId === wf.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
              </div>
            </div>

            {/* Expanded Content */}
            {expandedId === wf.id && (
              <div className="px-5 pb-5 border-t border-gray-100">
                {/* Step Pipeline */}
                <div className="py-4">
                  <h4 className="text-xs font-medium text-gray-500 uppercase mb-3">KYC Steps Pipeline</h4>
                  <div className="flex items-center gap-1 overflow-x-auto pb-2">
                    {wf.steps.map((step, idx) => (
                      <div key={step.stepType} className="flex items-center gap-1">
                        <div className={`px-3 py-2 rounded-lg border text-xs min-w-[140px] ${
                          step.mandatory ? 'bg-blue-50 border-blue-200' : 'bg-gray-50 border-gray-200'
                        }`}>
                          <div className="flex items-center justify-between mb-1">
                            <span className="font-mono text-[10px] text-gray-400">#{step.order}</span>
                            {!step.mandatory && (
                              <span className="text-[10px] text-yellow-600 font-medium">Optional</span>
                            )}
                          </div>
                          <p className="font-medium text-gray-800">{step.label}</p>
                          <div className="flex items-center justify-between mt-1">
                            <span className="text-[10px] text-gray-500">{step.provider}</span>
                            <span className="text-[10px] text-gray-500 flex items-center gap-0.5">
                              <Clock size={8} /> {step.slaHours}h
                            </span>
                          </div>
                        </div>
                        {idx < wf.steps.length - 1 && (
                          <ArrowRight size={14} className="text-gray-300 shrink-0" />
                        )}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Conditional Rules */}
                {wf.conditionalRules.length > 0 && (
                  <div className="py-3 border-t border-gray-100">
                    <h4 className="text-xs font-medium text-gray-500 uppercase mb-2">Conditional Rules</h4>
                    <div className="space-y-2">
                      {wf.conditionalRules.map((rule, idx) => (
                        <div key={idx} className="flex items-center gap-2 text-xs bg-yellow-50 border border-yellow-200 rounded-lg px-3 py-2">
                          <AlertTriangle size={12} className="text-yellow-600 shrink-0" />
                          <span className="text-gray-700">
                            If <code className="bg-white px-1 py-0.5 rounded font-mono text-blue-700">{rule.field}</code>{' '}
                            {rule.operator}{' '}
                            <code className="bg-white px-1 py-0.5 rounded font-mono text-blue-700">{rule.value}</code>{' '}
                            then <span className="font-medium text-yellow-800">{rule.action.replace(/_/g, ' ')}</span>:{' '}
                            <code className="bg-white px-1 py-0.5 rounded font-mono text-purple-700">{rule.targetStep}</code>
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Actions */}
                <div className="flex items-center justify-between pt-3 border-t border-gray-100">
                  <p className="text-[10px] text-gray-400">
                    Updated: {new Date(wf.updatedAt).toLocaleDateString('en-IN', { dateStyle: 'medium' })}
                  </p>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleDuplicate(wf)}
                      className="px-3 py-1.5 text-xs bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200 flex items-center gap-1"
                    >
                      <Copy size={12} /> Duplicate
                    </button>
                    <button
                      onClick={() => handleToggleActive(wf.id)}
                      className={`px-3 py-1.5 text-xs rounded-lg flex items-center gap-1 ${
                        wf.active
                          ? 'bg-red-50 text-red-600 hover:bg-red-100'
                          : 'bg-green-50 text-green-600 hover:bg-green-100'
                      }`}
                    >
                      {wf.active ? <><XCircle size={12} /> Deactivate</> : <><CheckCircle size={12} /> Activate</>}
                    </button>
                    <button className="px-3 py-1.5 text-xs bg-blue-600 text-white rounded-lg hover:bg-blue-700 flex items-center gap-1">
                      <Edit2 size={12} /> Edit Steps
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Create Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-6 space-y-4">
            <h2 className="text-lg font-bold text-gray-900">Create New Workflow</h2>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Workflow Name</label>
                <input type="text" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" placeholder="e.g. Partnership LAP KYC" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Borrower Type</label>
                  <select className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm">
                    <option value="INDIVIDUAL">Individual</option>
                    <option value="PROPRIETOR">Proprietor</option>
                    <option value="PARTNERSHIP">Partnership</option>
                    <option value="COMPANY">Company</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Loan Product</label>
                  <select className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm">
                    <option>Personal Loan</option>
                    <option>Business Loan</option>
                    <option>Home Loan</option>
                    <option>Vehicle Loan</option>
                    <option>MSME Loan</option>
                    <option>Gold Loan</option>
                    <option>LAP</option>
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Initial Steps</label>
                <p className="text-xs text-gray-500 mb-2">Select the KYC steps for this workflow</p>
                <div className="grid grid-cols-2 gap-2">
                  {STEP_TYPES.map((st) => (
                    <label key={st} className="flex items-center gap-2 text-xs text-gray-700 cursor-pointer">
                      <input type="checkbox" className="rounded border-gray-300" defaultChecked={['AADHAAR_OTP', 'PAN_VERIFY', 'FACE_MATCH', 'AML_SCREENING'].includes(st)} />
                      {st.replace(/_/g, ' ')}
                    </label>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setShowCreateModal(false)}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={() => setShowCreateModal(false)}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 flex items-center justify-center gap-1"
              >
                <Save size={14} /> Create Workflow
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
