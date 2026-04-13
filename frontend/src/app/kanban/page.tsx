'use client';

import { useState } from 'react';
import Link from 'next/link';
import {
  GripVertical,
  Eye,
  Clock,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  Filter,
} from 'lucide-react';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { formatCurrency } from '@/lib/utils';
import type { ApplicationStatus } from '@/types';

type KanbanCard = {
  id: string;
  applicationNumber: string;
  borrowerName: string;
  borrowerType: string;
  loanProduct: string;
  requestedAmount: number;
  assignedTo: string;
  createdAt: string;
  slaHoursRemaining: number;
  escalated: boolean;
};

type KanbanColumn = {
  status: string;
  label: string;
  color: string;
  cards: KanbanCard[];
};

const MOCK_COLUMNS: KanbanColumn[] = [
  {
    status: 'DRAFT',
    label: 'Draft',
    color: 'bg-gray-100 border-gray-300',
    cards: [
      { id: '1', applicationNumber: 'LOS-IND-20260413-00012', borrowerName: 'Rajesh Kumar', borrowerType: 'INDIVIDUAL', loanProduct: 'Personal Loan', requestedAmount: 300000, assignedTo: 'Unassigned', createdAt: '2026-04-13T08:00:00Z', slaHoursRemaining: 20, escalated: false },
      { id: '2', applicationNumber: 'LOS-PRP-20260413-00013', borrowerName: 'Kumar Traders', borrowerType: 'PROPRIETOR', loanProduct: 'MSME Loan', requestedAmount: 1500000, assignedTo: 'Unassigned', createdAt: '2026-04-13T09:30:00Z', slaHoursRemaining: 22, escalated: false },
    ],
  },
  {
    status: 'CONSENT_PENDING',
    label: 'Consent Pending',
    color: 'bg-yellow-50 border-yellow-300',
    cards: [
      { id: '3', applicationNumber: 'LOS-IND-20260412-00008', borrowerName: 'Priya Sharma', borrowerType: 'INDIVIDUAL', loanProduct: 'Home Loan', requestedAmount: 4500000, assignedTo: 'Amit Singh', createdAt: '2026-04-12T14:00:00Z', slaHoursRemaining: 8, escalated: false },
    ],
  },
  {
    status: 'KYC_IN_PROGRESS',
    label: 'KYC In Progress',
    color: 'bg-blue-50 border-blue-300',
    cards: [
      { id: '4', applicationNumber: 'LOS-CMP-20260411-00005', borrowerName: 'TechVentures Pvt Ltd', borrowerType: 'COMPANY', loanProduct: 'Business Loan', requestedAmount: 10000000, assignedTo: 'Deepak Verma', createdAt: '2026-04-11T10:00:00Z', slaHoursRemaining: -2, escalated: true },
      { id: '5', applicationNumber: 'LOS-IND-20260412-00009', borrowerName: 'Suresh Patel', borrowerType: 'INDIVIDUAL', loanProduct: 'Vehicle Loan', requestedAmount: 800000, assignedTo: 'Priya Reddy', createdAt: '2026-04-12T16:00:00Z', slaHoursRemaining: 14, escalated: false },
      { id: '6', applicationNumber: 'LOS-PRP-20260412-00010', borrowerName: 'Gupta Electronics', borrowerType: 'PROPRIETOR', loanProduct: 'MSME Loan', requestedAmount: 2000000, assignedTo: 'Amit Singh', createdAt: '2026-04-12T11:00:00Z', slaHoursRemaining: 6, escalated: false },
    ],
  },
  {
    status: 'UNDERWRITING',
    label: 'Underwriting',
    color: 'bg-purple-50 border-purple-300',
    cards: [
      { id: '7', applicationNumber: 'LOS-IND-20260410-00003', borrowerName: 'Anita Desai', borrowerType: 'INDIVIDUAL', loanProduct: 'Personal Loan', requestedAmount: 500000, assignedTo: 'Rahul Mehta', createdAt: '2026-04-10T09:00:00Z', slaHoursRemaining: -8, escalated: true },
      { id: '8', applicationNumber: 'LOS-PRT-20260411-00004', borrowerName: 'Shah & Partners', borrowerType: 'PARTNERSHIP', loanProduct: 'Business Loan', requestedAmount: 5000000, assignedTo: 'Deepak Verma', createdAt: '2026-04-11T13:00:00Z', slaHoursRemaining: 12, escalated: false },
    ],
  },
  {
    status: 'APPROVED',
    label: 'Approved',
    color: 'bg-green-50 border-green-300',
    cards: [
      { id: '9', applicationNumber: 'LOS-IND-20260409-00001', borrowerName: 'Vikram Malhotra', borrowerType: 'INDIVIDUAL', loanProduct: 'Home Loan', requestedAmount: 7500000, assignedTo: 'Rahul Mehta', createdAt: '2026-04-09T08:00:00Z', slaHoursRemaining: 40, escalated: false },
    ],
  },
  {
    status: 'ESIGN_PENDING',
    label: 'eSign Pending',
    color: 'bg-orange-50 border-orange-300',
    cards: [
      { id: '10', applicationNumber: 'LOS-CMP-20260408-00002', borrowerName: 'Innovate Solutions', borrowerType: 'COMPANY', loanProduct: 'Business Loan', requestedAmount: 15000000, assignedTo: 'Priya Reddy', createdAt: '2026-04-08T10:00:00Z', slaHoursRemaining: 16, escalated: false },
    ],
  },
  {
    status: 'DISBURSEMENT_PENDING',
    label: 'Disbursement',
    color: 'bg-teal-50 border-teal-300',
    cards: [
      { id: '11', applicationNumber: 'LOS-IND-20260407-00006', borrowerName: 'Meera Joshi', borrowerType: 'INDIVIDUAL', loanProduct: 'Gold Loan', requestedAmount: 200000, assignedTo: 'Amit Singh', createdAt: '2026-04-07T12:00:00Z', slaHoursRemaining: 30, escalated: false },
    ],
  },
];

export default function KanbanPage() {
  const [columns] = useState<KanbanColumn[]>(MOCK_COLUMNS);
  const [expandedCards, setExpandedCards] = useState<Set<string>>(new Set());
  const [filterAssignee, setFilterAssignee] = useState('');

  const toggleCard = (id: string) => {
    const next = new Set(expandedCards);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setExpandedCards(next);
  };

  const allAssignees = Array.from(
    new Set(columns.flatMap((c) => c.cards.map((card) => card.assignedTo)))
  ).sort();

  const totalCards = columns.reduce((sum, col) => sum + col.cards.length, 0);
  const escalatedCount = columns.reduce(
    (sum, col) => sum + col.cards.filter((c) => c.escalated).length,
    0
  );

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Pipeline Board</h1>
          <p className="text-sm text-gray-500 mt-1">
            {totalCards} applications in pipeline &middot;{' '}
            <span className="text-red-600 font-medium">{escalatedCount} escalated</span>
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <Filter size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <select
              value={filterAssignee}
              onChange={(e) => setFilterAssignee(e.target.value)}
              className="pl-8 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="">All Assignees</option>
              {allAssignees.map((a) => (
                <option key={a} value={a}>{a}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Kanban Board */}
      <div className="flex gap-3 overflow-x-auto pb-4" style={{ minHeight: '70vh' }}>
        {columns.map((col) => {
          const filteredCards = filterAssignee
            ? col.cards.filter((c) => c.assignedTo === filterAssignee)
            : col.cards;

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
                    className={`bg-white rounded-lg shadow-sm border p-3 cursor-pointer hover:shadow-md transition-shadow ${
                      card.escalated ? 'border-red-400 ring-1 ring-red-200' : 'border-gray-200'
                    }`}
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
                      {card.escalated && (
                        <AlertTriangle size={14} className="text-red-500 shrink-0" />
                      )}
                    </div>
                    <p className="text-sm font-medium text-gray-900 truncate">{card.borrowerName}</p>
                    <p className="text-xs text-gray-500">{card.loanProduct}</p>
                    <div className="flex items-center justify-between mt-2">
                      <span className="text-xs font-medium text-gray-700">
                        {formatCurrency(card.requestedAmount)}
                      </span>
                      <div className="flex items-center gap-1">
                        <Clock size={12} className={card.slaHoursRemaining < 0 ? 'text-red-500' : card.slaHoursRemaining < 8 ? 'text-yellow-500' : 'text-green-500'} />
                        <span className={`text-xs font-medium ${card.slaHoursRemaining < 0 ? 'text-red-600' : card.slaHoursRemaining < 8 ? 'text-yellow-600' : 'text-green-600'}`}>
                          {card.slaHoursRemaining < 0
                            ? `${Math.abs(card.slaHoursRemaining)}h overdue`
                            : `${card.slaHoursRemaining}h left`}
                        </span>
                      </div>
                    </div>

                    {expandedCards.has(card.id) && (
                      <div className="mt-2 pt-2 border-t border-gray-100 space-y-1">
                        <div className="flex justify-between text-xs">
                          <span className="text-gray-500">Borrower Type</span>
                          <span className="text-gray-700">{card.borrowerType}</span>
                        </div>
                        <div className="flex justify-between text-xs">
                          <span className="text-gray-500">Assigned To</span>
                          <span className="text-gray-700">{card.assignedTo}</span>
                        </div>
                        <div className="flex justify-between text-xs">
                          <span className="text-gray-500">Created</span>
                          <span className="text-gray-700">{new Date(card.createdAt).toLocaleDateString()}</span>
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
                  <div className="text-center py-8 text-xs text-gray-400">
                    No applications
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
