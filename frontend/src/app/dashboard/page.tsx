'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  FileText,
  Users,
  TrendingUp,
  Clock,
  CheckCircle,
  AlertTriangle,
  ArrowRight,
} from 'lucide-react';
import { StatCard, StatusBadge, LoadingSpinner } from '@/components/ui/StatusBadge';
import { applicationApi } from '@/lib/api';
import { formatCurrency, formatDate, STATUS_CONFIG } from '@/lib/utils';
import type { LoanApplication, ApplicationStatus, DashboardSummary } from '@/types';

// Mock data for demo when API is not running
const MOCK_SUMMARY: DashboardSummary = {
  totalApplications: 156,
  todayCount: 8,
  thisMonthCount: 42,
  statusCounts: {
    DRAFT: 12,
    CONSENT_PENDING: 8,
    KYC_IN_PROGRESS: 15,
    KYC_FAILED: 3,
    UNDERWRITING: 18,
    APPROVED: 22,
    REJECTED: 5,
    SANCTION_ISSUED: 14,
    ESIGN_PENDING: 9,
    DISBURSEMENT_PENDING: 7,
    DISBURSED: 38,
    WITHDRAWN: 3,
    ON_HOLD: 2,
  },
};

const MOCK_RECENT: LoanApplication[] = [
  {
    id: '1',
    applicationNumber: 'LOS-IND-20260413-00001',
    customerId: 'c1',
    borrowerType: 'INDIVIDUAL',
    loanProduct: 'Term Loan',
    status: 'KYC_IN_PROGRESS',
    requestedAmount: 500000,
    createdAt: '2026-04-13T08:30:00Z',
    updatedAt: '2026-04-13T09:15:00Z',
  },
  {
    id: '2',
    applicationNumber: 'LOS-CMP-20260412-00003',
    customerId: 'c2',
    borrowerType: 'COMPANY',
    loanProduct: 'Business Loan',
    status: 'UNDERWRITING',
    requestedAmount: 2500000,
    createdAt: '2026-04-12T14:00:00Z',
    updatedAt: '2026-04-13T07:45:00Z',
  },
  {
    id: '3',
    applicationNumber: 'LOS-PRP-20260411-00002',
    customerId: 'c3',
    borrowerType: 'PROPRIETOR',
    loanProduct: 'Business Loan',
    status: 'APPROVED',
    requestedAmount: 1000000,
    approvedAmount: 900000,
    createdAt: '2026-04-11T10:00:00Z',
    updatedAt: '2026-04-12T16:30:00Z',
  },
  {
    id: '4',
    applicationNumber: 'LOS-IND-20260410-00005',
    customerId: 'c4',
    borrowerType: 'INDIVIDUAL',
    loanProduct: 'Personal Loan',
    status: 'DISBURSED',
    requestedAmount: 300000,
    approvedAmount: 300000,
    createdAt: '2026-04-10T09:00:00Z',
    updatedAt: '2026-04-13T06:00:00Z',
  },
  {
    id: '5',
    applicationNumber: 'LOS-IND-20260409-00004',
    customerId: 'c5',
    borrowerType: 'INDIVIDUAL',
    loanProduct: 'Term Loan',
    status: 'REJECTED',
    requestedAmount: 750000,
    createdAt: '2026-04-09T11:30:00Z',
    updatedAt: '2026-04-11T14:00:00Z',
  },
];

export default function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [recentApps, setRecentApps] = useState<LoanApplication[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchData() {
      try {
        const [dashData, appsData] = await Promise.all([
          applicationApi.getDashboard(),
          applicationApi.list({ page: 0, size: 5 }),
        ]);
        setSummary(dashData);
        setRecentApps(appsData.content);
      } catch {
        // Use mock data when API not available
        setSummary(MOCK_SUMMARY);
        setRecentApps(MOCK_RECENT);
      } finally {
        setLoading(false);
      }
    }
    fetchData();
  }, []);

  if (loading) return <LoadingSpinner />;

  const s = summary || MOCK_SUMMARY;
  const apps = recentApps.length > 0 ? recentApps : MOCK_RECENT;

  const activeCount =
    (s.statusCounts.KYC_IN_PROGRESS || 0) +
    (s.statusCounts.UNDERWRITING || 0) +
    (s.statusCounts.ESIGN_PENDING || 0) +
    (s.statusCounts.DISBURSEMENT_PENDING || 0);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Dashboard</h1>
          <p className="text-sm text-slate-500 mt-0.5">Loan Origination Overview</p>
        </div>
        <Link
          href="/applications/new"
          className="bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-primary-hover transition-colors"
        >
          + New Application
        </Link>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Applications"
          value={s.totalApplications}
          subtitle={`${s.thisMonthCount} this month`}
          icon={<FileText size={24} />}
        />
        <StatCard
          title="Active Pipeline"
          value={activeCount}
          subtitle="In progress"
          icon={<Clock size={24} />}
          color="text-blue-600"
        />
        <StatCard
          title="Approved"
          value={s.statusCounts.APPROVED || 0}
          subtitle="Pending sanction"
          icon={<CheckCircle size={24} />}
          color="text-green-600"
        />
        <StatCard
          title="Today"
          value={s.todayCount}
          subtitle="New applications"
          icon={<TrendingUp size={24} />}
          color="text-amber-600"
        />
      </div>

      {/* Status Pipeline */}
      <div className="bg-card-bg rounded-xl border border-border p-5">
        <h2 className="text-sm font-semibold text-slate-900 mb-4">Application Pipeline</h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
          {(
            ['DRAFT', 'KYC_IN_PROGRESS', 'UNDERWRITING', 'APPROVED', 'SANCTION_ISSUED', 'DISBURSED'] as ApplicationStatus[]
          ).map((status) => {
            const config = STATUS_CONFIG[status];
            return (
              <Link
                key={status}
                href={`/applications?status=${status}`}
                className={`${config.bg} rounded-lg p-3 text-center hover:opacity-80 transition-opacity`}
              >
                <p className={`text-lg font-bold ${config.color}`}>
                  {s.statusCounts[status] || 0}
                </p>
                <p className="text-xs text-slate-600 mt-0.5">{config.label}</p>
              </Link>
            );
          })}
        </div>
      </div>

      {/* Alert: Items needing attention */}
      {((s.statusCounts.KYC_FAILED || 0) > 0 || (s.statusCounts.ON_HOLD || 0) > 0) && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-start gap-3">
          <AlertTriangle size={18} className="text-amber-600 mt-0.5 shrink-0" />
          <div>
            <p className="text-sm font-medium text-amber-800">Attention Required</p>
            <p className="text-xs text-amber-700 mt-0.5">
              {s.statusCounts.KYC_FAILED || 0} KYC failures and {s.statusCounts.ON_HOLD || 0} applications on hold
            </p>
          </div>
        </div>
      )}

      {/* Recent Applications Table */}
      <div className="bg-card-bg rounded-xl border border-border">
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <h2 className="text-sm font-semibold text-slate-900">Recent Applications</h2>
          <Link
            href="/applications"
            className="text-xs text-primary hover:text-primary-hover font-medium flex items-center gap-1"
          >
            View All <ArrowRight size={12} />
          </Link>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border">
                <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Application #</th>
                <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Type</th>
                <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Product</th>
                <th className="text-right text-xs font-medium text-slate-500 px-5 py-3">Amount</th>
                <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Status</th>
                <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Date</th>
              </tr>
            </thead>
            <tbody>
              {apps.map((app) => (
                <tr key={app.id} className="border-b border-border last:border-0 hover:bg-slate-50 transition-colors">
                  <td className="px-5 py-3">
                    <Link
                      href={`/applications/${app.id}`}
                      className="text-sm text-primary hover:underline font-medium"
                    >
                      {app.applicationNumber}
                    </Link>
                  </td>
                  <td className="px-5 py-3 text-sm text-slate-600">{app.borrowerType}</td>
                  <td className="px-5 py-3 text-sm text-slate-600">{app.loanProduct}</td>
                  <td className="px-5 py-3 text-sm text-slate-900 text-right font-medium">
                    {formatCurrency(app.requestedAmount)}
                  </td>
                  <td className="px-5 py-3">
                    <StatusBadge status={app.status} />
                  </td>
                  <td className="px-5 py-3 text-sm text-slate-500">{formatDate(app.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
