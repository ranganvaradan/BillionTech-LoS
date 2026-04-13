'use client';

import { useState } from 'react';
import {
  Shield,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Clock,
  Search,
  Play,
} from 'lucide-react';

interface KycDashboardItem {
  applicationNumber: string;
  applicationId: string;
  borrowerName: string;
  product: string;
  kycStatus: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  stepsCompleted: number;
  totalSteps: number;
  lastUpdated: string;
}

const MOCK_KYC_ITEMS: KycDashboardItem[] = [
  { applicationNumber: 'LOS-IND-20260413-00001', applicationId: '1', borrowerName: 'Rahul Sharma', product: 'Term Loan', kycStatus: 'IN_PROGRESS', stepsCompleted: 5, totalSteps: 9, lastUpdated: '2026-04-13T09:15:00Z' },
  { applicationNumber: 'LOS-CMP-20260412-00003', applicationId: '2', borrowerName: 'TechCorp Pvt Ltd', product: 'Business Loan', kycStatus: 'COMPLETED', stepsCompleted: 9, totalSteps: 9, lastUpdated: '2026-04-12T16:00:00Z' },
  { applicationNumber: 'LOS-PRP-20260411-00002', applicationId: '3', borrowerName: 'Priya Enterprises', product: 'Business Loan', kycStatus: 'FAILED', stepsCompleted: 4, totalSteps: 9, lastUpdated: '2026-04-11T14:30:00Z' },
  { applicationNumber: 'LOS-IND-20260410-00005', applicationId: '4', borrowerName: 'Amit Patel', product: 'Personal Loan', kycStatus: 'PENDING', stepsCompleted: 0, totalSteps: 7, lastUpdated: '2026-04-10T10:00:00Z' },
  { applicationNumber: 'LOS-PRT-20260408-00001', applicationId: '5', borrowerName: 'Green Energy Partners', product: 'Business Loan', kycStatus: 'IN_PROGRESS', stepsCompleted: 7, totalSteps: 9, lastUpdated: '2026-04-08T11:00:00Z' },
];

const STATUS_ICON = {
  PENDING: <Clock size={16} className="text-slate-400" />,
  IN_PROGRESS: <AlertTriangle size={16} className="text-amber-500" />,
  COMPLETED: <CheckCircle size={16} className="text-green-600" />,
  FAILED: <XCircle size={16} className="text-red-600" />,
};

const STATUS_BG = {
  PENDING: 'bg-slate-50 text-slate-600',
  IN_PROGRESS: 'bg-amber-50 text-amber-700',
  COMPLETED: 'bg-green-50 text-green-700',
  FAILED: 'bg-red-50 text-red-700',
};

export default function KycManagementPage() {
  const [statusFilter, setStatusFilter] = useState('');
  const [searchTerm, setSearchTerm] = useState('');

  const filtered = MOCK_KYC_ITEMS.filter((item) => {
    if (statusFilter && item.kycStatus !== statusFilter) return false;
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      return item.applicationNumber.toLowerCase().includes(term) || item.borrowerName.toLowerCase().includes(term);
    }
    return true;
  });

  const summaryByStatus = MOCK_KYC_ITEMS.reduce((acc, item) => {
    acc[item.kycStatus] = (acc[item.kycStatus] || 0) + 1;
    return acc;
  }, {} as Record<string, number>);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-900">KYC Management</h1>
        <p className="text-sm text-slate-500 mt-0.5">Track and manage KYC verification workflows</p>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-4 gap-4">
        {(['PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'] as const).map((status) => (
          <button
            key={status}
            onClick={() => setStatusFilter(statusFilter === status ? '' : status)}
            className={`rounded-xl border p-4 text-left transition-all ${
              statusFilter === status ? 'border-primary shadow-md' : 'border-border hover:border-slate-300'
            } bg-card-bg`}
          >
            <div className="flex items-center justify-between">
              {STATUS_ICON[status]}
              <span className="text-2xl font-bold text-slate-900">{summaryByStatus[status] || 0}</span>
            </div>
            <p className="text-xs text-slate-500 mt-2">{status.replace(/_/g, ' ')}</p>
          </button>
        ))}
      </div>

      {/* Search */}
      <div className="bg-card-bg rounded-xl border border-border p-4 flex items-center gap-2">
        <Search size={16} className="text-slate-400" />
        <input
          type="text"
          placeholder="Search by application number or borrower name..."
          className="bg-transparent text-sm outline-none w-full placeholder-slate-400"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </div>

      {/* KYC Items List */}
      <div className="bg-card-bg rounded-xl border border-border overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="border-b border-border bg-slate-50/50">
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Application</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Borrower</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Product</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">KYC Status</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Progress</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item) => (
              <tr key={item.applicationId} className="border-b border-border last:border-0 hover:bg-slate-50">
                <td className="px-5 py-3">
                  <a href={`/applications/${item.applicationId}`} className="text-sm text-primary hover:underline font-medium">
                    {item.applicationNumber}
                  </a>
                </td>
                <td className="px-5 py-3 text-sm text-slate-700">{item.borrowerName}</td>
                <td className="px-5 py-3 text-sm text-slate-600">{item.product}</td>
                <td className="px-5 py-3">
                  <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_BG[item.kycStatus]}`}>
                    {STATUS_ICON[item.kycStatus]} {item.kycStatus.replace(/_/g, ' ')}
                  </span>
                </td>
                <td className="px-5 py-3">
                  <div className="flex items-center gap-2">
                    <div className="w-20 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full ${item.kycStatus === 'COMPLETED' ? 'bg-green-500' : item.kycStatus === 'FAILED' ? 'bg-red-500' : 'bg-blue-500'}`}
                        style={{ width: `${(item.stepsCompleted / item.totalSteps) * 100}%` }}
                      />
                    </div>
                    <span className="text-xs text-slate-500">{item.stepsCompleted}/{item.totalSteps}</span>
                  </div>
                </td>
                <td className="px-5 py-3">
                  {item.kycStatus !== 'COMPLETED' && (
                    <button className="text-xs bg-primary text-white px-2.5 py-1 rounded-md hover:bg-primary-hover flex items-center gap-1">
                      <Play size={10} /> {item.kycStatus === 'FAILED' ? 'Retry' : 'Continue'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
