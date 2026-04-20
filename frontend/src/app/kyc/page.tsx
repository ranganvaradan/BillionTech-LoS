'use client';

import type { ChangeEvent, ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';
import {
  Shield,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Clock,
  Search,
  Play,
} from 'lucide-react';

import { applicationApi, kycApi } from '@/lib/api';
import type { KycOutcomeResponse, LoanApplication } from '@/types';

type UiKycStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';

interface KycDashboardItem {
  applicationNumber: string;
  applicationId: string;
  borrowerName: string;
  product: string;
  applicationStatus: string;
  kycOutcome: string;
  kycStatus: UiKycStatus;
  stepsCompleted: number;
  totalSteps: number;
  lastStep: string;
}

const STATUS_ICON: Record<UiKycStatus, ReactNode> = {
  PENDING: <Clock size={16} className="text-slate-400" />,
  IN_PROGRESS: <AlertTriangle size={16} className="text-amber-500" />,
  COMPLETED: <CheckCircle size={16} className="text-green-600" />,
  FAILED: <XCircle size={16} className="text-red-600" />,
};

const STATUS_BG: Record<UiKycStatus, string> = {
  PENDING: 'bg-slate-50 text-slate-600',
  IN_PROGRESS: 'bg-amber-50 text-amber-700',
  COMPLETED: 'bg-green-50 text-green-700',
  FAILED: 'bg-red-50 text-red-700',
};

export default function KycManagementPage() {
  const [statusFilter, setStatusFilter] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [items, setItems] = useState<KycDashboardItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError(null);

      try {
        const page = await applicationApi.list({ page: 0, size: 200 });
        const apps = (page.content ?? []) as LoanApplication[];

        const outcomeResults = await Promise.all(
          apps.map(async (app: LoanApplication) => {
            try {
              const outcome = (await kycApi.getOutcome(String(app.id))) as KycOutcomeResponse;
              return { app, outcome };
            } catch (e) {
              return { app, outcome: null as KycOutcomeResponse | null };
            }
          })
        );

        const mapped: KycDashboardItem[] = outcomeResults.map(({ app, outcome }: { app: LoanApplication; outcome: KycOutcomeResponse | null }) => {
          const personalInfo = (app.personalInfo ?? {}) as Record<string, unknown>;
          const borrowerName =
            String((personalInfo as any).fullName ?? (personalInfo as any).name ?? `${(personalInfo as any).firstName ?? ''} ${(personalInfo as any).lastName ?? ''}`)
              .trim() ||
            '—';

          const kycOutcome = String(outcome?.outcome ?? 'INCOMPLETE');
          const stepSummary: any[] = Array.isArray(outcome?.stepSummary) ? outcome.stepSummary : [];
          const totalSteps = stepSummary.length;
          const stepsCompleted = stepSummary.filter((s) => String(s?.outcome).toUpperCase() === 'SUCCESS').length;
          const lastStep = stepSummary.length > 0 ? String(stepSummary[stepSummary.length - 1]?.stepType ?? '—') : '—';

          let kycStatus: UiKycStatus = 'IN_PROGRESS';
          if (kycOutcome === 'PASS') kycStatus = 'COMPLETED';
          else if (kycOutcome === 'FAIL') kycStatus = 'FAILED';
          else if (totalSteps === 0) kycStatus = 'PENDING';

          return {
            applicationNumber: String((app as any).applicationNumber ?? '—'),
            applicationId: String(app.id),
            borrowerName,
            product: String(app.loanProduct ?? '—'),
            applicationStatus: String(app.status ?? '—'),
            kycOutcome,
            kycStatus,
            stepsCompleted,
            totalSteps,
            lastStep,
          };
        });

        if (!cancelled) {
          setItems(mapped);
        }
      } catch (e: any) {
        if (!cancelled) {
          setError(e?.message ? String(e.message) : 'Failed to load KYC dashboard');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    load();

    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = useMemo(() => items.filter((item: KycDashboardItem) => {
    if (statusFilter && item.kycStatus !== statusFilter) return false;
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      return item.applicationNumber.toLowerCase().includes(term) || item.borrowerName.toLowerCase().includes(term);
    }
    return true;
  }), [items, searchTerm, statusFilter]);

  const summaryByStatus = useMemo(() => items.reduce((acc: Record<string, number>, item: KycDashboardItem) => {
    acc[item.kycStatus] = (acc[item.kycStatus] || 0) + 1;
    return acc;
  }, {} as Record<string, number>), [items]);

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
          onChange={(e: ChangeEvent<HTMLInputElement>) => setSearchTerm(e.target.value)}
        />
      </div>

      {/* KYC Items List */}
      <div className="bg-card-bg rounded-xl border border-border overflow-hidden">
        {loading ? (
          <div className="p-6 text-sm text-slate-600">Loading…</div>
        ) : error ? (
          <div className="p-6 text-sm text-red-600">{error}</div>
        ) : (
        <table className="w-full">
          <thead>
            <tr className="border-b border-border bg-slate-50/50">
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Application</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Borrower</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Product</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">App Status</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">KYC Outcome</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">KYC Status</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Progress</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Last Step</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item: KycDashboardItem) => (
              <tr key={item.applicationId} className="border-b border-border last:border-0 hover:bg-slate-50">
                <td className="px-5 py-3">
                  <a href={`/applications/${item.applicationId}`} className="text-sm text-primary hover:underline font-medium">
                    {item.applicationNumber}
                  </a>
                </td>
                <td className="px-5 py-3 text-sm text-slate-700">{item.borrowerName}</td>
                <td className="px-5 py-3 text-sm text-slate-600">{item.product}</td>
                <td className="px-5 py-3 text-sm text-slate-600">{item.applicationStatus}</td>
                <td className="px-5 py-3 text-sm text-slate-600">{item.kycOutcome}</td>
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
                        style={{ width: `${item.totalSteps > 0 ? (item.stepsCompleted / item.totalSteps) * 100 : 0}%` }}
                      />
                    </div>
                    <span className="text-xs text-slate-500">{item.stepsCompleted}/{item.totalSteps}</span>
                  </div>
                </td>
                <td className="px-5 py-3 text-sm text-slate-600">{item.lastStep}</td>
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
        )}
      </div>
    </div>
  );
}
