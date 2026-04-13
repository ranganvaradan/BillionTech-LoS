'use client';

import { useState } from 'react';
import { Plus, Key, Copy, Trash2, Shield, Eye, EyeOff } from 'lucide-react';

interface ApiKey {
  id: string;
  partnerName: string;
  description: string;
  apiKey: string;
  scopes: string[];
  active: boolean;
  createdAt: string;
  lastUsedAt: string;
  requestCount: number;
}

const MOCK_API_KEYS: ApiKey[] = [
  { id: '1', partnerName: 'FinServ Partners', description: 'Production API access', apiKey: 'los-api-a3f8c1d2e4b5...', scopes: ['READ', 'WRITE', 'WEBHOOK'], active: true, createdAt: '2026-03-15', lastUsedAt: '2026-04-13 09:45', requestCount: 12450 },
  { id: '2', partnerName: 'LoanConnect India', description: 'DSA application submission', apiKey: 'los-api-b7e2f9a1c3d6...', scopes: ['READ', 'WRITE'], active: true, createdAt: '2026-03-20', lastUsedAt: '2026-04-13 08:12', requestCount: 8320 },
  { id: '3', partnerName: 'CreditBridge', description: 'Sandbox testing', apiKey: 'los-sandbox-c1d4e7f2a5b8...', scopes: ['READ'], active: true, createdAt: '2026-04-01', lastUsedAt: '2026-04-12 14:30', requestCount: 156 },
  { id: '4', partnerName: 'QuickLoan DSA', description: 'Legacy integration', apiKey: 'los-api-d5e8f1a4b7c2...', scopes: ['READ', 'WRITE'], active: false, createdAt: '2026-02-10', lastUsedAt: '2026-03-25 11:00', requestCount: 3200 },
];

const MOCK_IP_WHITELIST = [
  { ip: '10.0.0.0/8', description: 'Internal network' },
  { ip: '172.16.0.0/12', description: 'Docker network' },
  { ip: '192.168.1.100', description: 'FinServ Partners office' },
  { ip: '203.0.113.50', description: 'LoanConnect India server' },
];

export default function ApiKeysPage() {
  const [activeTab, setActiveTab] = useState<'keys' | 'whitelist'>('keys');
  const [showNewKeyForm, setShowNewKeyForm] = useState(false);

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">API Key Management</h1>
          <p className="text-sm text-gray-500 mt-1">Manage partner API keys and IP whitelisting</p>
        </div>
        <button onClick={() => setShowNewKeyForm(!showNewKeyForm)}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-white rounded-lg hover:bg-primary/90 text-sm">
          <Plus size={16} /> Generate API Key
        </button>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <p className="text-xs text-gray-500">Total Keys</p>
          <p className="text-2xl font-bold text-gray-900">{MOCK_API_KEYS.length}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <p className="text-xs text-gray-500">Active Keys</p>
          <p className="text-2xl font-bold text-green-600">{MOCK_API_KEYS.filter(k => k.active).length}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <p className="text-xs text-gray-500">Total Requests (30d)</p>
          <p className="text-2xl font-bold text-gray-900">{MOCK_API_KEYS.reduce((s, k) => s + k.requestCount, 0).toLocaleString()}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <p className="text-xs text-gray-500">Whitelisted IPs</p>
          <p className="text-2xl font-bold text-gray-900">{MOCK_IP_WHITELIST.length}</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200">
        <nav className="flex gap-6">
          {[{ key: 'keys' as const, label: 'API Keys' }, { key: 'whitelist' as const, label: 'IP Whitelist' }].map(tab => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`pb-3 text-sm font-medium border-b-2 transition-colors ${activeTab === tab.key ? 'border-primary text-primary' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* New Key Form */}
      {showNewKeyForm && (
        <div className="bg-blue-50 border border-blue-200 rounded-xl p-5">
          <h3 className="text-sm font-semibold text-blue-900 mb-3">Generate New API Key</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <input type="text" placeholder="Partner Name" className="px-3 py-2 border border-blue-300 rounded-lg text-sm" />
            <input type="text" placeholder="Description" className="px-3 py-2 border border-blue-300 rounded-lg text-sm" />
            <select className="px-3 py-2 border border-blue-300 rounded-lg text-sm">
              <option>Production</option>
              <option>Sandbox</option>
            </select>
          </div>
          <div className="mt-3 flex gap-2">
            <button className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700">Generate</button>
            <button onClick={() => setShowNewKeyForm(false)} className="px-4 py-2 bg-white text-gray-700 border border-gray-300 rounded-lg text-sm hover:bg-gray-50">Cancel</button>
          </div>
        </div>
      )}

      {/* API Keys Tab */}
      {activeTab === 'keys' && (
        <div className="space-y-4">
          {MOCK_API_KEYS.map(key => (
            <div key={key.id} className={`bg-white rounded-xl border ${key.active ? 'border-gray-200' : 'border-red-200 bg-red-50/30'} p-5`}>
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className={`p-2 rounded-lg ${key.active ? 'bg-green-50' : 'bg-red-50'}`}>
                    <Key size={20} className={key.active ? 'text-green-600' : 'text-red-400'} />
                  </div>
                  <div>
                    <h3 className="font-medium text-gray-900">{key.partnerName}</h3>
                    <p className="text-xs text-gray-500">{key.description}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 rounded-full text-xs ${key.active ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                    {key.active ? 'Active' : 'Revoked'}
                  </span>
                  {key.active && (
                    <button className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 rounded" title="Revoke">
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>
              </div>
              <div className="mt-3 flex items-center gap-2 bg-gray-50 rounded-lg px-3 py-2">
                <code className="text-xs text-gray-600 flex-1 font-mono">{key.apiKey}</code>
                <button className="p-1 text-gray-400 hover:text-gray-600" title="Copy"><Copy size={14} /></button>
              </div>
              <div className="mt-3 flex items-center gap-4 text-xs text-gray-500">
                <span>Scopes: {key.scopes.map(s => (
                  <span key={s} className="inline-block px-1.5 py-0.5 bg-gray-100 rounded text-gray-600 mr-1">{s}</span>
                ))}</span>
                <span>Created: {key.createdAt}</span>
                <span>Last used: {key.lastUsedAt}</span>
                <span>Requests: {key.requestCount.toLocaleString()}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* IP Whitelist Tab */}
      {activeTab === 'whitelist' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <div className="p-4 border-b border-gray-100 flex justify-between items-center">
            <h3 className="text-sm font-semibold text-gray-900">IP Whitelist (Admin API Access)</h3>
            <button className="flex items-center gap-1 px-3 py-1.5 bg-primary text-white rounded-lg text-xs hover:bg-primary/90">
              <Plus size={14} /> Add IP
            </button>
          </div>
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                <th className="px-4 py-3 text-left">IP Address / CIDR</th>
                <th className="px-4 py-3 text-left">Description</th>
                <th className="px-4 py-3 text-center">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {MOCK_IP_WHITELIST.map((ip, idx) => (
                <tr key={idx} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-sm flex items-center gap-2">
                    <Shield size={14} className="text-green-500" /> {ip.ip}
                  </td>
                  <td className="px-4 py-3 text-gray-600">{ip.description}</td>
                  <td className="px-4 py-3 text-center">
                    <button className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 rounded" title="Remove">
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
