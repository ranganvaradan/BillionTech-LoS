'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import {
  Eye,
  Clock,
  AlertTriangle,
  Filter,
} from 'lucide-react';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { formatCurrency } from '@/lib/utils';
import { applicationApi } from '@/lib/api';
import type { ApplicationStatus, LoanApplication } from '@/types';

type KanbanColumn = {
  status: ApplicationStatus;
  label: string;
  color: string;
  cards: LoanApplication[];
};

const PIPELINE_COLUMNS: Array<Pick<KanbanColumn, 'status' | 'label' | 'color'>> = [
  { status: 'DRAFT', label: 'Draft', color: 'bg-gray-100 border-gray-300' },
  { status: 'CONSENT_PENDING', label: 'Consent Pending', color: 'bg-yellow-50 border-yellow-300' },
  { status: 'KYC_IN_PROGRESS', label: 'KYC In Progress', color: 'bg-blue-50 border-blue-300' },
  { status: 'KYC_FAILED', label: 'KYC Failed', color: 'bg-red-50 border-red-300' },
  { status: 'UNDERWRITING', label: 'Underwriting', color: 'bg-purple-50 border-purple-300' },
  { status: 'APPROVED', label: 'Approved', color: 'bg-green-50 border-green-300' },
  { status: 'SANCTION_ISSUED', label: 'Sanction Issued', color: 'bg-emerald-50 border-emerald-300' },
  { status: 'ESIGN_PENDING', label: 'eSign Pending', color: 'bg-orange-50 border-orange-300' },
  { status: 'ESIGN_COMPLETED', label: 'eSign Completed', color: 'bg-teal-50 border-teal-300' },
  { status: 'DISBURSED', label: 'Disbursed', color: 'bg-slate-50 border-slate-300' },
];

export default function KanbanPage() {
  const [applications, setApplications] = useState<LoanApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  const [expandedCards, setExpandedCards] = useState<Set<string>>(new Set());
  const [statusFilter, setStatusFilter] = useState<ApplicationStatus | ''>('');

  useEffect(() => {
    async function fetchApps() {
      try {
        setError('');
        setLoading(true);
        const data = await applicationApi.list({ page: 0, size: 200 });
        setApplications(data.content);
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : 'Failed to load pipeline applications.';
        setError(msg);
        setApplications([]);
      } finally {
        setLoading(false);
      }
    }

    fetchApps();
  }, []);

  const toggleCard = (id: string) => {
    const next = new Set(expandedCards);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setExpandedCards(next);
  };

  const columns: KanbanColumn[] = useMemo(() => {
    const byStatus = new Map<ApplicationStatus, LoanApplication[]>();
    for (const { status } of PIPELINE_COLUMNS) {
      byStatus.set(status, []);
    }

    for (const app of applications) {
      if (byStatus.has(app.status)) {
        byStatus.get(app.status)!.push(app);
      }
    }

    return PIPELINE_COLUMNS.map((col) => {
      const cards = byStatus.get(col.status) || [];
      return {
        ...col,
        cards: cards
          .slice()
          .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()),
      };
    });
  }, [applications]);

  const totalCards = columns.reduce((sum, col) => sum + col.cards.length, 0);

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Pipeline Board</h1>
          <p className="text-sm text-gray-500 mt-1">
            {totalCards} applications in pipeline
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <Filter size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as ApplicationStatus | '')}
              className="pl-8 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="">All Statuses</option>
              {PIPELINE_COLUMNS.map((c) => (
                <option key={c.status} value={c.status}>{c.label}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="bg-white rounded-xl border border-gray-200 p-6 text-sm text-gray-500">
          Loading pipeline...
        </div>
      ) : error ? (
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <p className="text-sm text-gray-500">This page couldn’t load.</p>
          <p className="text-sm text-red-600 mt-2">{error}</p>
          <div className="mt-3">
            <Link href="/applications" className="text-sm text-blue-600 hover:underline">
              Go to Applications
            </Link>
          </div>
        </div>
      ) : (

        <>
          {/* Kanban Board */}
          <div className="flex gap-3 overflow-x-auto pb-4" style={{ minHeight: '70vh' }}>
            {columns
              .filter((c) => (statusFilter ? c.status === statusFilter : true))
              .map((col) => {
                const filteredCards = col.cards;

                return (
                  <div
                    key={col.status}
                    className={`flex-shrink-0 w-72 rounded-xl border-2 ${col.color} flex flex-col`}
                  >
                    {/* Column Header */}
                    <div className="px-3 py-2.5 border-b border-gray-200 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <StatusBadge status={col.status as ApplicationStatus} />
                        <span className="text-xs text-gray-500 font-medium bg-white px-1.5 py-0.5 rounded-full">
                          {filteredCards.length}
                        </span>
                      </div>
                    </div>

                    {/* Cards */}
                    <div className="flex-1 p-2 space-y-2 overflow-y-auto">
                      {filteredCards.map((card) => (
                        <div
                          key={card.id}
                          className="bg-white rounded-lg shadow-sm border border-gray-200 p-3 cursor-pointer hover:shadow-md transition-shadow"
                          onClick={() => toggleCard(card.id)}
                        >
                          <div className="flex items-start justify-between mb-1">
                            <Link
                              href={`/applications/${card.id}`}
                              className="text-xs font-mono text-blue-600 hover:underline"
                              onClick={(e) => e.stopPropagation()}
                            >
                              {card.applicationNumber}
                            </Link>
                          </div>
                          <p className="text-sm font-medium text-gray-900 truncate">
                            {String(card.personalInfo?.fullName || '—')}
                          </p>
                          <p className="text-xs text-gray-500">{card.loanProduct}</p>
                          <div className="flex items-center justify-between mt-2">
                            <span className="text-xs font-medium text-gray-700">
                              {formatCurrency(card.requestedAmount)}
                            </span>
                            <span className="text-[10px] text-gray-500">
                              Updated {new Date(card.updatedAt).toLocaleDateString()}
                            </span>
                          </div>

                          {expandedCards.has(card.id) && (
                            <div className="mt-2 pt-2 border-t border-gray-100 space-y-1">
                              <div className="flex justify-between text-xs">
                                <span className="text-gray-500">Borrower Type</span>
                                <span className="text-gray-700">{card.borrowerType}</span>
                              </div>
                              <div className="flex justify-between text-xs">
                                <span className="text-gray-500">Created</span>
                                <span className="text-gray-700">
                                  {new Date(card.createdAt).toLocaleDateString()}
                                </span>
                              </div>
                              <Link
                                href={`/applications/${card.id}`}
                                className="flex items-center gap-1 text-xs text-blue-600 hover:underline mt-1"
                                onClick={(e) => e.stopPropagation()}
                              >
                                <Eye size={12} /> View Details
                              </Link>
                            </div>
                          )}
                        </div>
                      ))}

                      {filteredCards.length === 0 && (
                        <div className="text-center py-8 text-xs text-gray-400">No applications</div>
                      )}
                    </div>
                  </div>
                );
              })}
          </div>
        </>
      )}
    </div>
  );
}
