'use client';

import { useState } from 'react';
import { Plus, Search, Building2, TrendingUp, DollarSign, PieChart } from 'lucide-react';

interface CoLendingPartner {
  id: string;
  partnerName: string;
  partnerCode: string;
  partnerType: string;
  defaultApportionmentPercent: number;
  maxExposureLimit: number;
  interestRateSpread: number;
  contactEmail: string;
  active: boolean;
  allocatedAmount: number;
  dealCount: number;
}

const MOCK_PARTNERS: CoLendingPartner[] = [
  { id: '1', partnerName: 'State Bank of India', partnerCode: 'SBI001', partnerType: 'BANK', defaultApportionmentPercent: 80, maxExposureLimit: 500000000, interestRateSpread: 0.5, contactEmail: 'colending@sbi.co.in', active: true, allocatedAmount: 125000000, dealCount: 42 },
  { id: '2', partnerName: 'HDFC Bank', partnerCode: 'HDFC001', partnerType: 'BANK', defaultApportionmentPercent: 75, maxExposureLimit: 400000000, interestRateSpread: 0.75, contactEmail: 'partners@hdfc.com', active: true, allocatedAmount: 98000000, dealCount: 35 },
  { id: '3', partnerName: 'Bajaj Finance', partnerCode: 'BAJAJ01', partnerType: 'NBFC', defaultApportionmentPercent: 70, maxExposureLimit: 200000000, interestRateSpread: 1.0, contactEmail: 'colend@bajajfinance.in', active: true, allocatedAmount: 45000000, dealCount: 18 },
  { id: '4', partnerName: 'PNB Housing Finance', partnerCode: 'PNBHF01', partnerType: 'HFC', defaultApportionmentPercent: 65, maxExposureLimit: 150000000, interestRateSpread: 1.25, contactEmail: 'partner@pnbhousing.com', active: false, allocatedAmount: 12000000, dealCount: 5 },
];

interface Allocation {
  id: string;
  applicationNumber: string;
  partnerName: string;
  sharePercent: number;
  shareAmount: number;
  status: string;
  partnerReferenceId: string;
}

const MOCK_ALLOCATIONS: Allocation[] = [
  { id: '1', applicationNumber: 'LOS-IND-20260413-00001', partnerName: 'State Bank of India', sharePercent: 80, shareAmount: 4000000, status: 'DISBURSED', partnerReferenceId: 'SBI-CL-2026-0042' },
  { id: '2', applicationNumber: 'LOS-IND-20260413-00003', partnerName: 'HDFC Bank', sharePercent: 75, shareAmount: 3750000, status: 'ACCEPTED', partnerReferenceId: 'HDFC-CL-2026-0035' },
  { id: '3', applicationNumber: 'LOS-PRO-20260413-00002', partnerName: 'Bajaj Finance', sharePercent: 70, shareAmount: 2100000, status: 'PENDING', partnerReferenceId: '' },
  { id: '4', applicationNumber: 'LOS-IND-20260413-00005', partnerName: 'State Bank of India', sharePercent: 80, shareAmount: 2400000, status: 'DISBURSED', partnerReferenceId: 'SBI-CL-2026-0043' },
];

const statusColors: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  ACCEPTED: 'bg-blue-100 text-blue-800',
  DISBURSED: 'bg-green-100 text-green-800',
  SETTLED: 'bg-purple-100 text-purple-800',
};

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);

export default function CoLendingPage() {
  const [activeTab, setActiveTab] = useState<'partners' | 'allocations' | 'settlement'>('partners');
  const [search, setSearch] = useState('');

  const totalAllocated = MOCK_PARTNERS.reduce((s, p) => s + p.allocatedAmount, 0);
  const totalDeals = MOCK_PARTNERS.reduce((s, p) => s + p.dealCount, 0);
  const activePartners = MOCK_PARTNERS.filter(p => p.active).length;

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Co-Lending Management</h1>
          <p className="text-sm text-gray-500 mt-1">Manage co-lending partners, allocations, and settlements</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-primary text-white rounded-lg hover:bg-primary/90 text-sm">
          <Plus size={16} /> Add Partner
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-50 rounded-lg"><Building2 size={20} className="text-blue-600" /></div>
            <div>
              <p className="text-xs text-gray-500">Active Partners</p>
              <p className="text-xl font-bold text-gray-900">{activePartners}</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-green-50 rounded-lg"><DollarSign size={20} className="text-green-600" /></div>
            <div>
              <p className="text-xs text-gray-500">Total Allocated</p>
              <p className="text-xl font-bold text-gray-900">{formatCurrency(totalAllocated)}</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-purple-50 rounded-lg"><PieChart size={20} className="text-purple-600" /></div>
            <div>
              <p className="text-xs text-gray-500">Total Deals</p>
              <p className="text-xl font-bold text-gray-900">{totalDeals}</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-orange-50 rounded-lg"><TrendingUp size={20} className="text-orange-600" /></div>
            <div>
              <p className="text-xs text-gray-500">Avg Share %</p>
              <p className="text-xl font-bold text-gray-900">
                {(MOCK_PARTNERS.filter(p => p.active).reduce((s, p) => s + p.defaultApportionmentPercent, 0) / activePartners).toFixed(0)}%
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200">
        <nav className="flex gap-6">
          {(['partners', 'allocations', 'settlement'] as const).map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)}
              className={`pb-3 text-sm font-medium border-b-2 transition-colors capitalize ${activeTab === tab ? 'border-primary text-primary' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
              {tab}
            </button>
          ))}
        </nav>
      </div>

      {/* Search */}
      <div className="relative max-w-md">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input type="text" placeholder="Search..." value={search} onChange={e => setSearch(e.target.value)}
          className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/20 focus:border-primary" />
      </div>

      {/* Partners Tab */}
      {activeTab === 'partners' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                <th className="px-4 py-3 text-left">Partner</th>
                <th className="px-4 py-3 text-left">Type</th>
                <th className="px-4 py-3 text-right">Share %</th>
                <th className="px-4 py-3 text-right">Max Exposure</th>
                <th className="px-4 py-3 text-right">Allocated</th>
                <th className="px-4 py-3 text-right">Deals</th>
                <th className="px-4 py-3 text-center">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {MOCK_PARTNERS.filter(p => p.partnerName.toLowerCase().includes(search.toLowerCase())).map(p => (
                <tr key={p.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <div className="font-medium text-gray-900">{p.partnerName}</div>
                    <div className="text-xs text-gray-500">{p.partnerCode} · {p.contactEmail}</div>
                  </td>
                  <td className="px-4 py-3">
                    <span className="px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-700">{p.partnerType}</span>
                  </td>
                  <td className="px-4 py-3 text-right font-medium">{p.defaultApportionmentPercent}%</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(p.maxExposureLimit)}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(p.allocatedAmount)}</td>
                  <td className="px-4 py-3 text-right">{p.dealCount}</td>
                  <td className="px-4 py-3 text-center">
                    <span className={`px-2 py-0.5 rounded-full text-xs ${p.active ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                      {p.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Allocations Tab */}
      {activeTab === 'allocations' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                <th className="px-4 py-3 text-left">Application</th>
                <th className="px-4 py-3 text-left">Partner</th>
                <th className="px-4 py-3 text-right">Share %</th>
                <th className="px-4 py-3 text-right">Share Amount</th>
                <th className="px-4 py-3 text-left">Partner Ref</th>
                <th className="px-4 py-3 text-center">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {MOCK_ALLOCATIONS.map(a => (
                <tr key={a.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs">{a.applicationNumber}</td>
                  <td className="px-4 py-3">{a.partnerName}</td>
                  <td className="px-4 py-3 text-right font-medium">{a.sharePercent}%</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(a.shareAmount)}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{a.partnerReferenceId || '-'}</td>
                  <td className="px-4 py-3 text-center">
                    <span className={`px-2 py-0.5 rounded-full text-xs ${statusColors[a.status] || 'bg-gray-100 text-gray-700'}`}>
                      {a.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Settlement Tab */}
      {activeTab === 'settlement' && (
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Settlement Summary</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {MOCK_PARTNERS.filter(p => p.active).map(p => (
              <div key={p.id} className="border border-gray-200 rounded-lg p-4 space-y-3">
                <div className="flex justify-between items-start">
                  <div>
                    <h4 className="font-medium text-gray-900">{p.partnerName}</h4>
                    <p className="text-xs text-gray-500">{p.partnerCode}</p>
                  </div>
                  <span className="px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-700">{p.partnerType}</span>
                </div>
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between"><span className="text-gray-500">Total Allocated</span><span className="font-medium">{formatCurrency(p.allocatedAmount)}</span></div>
                  <div className="flex justify-between"><span className="text-gray-500">Interest Spread</span><span className="font-medium">{p.interestRateSpread}%</span></div>
                  <div className="flex justify-between"><span className="text-gray-500">Active Deals</span><span className="font-medium">{p.dealCount}</span></div>
                  <div className="flex justify-between"><span className="text-gray-500">Settlement Due</span><span className="font-medium text-orange-600">{formatCurrency(p.allocatedAmount * 0.02)}</span></div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
