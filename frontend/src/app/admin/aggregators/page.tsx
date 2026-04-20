'use client';

import { useState } from 'react';
import {
  Link2,
  Plus,
  Edit2,
  Trash2,
  CheckCircle,
  XCircle,
  RefreshCw,
  Settings,
  Globe,
  Key,
  AlertTriangle,
} from 'lucide-react';

type Aggregator = {
  id: string;
  name: string;
  type: 'KYC' | 'BUREAU' | 'ESIGN' | 'AA' | 'NACH' | 'SMS' | 'EMAIL';
  provider: string;
  baseUrl: string;
  status: 'ACTIVE' | 'INACTIVE' | 'ERROR';
  lastHealthCheck: string;
  responseTimeMs: number;
  successRate: number;
  apiKeyConfigured: boolean;
  supportedSteps: string[];
};

const MOCK_AGGREGATORS: Aggregator[] = [
  {
    id: '1', name: 'Authbridge KYC', type: 'KYC', provider: 'Authbridge',
    baseUrl: 'https://api.authbridge.com/v2', status: 'ACTIVE',
    lastHealthCheck: '2026-04-13T10:25:00Z', responseTimeMs: 245, successRate: 99.2,
    apiKeyConfigured: true,
    supportedSteps: ['AADHAAR_OTP', 'PAN_VERIFY', 'GSTIN_VERIFY', 'BANK_PENNY_DROP', 'UDYAM_VERIFY', 'CIN_MCA21', 'AML_SCREENING'],
  },
  {
    id: '2', name: 'Hyperverge Face Match', type: 'KYC', provider: 'Hyperverge',
    baseUrl: 'https://api.hyperverge.co/v1', status: 'ACTIVE',
    lastHealthCheck: '2026-04-13T10:24:00Z', responseTimeMs: 890, successRate: 97.8,
    apiKeyConfigured: true,
    supportedSteps: ['FACE_MATCH', 'LIVENESS'],
  },
  {
    id: '3', name: 'Equifax Bureau', type: 'BUREAU', provider: 'Equifax',
    baseUrl: 'https://api.equifax.co.in/v1', status: 'ACTIVE',
    lastHealthCheck: '2026-04-13T10:23:00Z', responseTimeMs: 1200, successRate: 98.5,
    apiKeyConfigured: true,
    supportedSteps: ['CREDIT_PULL', 'SCORE_CHECK'],
  },
  {
    id: '4', name: 'eMudhra eSign', type: 'ESIGN', provider: 'eMudhra',
    baseUrl: 'https://api.emudhra.com/esign/v2', status: 'ACTIVE',
    lastHealthCheck: '2026-04-13T10:22:00Z', responseTimeMs: 560, successRate: 99.5,
    apiKeyConfigured: true,
    supportedSteps: ['KFS_SIGN', 'AGREEMENT_SIGN', 'SANCTION_LETTER_SIGN', 'NACH_SIGN'],
  },
  {
    id: '5', name: 'Finvu Account Aggregator', type: 'AA', provider: 'Finvu',
    baseUrl: 'https://api.finvu.in/aa/v1', status: 'INACTIVE',
    lastHealthCheck: '2026-04-12T18:00:00Z', responseTimeMs: 0, successRate: 0,
    apiKeyConfigured: false,
    supportedSteps: ['CONSENT_CREATE', 'DATA_FETCH', 'CONSENT_REVOKE'],
  },
  {
    id: '6', name: 'NPCI NACH', type: 'NACH', provider: 'NPCI',
    baseUrl: 'https://api.npci.org.in/nach/v1', status: 'ERROR',
    lastHealthCheck: '2026-04-13T10:20:00Z', responseTimeMs: 5000, successRate: 85.2,
    apiKeyConfigured: true,
    supportedSteps: ['MANDATE_CREATE', 'MANDATE_REGISTER', 'MANDATE_CANCEL'],
  },
  {
    id: '7', name: 'MSG91 SMS', type: 'SMS', provider: 'MSG91',
    baseUrl: 'https://api.msg91.com/v5', status: 'ACTIVE',
    lastHealthCheck: '2026-04-13T10:25:00Z', responseTimeMs: 180, successRate: 99.8,
    apiKeyConfigured: true,
    supportedSteps: ['OTP_SEND', 'NOTIFICATION_SMS'],
  },
  {
    id: '8', name: 'SendGrid Email', type: 'EMAIL', provider: 'SendGrid',
    baseUrl: 'https://api.sendgrid.com/v3', status: 'ACTIVE',
    lastHealthCheck: '2026-04-13T10:24:00Z', responseTimeMs: 320, successRate: 99.6,
    apiKeyConfigured: true,
    supportedSteps: ['NOTIFICATION_EMAIL', 'OTP_EMAIL'],
  },
];

const TYPE_COLORS: Record<string, string> = {
  KYC: 'bg-blue-100 text-blue-700',
  BUREAU: 'bg-purple-100 text-purple-700',
  ESIGN: 'bg-indigo-100 text-indigo-700',
  AA: 'bg-teal-100 text-teal-700',
  NACH: 'bg-orange-100 text-orange-700',
  SMS: 'bg-green-100 text-green-700',
  EMAIL: 'bg-pink-100 text-pink-700',
};

export default function AggregatorConfigPage() {
  const [aggregators, setAggregators] = useState<Aggregator[]>(MOCK_AGGREGATORS);
  const [typeFilter, setTypeFilter] = useState('');
  const [showAddModal, setShowAddModal] = useState(false);
  const [selectedAgg, setSelectedAgg] = useState<Aggregator | null>(null);

  const filtered = aggregators.filter((a) => !typeFilter || a.type === typeFilter);

  const activeCount = aggregators.filter((a) => a.status === 'ACTIVE').length;
  const errorCount = aggregators.filter((a) => a.status === 'ERROR').length;

  const handleToggleStatus = (id: string) => {
    setAggregators(
      aggregators.map((a) =>
        a.id === id
          ? { ...a, status: a.status === 'ACTIVE' ? 'INACTIVE' as const : 'ACTIVE' as const }
          : a
      )
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Aggregator Configuration</h1>
          <p className="text-sm text-gray-500 mt-1">
            {aggregators.length} integrations &middot;{' '}
            <span className="text-green-600">{activeCount} active</span>
            {errorCount > 0 && (
              <> &middot; <span className="text-red-600">{errorCount} errors</span></>
            )}
          </p>
        </div>
        <button
          onClick={() => setShowAddModal(true)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 flex items-center gap-2"
        >
          <Plus size={16} /> Add Aggregator
        </button>
      </div>

      {/* Type Filter Chips */}
      <div className="flex gap-2 flex-wrap">
        <button
          onClick={() => setTypeFilter('')}
          className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
            !typeFilter ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }`}
        >
          All ({aggregators.length})
        </button>
        {['KYC', 'BUREAU', 'ESIGN', 'AA', 'NACH', 'SMS', 'EMAIL'].map((type) => {
          const count = aggregators.filter((a) => a.type === type).length;
          if (count === 0) return null;
          return (
            <button
              key={type}
              onClick={() => setTypeFilter(typeFilter === type ? '' : type)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                typeFilter === type
                  ? 'bg-blue-600 text-white'
                  : `${TYPE_COLORS[type]} hover:opacity-80`
              }`}
            >
              {type} ({count})
            </button>
          );
        })}
      </div>

      {/* Aggregator Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {filtered.map((agg) => (
          <div
            key={agg.id}
            className={`bg-white rounded-xl border p-5 hover:shadow-md transition-shadow ${
              agg.status === 'ERROR'
                ? 'border-red-300 ring-1 ring-red-100'
                : agg.status === 'INACTIVE'
                ? 'border-gray-200 opacity-70'
                : 'border-gray-200'
            }`}
          >
            <div className="flex items-start justify-between mb-3">
              <div className="flex items-center gap-3">
                <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${TYPE_COLORS[agg.type]}`}>
                  <Link2 size={18} />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-gray-900">{agg.name}</h3>
                  <p className="text-xs text-gray-500">{agg.provider}</p>
                </div>
              </div>
              <div className="flex items-center gap-1.5">
                {agg.status === 'ACTIVE' && <CheckCircle size={14} className="text-green-500" />}
                {agg.status === 'INACTIVE' && <XCircle size={14} className="text-gray-400" />}
                {agg.status === 'ERROR' && <AlertTriangle size={14} className="text-red-500" />}
                <span className={`text-xs font-medium ${
                  agg.status === 'ACTIVE' ? 'text-green-600' : agg.status === 'ERROR' ? 'text-red-600' : 'text-gray-500'
                }`}>
                  {agg.status}
                </span>
              </div>
            </div>

            <div className="flex items-center gap-1 mb-3">
              <Globe size={12} className="text-gray-400" />
              <span className="text-xs text-gray-500 font-mono truncate">{agg.baseUrl}</span>
            </div>

            <div className="grid grid-cols-3 gap-3 mb-3">
              <div>
                <p className="text-xs text-gray-500">Response Time</p>
                <p className={`text-sm font-semibold ${
                  agg.responseTimeMs > 2000 ? 'text-red-600' : agg.responseTimeMs > 1000 ? 'text-yellow-600' : 'text-green-600'
                }`}>
                  {agg.responseTimeMs > 0 ? `${agg.responseTimeMs}ms` : '-'}
                </p>
              </div>
              <div>
                <p className="text-xs text-gray-500">Success Rate</p>
                <p className={`text-sm font-semibold ${
                  agg.successRate < 95 ? 'text-red-600' : agg.successRate < 99 ? 'text-yellow-600' : 'text-green-600'
                }`}>
                  {agg.successRate > 0 ? `${agg.successRate}%` : '-'}
                </p>
              </div>
              <div>
                <p className="text-xs text-gray-500">API Key</p>
                <p className="text-sm font-semibold">
                  {agg.apiKeyConfigured ? (
                    <span className="text-green-600 flex items-center gap-1"><Key size={12} /> Set</span>
                  ) : (
                    <span className="text-red-600">Missing</span>
                  )}
                </p>
              </div>
            </div>

            <div className="flex flex-wrap gap-1 mb-3">
              {agg.supportedSteps.map((step) => (
                <span key={step} className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-[10px] font-mono">
                  {step}
                </span>
              ))}
            </div>

            <div className="flex items-center justify-between pt-3 border-t border-gray-100">
              <p className="text-[10px] text-gray-400">
                Last check: {new Date(agg.lastHealthCheck).toLocaleString('en-IN', { dateStyle: 'short', timeStyle: 'short' })}
              </p>
              <div className="flex items-center gap-2">
                <button className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded" title="Health Check">
                  <RefreshCw size={14} />
                </button>
                <button
                  onClick={() => setSelectedAgg(agg)}
                  className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded"
                  title="Configure"
                >
                  <Settings size={14} />
                </button>
                <button
                  onClick={() => handleToggleStatus(agg.id)}
                  className={`px-2.5 py-1 text-xs rounded ${
                    agg.status === 'ACTIVE'
                      ? 'bg-red-50 text-red-600 hover:bg-red-100'
                      : 'bg-green-50 text-green-600 hover:bg-green-100'
                  }`}
                >
                  {agg.status === 'ACTIVE' ? 'Disable' : 'Enable'}
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Add Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-6 space-y-4">
            <h2 className="text-lg font-bold text-gray-900">Add New Aggregator</h2>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                <input type="text" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" placeholder="e.g. CERSAI Check" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                <select className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm">
                  <option value="KYC">KYC</option>
                  <option value="BUREAU">Bureau</option>
                  <option value="ESIGN">eSign</option>
                  <option value="AA">Account Aggregator</option>
                  <option value="NACH">NACH</option>
                  <option value="SMS">SMS</option>
                  <option value="EMAIL">Email</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Provider</label>
                <input type="text" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" placeholder="e.g. Authbridge" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Base URL</label>
                <input type="text" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" placeholder="https://api.provider.com/v1" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">API Key</label>
                <input type="password" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" placeholder="Enter API key" />
              </div>
            </div>
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setShowAddModal(false)}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={() => setShowAddModal(false)}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
              >
                Add Aggregator
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Config Detail Modal */}
      {selectedAgg && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-gray-900">Configure: {selectedAgg.name}</h2>
              <button onClick={() => setSelectedAgg(null)} className="text-gray-400 hover:text-gray-600">
                <XCircle size={20} />
              </button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Base URL</label>
                <input type="text" defaultValue={selectedAgg.baseUrl} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">API Key</label>
                <input type="password" defaultValue="●●●●●●●●●●●●" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Timeout (ms)</label>
                <input type="number" defaultValue={5000} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Retry Count</label>
                <input type="number" defaultValue={3} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Supported Steps</label>
                <div className="flex flex-wrap gap-1">
                  {selectedAgg.supportedSteps.map((step) => (
                    <span key={step} className="px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs font-mono">
                      {step}
                    </span>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setSelectedAgg(null)}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={() => setSelectedAgg(null)}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
              >
                Save Changes
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
