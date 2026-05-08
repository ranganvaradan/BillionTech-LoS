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
  'ESIGN_PENDING', 'ESIGN_COMPLETED', 'DISBURSEMENT_PENDING', 'DISBURSED', 'WITHDRAWN', 'ON_HOLD',
];

export default function ApplicationsContent() {
  const searchParams = useSearchParams();
  const [applications, setApplications] = useState<LoanApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>(searchParams.get('status') || '');
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    async function fetchApps() {
      try {
        setError('');
        const params: Record<string, string | number> = { page: 0, size: 20 };
        if (statusFilter) params.status = statusFilter;
        const data = await applicationApi.list(params);
        setApplications(data.content);
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : 'Failed to load applications.';
        setError(msg);
        setApplications([]);
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
      ) : error ? (
        <div className="bg-card-bg rounded-xl border border-border p-6">
          <p className="text-sm text-slate-500">This page couldn’t load.</p>
          <p className="text-sm text-red-600 mt-2">{error}</p>
        </div>
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
