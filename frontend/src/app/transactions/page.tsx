'use client';

import { useState } from 'react';
import { CreditCard, ArrowDownLeft, ArrowUpRight, Search, Filter } from 'lucide-react';
import { formatCurrency, formatDateTime } from '@/lib/utils';

interface TransactionItem {
  id: string;
  applicationNumber: string;
  type: 'DISBURSEMENT' | 'REPAYMENT';
  amount: number;
  status: 'COMPLETED' | 'PENDING' | 'FAILED';
  referenceNumber: string;
  utrNumber?: string;
  borrowerName: string;
  createdAt: string;
}

const MOCK_TRANSACTIONS: TransactionItem[] = [
  { id: 't1', applicationNumber: 'LOS-IND-20260410-00005', type: 'DISBURSEMENT', amount: 300000, status: 'COMPLETED', referenceNumber: 'REF-001', utrNumber: 'UTR-20260410-001', borrowerName: 'Amit Patel', createdAt: '2026-04-10T12:00:00Z' },
  { id: 't2', applicationNumber: 'LOS-IND-20260410-00005', type: 'REPAYMENT', amount: 12000, status: 'COMPLETED', referenceNumber: 'REF-002', utrNumber: 'UTR-20260413-001', borrowerName: 'Amit Patel', createdAt: '2026-04-13T09:00:00Z' },
  { id: 't3', applicationNumber: 'LOS-PRT-20260408-00001', type: 'DISBURSEMENT', amount: 4500000, status: 'PENDING', referenceNumber: 'REF-003', borrowerName: 'Green Energy Partners', createdAt: '2026-04-12T15:00:00Z' },
  { id: 't4', applicationNumber: 'LOS-PRP-20260411-00002', type: 'DISBURSEMENT', amount: 900000, status: 'COMPLETED', referenceNumber: 'REF-004', utrNumber: 'UTR-20260412-002', borrowerName: 'Priya Enterprises', createdAt: '2026-04-12T11:00:00Z' },
  { id: 't5', applicationNumber: 'LOS-CMP-20260412-00003', type: 'DISBURSEMENT', amount: 2500000, status: 'FAILED', referenceNumber: 'REF-005', borrowerName: 'TechCorp Pvt Ltd', createdAt: '2026-04-13T08:00:00Z' },
];

export default function TransactionsPage() {
  const [typeFilter, setTypeFilter] = useState('');
  const [searchTerm, setSearchTerm] = useState('');

  const filtered = MOCK_TRANSACTIONS.filter((txn) => {
    if (typeFilter && txn.type !== typeFilter) return false;
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      return txn.applicationNumber.toLowerCase().includes(term) || txn.borrowerName.toLowerCase().includes(term);
    }
    return true;
  });

  const totalDisbursed = MOCK_TRANSACTIONS.filter((t) => t.type === 'DISBURSEMENT' && t.status === 'COMPLETED').reduce((s, t) => s + t.amount, 0);
  const totalRepaid = MOCK_TRANSACTIONS.filter((t) => t.type === 'REPAYMENT' && t.status === 'COMPLETED').reduce((s, t) => s + t.amount, 0);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-900">Transactions</h1>
        <p className="text-sm text-slate-500 mt-0.5">Disbursements and repayment tracking</p>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-3 gap-4">
        <div className="bg-card-bg rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 text-slate-400 mb-2">
            <ArrowUpRight size={16} /> <span className="text-xs font-medium">Total Disbursed</span>
          </div>
          <p className="text-xl font-bold text-slate-900">{formatCurrency(totalDisbursed)}</p>
        </div>
        <div className="bg-card-bg rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 text-slate-400 mb-2">
            <ArrowDownLeft size={16} /> <span className="text-xs font-medium">Total Repaid</span>
          </div>
          <p className="text-xl font-bold text-green-700">{formatCurrency(totalRepaid)}</p>
        </div>
        <div className="bg-card-bg rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 text-slate-400 mb-2">
            <CreditCard size={16} /> <span className="text-xs font-medium">Net Outstanding</span>
          </div>
          <p className="text-xl font-bold text-amber-600">{formatCurrency(totalDisbursed - totalRepaid)}</p>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-card-bg rounded-xl border border-border p-4 flex gap-3 items-center">
        <div className="flex items-center gap-2 bg-slate-50 rounded-lg px-3 py-2 flex-1">
          <Search size={16} className="text-slate-400" />
          <input
            type="text"
            placeholder="Search by application or borrower..."
            className="bg-transparent text-sm outline-none w-full placeholder-slate-400"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter size={14} className="text-slate-400" />
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="text-sm border border-border rounded-lg px-3 py-2 bg-white outline-none"
          >
            <option value="">All Types</option>
            <option value="DISBURSEMENT">Disbursement</option>
            <option value="REPAYMENT">Repayment</option>
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="bg-card-bg rounded-xl border border-border overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="border-b border-border bg-slate-50/50">
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Application</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Borrower</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Type</th>
              <th className="text-right text-xs font-medium text-slate-500 px-5 py-3">Amount</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Status</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Reference</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Date</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((txn) => (
              <tr key={txn.id} className="border-b border-border last:border-0 hover:bg-slate-50">
                <td className="px-5 py-3 text-sm text-primary font-medium">{txn.applicationNumber}</td>
                <td className="px-5 py-3 text-sm text-slate-700">{txn.borrowerName}</td>
                <td className="px-5 py-3">
                  <span className={`inline-flex items-center gap-1 text-xs font-medium ${txn.type === 'DISBURSEMENT' ? 'text-blue-700' : 'text-green-700'}`}>
                    {txn.type === 'DISBURSEMENT' ? <ArrowUpRight size={12} /> : <ArrowDownLeft size={12} />}
                    {txn.type}
                  </span>
                </td>
                <td className="px-5 py-3 text-sm text-slate-900 text-right font-medium">{formatCurrency(txn.amount)}</td>
                <td className="px-5 py-3">
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                    txn.status === 'COMPLETED' ? 'bg-green-50 text-green-700' :
                    txn.status === 'PENDING' ? 'bg-amber-50 text-amber-700' :
                    'bg-red-50 text-red-700'
                  }`}>
                    {txn.status}
                  </span>
                </td>
                <td className="px-5 py-3 text-xs text-slate-500 font-mono">{txn.utrNumber || txn.referenceNumber}</td>
                <td className="px-5 py-3 text-xs text-slate-500">{formatDateTime(txn.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
