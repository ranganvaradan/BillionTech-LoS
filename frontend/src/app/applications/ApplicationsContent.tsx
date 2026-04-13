'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Plus, Filter, Search } from 'lucide-react';
import { StatusBadge, LoadingSpinner, EmptyState } from '@/components/ui/StatusBadge';
import { applicationApi } from '@/lib/api';
import { formatCurrency, formatDate, getBorrowerLabel } from '@/lib/utils';
import type { LoanApplication, ApplicationStatus } from '@/types';

const ALL_STATUSES: ApplicationStatus[] = [
  'DRAFT', 'CONSENT_PENDING', 'KYC_IN_PROGRESS', 'KYC_FAILED',
  'UNDERWRITING', 'APPROVED', 'REJECTED', 'SANCTION_ISSUED',
  'ESIGN_PENDING', 'DISBURSEMENT_PENDING', 'DISBURSED', 'WITHDRAWN', 'ON_HOLD',
];

const MOCK_APPS: LoanApplication[] = [
  { id: '1', applicationNumber: 'LOS-IND-20260413-00001', customerId: 'c1', borrowerType: 'INDIVIDUAL', loanProduct: 'Term Loan', status: 'KYC_IN_PROGRESS', requestedAmount: 500000, createdAt: '2026-04-13T08:30:00Z', updatedAt: '2026-04-13T09:15:00Z' },
  { id: '2', applicationNumber: 'LOS-CMP-20260412-00003', customerId: 'c2', borrowerType: 'COMPANY', loanProduct: 'Business Loan', status: 'UNDERWRITING', requestedAmount: 2500000, createdAt: '2026-04-12T14:00:00Z', updatedAt: '2026-04-13T07:45:00Z' },
  { id: '3', applicationNumber: 'LOS-PRP-20260411-00002', customerId: 'c3', borrowerType: 'PROPRIETOR', loanProduct: 'Business Loan', status: 'APPROVED', requestedAmount: 1000000, approvedAmount: 900000, createdAt: '2026-04-11T10:00:00Z', updatedAt: '2026-04-12T16:30:00Z' },
  { id: '4', applicationNumber: 'LOS-IND-20260410-00005', customerId: 'c4', borrowerType: 'INDIVIDUAL', loanProduct: 'Personal Loan', status: 'DISBURSED', requestedAmount: 300000, approvedAmount: 300000, createdAt: '2026-04-10T09:00:00Z', updatedAt: '2026-04-13T06:00:00Z' },
  { id: '5', applicationNumber: 'LOS-IND-20260409-00004', customerId: 'c5', borrowerType: 'INDIVIDUAL', loanProduct: 'Term Loan', status: 'REJECTED', requestedAmount: 750000, createdAt: '2026-04-09T11:30:00Z', updatedAt: '2026-04-11T14:00:00Z' },
  { id: '6', applicationNumber: 'LOS-PRT-20260408-00001', customerId: 'c6', borrowerType: 'PARTNERSHIP', loanProduct: 'Business Loan', status: 'SANCTION_ISSUED', requestedAmount: 5000000, approvedAmount: 4500000, createdAt: '2026-04-08T12:00:00Z', updatedAt: '2026-04-12T10:00:00Z' },
  { id: '7', applicationNumber: 'LOS-IND-20260407-00006', customerId: 'c7', borrowerType: 'INDIVIDUAL', loanProduct: 'Term Loan', status: 'ESIGN_PENDING', requestedAmount: 200000, approvedAmount: 200000, createdAt: '2026-04-07T15:00:00Z', updatedAt: '2026-04-11T08:00:00Z' },
  { id: '8', applicationNumber: 'LOS-CMP-20260406-00002', customerId: 'c8', borrowerType: 'COMPANY', loanProduct: 'Business Loan', status: 'DRAFT', requestedAmount: 10000000, createdAt: '2026-04-06T09:30:00Z', updatedAt: '2026-04-06T09:30:00Z' },
];

export default function ApplicationsContent() {
  const searchParams = useSearchParams();
  const [applications, setApplications] = useState<LoanApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>(searchParams.get('status') || '');
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    async function fetchApps() {
      try {
        const params: Record<string, string | number> = { page: 0, size: 20 };
        if (statusFilter) params.status = statusFilter;
        const data = await applicationApi.list(params);
        setApplications(data.content);
      } catch {
        setApplications(MOCK_APPS);
      } finally {
        setLoading(false);
      }
    }
    fetchApps();
  }, [statusFilter]);

  const filteredApps = applications.filter((app) => {
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      return (
        app.applicationNumber.toLowerCase().includes(term) ||
        app.loanProduct.toLowerCase().includes(term) ||
        app.borrowerType.toLowerCase().includes(term)
      );
    }
    return true;
  });

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Loan Applications</h1>
          <p className="text-sm text-slate-500 mt-0.5">{filteredApps.length} applications</p>
        </div>
        <Link
          href="/applications/new"
          className="bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-primary-hover transition-colors flex items-center gap-2"
        >
          <Plus size={16} /> New Application
        </Link>
      </div>

      {/* Filters */}
      <div className="bg-card-bg rounded-xl border border-border p-4 flex flex-wrap gap-3 items-center">
        <div className="flex items-center gap-2 bg-slate-50 rounded-lg px-3 py-2 flex-1 min-w-[200px]">
          <Search size={16} className="text-slate-400" />
          <input
            type="text"
            placeholder="Search by application number, product..."
            className="bg-transparent text-sm outline-none w-full placeholder-slate-400"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter size={14} className="text-slate-400" />
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="text-sm border border-border rounded-lg px-3 py-2 bg-white outline-none focus:ring-2 focus:ring-primary/20"
          >
            <option value="">All Statuses</option>
            {ALL_STATUSES.map((s) => (
              <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Table */}
      {loading ? (
        <LoadingSpinner />
      ) : filteredApps.length === 0 ? (
        <EmptyState
          message="No applications found"
          action={
            <Link href="/applications/new" className="text-sm text-primary hover:underline">
              Create your first application
            </Link>
          }
        />
      ) : (
        <div className="bg-card-bg rounded-xl border border-border overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-border bg-slate-50/50">
                  <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Application #</th>
                  <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Borrower Type</th>
                  <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Product</th>
                  <th className="text-right text-xs font-medium text-slate-500 px-5 py-3">Requested</th>
                  <th className="text-right text-xs font-medium text-slate-500 px-5 py-3">Approved</th>
                  <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Status</th>
                  <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Created</th>
                  <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Updated</th>
                </tr>
              </thead>
              <tbody>
                {filteredApps.map((app) => (
                  <tr key={app.id} className="border-b border-border last:border-0 hover:bg-slate-50 transition-colors">
                    <td className="px-5 py-3">
                      <Link href={`/applications/${app.id}`} className="text-sm text-primary hover:underline font-medium">
                        {app.applicationNumber}
                      </Link>
                    </td>
                    <td className="px-5 py-3 text-sm text-slate-600">{getBorrowerLabel(app.borrowerType)}</td>
                    <td className="px-5 py-3 text-sm text-slate-600">{app.loanProduct}</td>
                    <td className="px-5 py-3 text-sm text-slate-900 text-right font-medium">{formatCurrency(app.requestedAmount)}</td>
                    <td className="px-5 py-3 text-sm text-right">{app.approvedAmount ? formatCurrency(app.approvedAmount) : '—'}</td>
                    <td className="px-5 py-3"><StatusBadge status={app.status} /></td>
                    <td className="px-5 py-3 text-sm text-slate-500">{formatDate(app.createdAt)}</td>
                    <td className="px-5 py-3 text-sm text-slate-500">{formatDate(app.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
