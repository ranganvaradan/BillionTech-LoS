'use client';

import { useState } from 'react';
import { Plus, Search, Building2, MapPin, DollarSign, Clock } from 'lucide-react';

interface CollateralValuation {
  id: string;
  applicationNumber: string;
  collateralType: string;
  description: string;
  address: string;
  marketValue: number;
  forcedSaleValue: number;
  valuationAmount: number;
  valuerName: string;
  valuationDate: string;
  valuationExpiry: string;
  status: string;
}

const MOCK_COLLATERALS: CollateralValuation[] = [
  { id: '1', applicationNumber: 'LOS-IND-20260413-00001', collateralType: 'PROPERTY', description: '3BHK Apartment, Sector 54', address: 'Tower B, 12th Floor, Sector 54, Gurgaon, Haryana', marketValue: 8500000, forcedSaleValue: 6800000, valuationAmount: 6800000, valuerName: 'CRISIL Valuers Pvt Ltd', valuationDate: '2026-04-10', valuationExpiry: '2026-10-10', status: 'COMPLETED' },
  { id: '2', applicationNumber: 'LOS-PRO-20260413-00002', collateralType: 'MACHINERY', description: 'CNC Lathe Machine + accessories', address: 'Plot 45, Industrial Area Phase II, Noida', marketValue: 3200000, forcedSaleValue: 2240000, valuationAmount: 2240000, valuerName: 'ICRA Valuers', valuationDate: '2026-04-08', valuationExpiry: '2026-10-08', status: 'COMPLETED' },
  { id: '3', applicationNumber: 'LOS-IND-20260413-00003', collateralType: 'VEHICLE', description: '2024 Toyota Fortuner Legender', address: 'N/A', marketValue: 4200000, forcedSaleValue: 3360000, valuationAmount: 3360000, valuerName: 'Shriram Automall', valuationDate: '2026-04-12', valuationExpiry: '2026-10-12', status: 'COMPLETED' },
  { id: '4', applicationNumber: 'LOS-IND-20260413-00005', collateralType: 'GOLD', description: '250g Gold Ornaments (22K)', address: 'Branch Vault', marketValue: 1875000, forcedSaleValue: 1687500, valuationAmount: 1687500, valuerName: 'MMTC-PAMP', valuationDate: '2026-04-13', valuationExpiry: '2026-07-13', status: 'COMPLETED' },
  { id: '5', applicationNumber: 'LOS-COM-20260413-00004', collateralType: 'PROPERTY', description: 'Commercial Office Space, Cyber City', address: 'Unit 501, DLF Cyber City, Phase III, Gurgaon', marketValue: 15000000, forcedSaleValue: 12000000, valuationAmount: 12000000, valuerName: 'Pending Assignment', valuationDate: '', valuationExpiry: '', status: 'PENDING' },
  { id: '6', applicationNumber: 'LOS-IND-20260413-00006', collateralType: 'FIXED_DEPOSIT', description: 'FD with HDFC Bank, 3yr', address: 'N/A', marketValue: 2000000, forcedSaleValue: 2000000, valuationAmount: 1900000, valuerName: 'Auto (Bank Confirmation)', valuationDate: '2026-04-13', valuationExpiry: '2029-04-13', status: 'COMPLETED' },
];

const typeIcons: Record<string, string> = {
  PROPERTY: '🏠', VEHICLE: '🚗', GOLD: '🥇', FIXED_DEPOSIT: '🏦', MACHINERY: '⚙️', SHARES: '📈',
};

const statusColors: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-green-100 text-green-800',
  EXPIRED: 'bg-red-100 text-red-800',
};

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);

export default function CollateralPage() {
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');

  const filtered = MOCK_COLLATERALS.filter(c => {
    const matchesSearch = c.applicationNumber.toLowerCase().includes(search.toLowerCase()) ||
      c.description.toLowerCase().includes(search.toLowerCase());
    const matchesType = typeFilter === 'ALL' || c.collateralType === typeFilter;
    return matchesSearch && matchesType;
  });

  const totalMarketValue = MOCK_COLLATERALS.reduce((s, c) => s + c.marketValue, 0);
  const totalFSV = MOCK_COLLATERALS.reduce((s, c) => s + c.forcedSaleValue, 0);
  const completed = MOCK_COLLATERALS.filter(c => c.status === 'COMPLETED').length;

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Collateral Valuation</h1>
          <p className="text-sm text-gray-500 mt-1">Track and manage collateral valuations for secured loans</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-primary text-white rounded-lg hover:bg-primary/90 text-sm">
          <Plus size={16} /> New Valuation
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-50 rounded-lg"><Building2 size={20} className="text-blue-600" /></div>
            <div>
              <p className="text-xs text-gray-500">Total Collaterals</p>
              <p className="text-xl font-bold text-gray-900">{MOCK_COLLATERALS.length}</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-green-50 rounded-lg"><DollarSign size={20} className="text-green-600" /></div>
            <div>
              <p className="text-xs text-gray-500">Total Market Value</p>
              <p className="text-xl font-bold text-gray-900">{formatCurrency(totalMarketValue)}</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-orange-50 rounded-lg"><MapPin size={20} className="text-orange-600" /></div>
            <div>
              <p className="text-xs text-gray-500">Total FSV</p>
              <p className="text-xl font-bold text-gray-900">{formatCurrency(totalFSV)}</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-purple-50 rounded-lg"><Clock size={20} className="text-purple-600" /></div>
            <div>
              <p className="text-xs text-gray-500">Completed</p>
              <p className="text-xl font-bold text-gray-900">{completed}/{MOCK_COLLATERALS.length}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-4 items-center">
        <div className="relative flex-1 max-w-md">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input type="text" placeholder="Search by application or description..." value={search} onChange={e => setSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/20 focus:border-primary" />
        </div>
        <select value={typeFilter} onChange={e => setTypeFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/20 focus:border-primary">
          <option value="ALL">All Types</option>
          <option value="PROPERTY">Property</option>
          <option value="VEHICLE">Vehicle</option>
          <option value="GOLD">Gold</option>
          <option value="FIXED_DEPOSIT">Fixed Deposit</option>
          <option value="MACHINERY">Machinery</option>
          <option value="SHARES">Shares</option>
        </select>
      </div>

      {/* Collateral Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {filtered.map(c => (
          <div key={c.id} className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between mb-3">
              <div className="flex items-center gap-2">
                <span className="text-2xl">{typeIcons[c.collateralType] || '📋'}</span>
                <div>
                  <h3 className="font-medium text-gray-900">{c.description}</h3>
                  <p className="text-xs text-gray-500 font-mono">{c.applicationNumber}</p>
                </div>
              </div>
              <span className={`px-2 py-0.5 rounded-full text-xs ${statusColors[c.status] || 'bg-gray-100 text-gray-700'}`}>
                {c.status}
              </span>
            </div>
            {c.address !== 'N/A' && (
              <p className="text-xs text-gray-500 mb-3 flex items-center gap-1"><MapPin size={12} /> {c.address}</p>
            )}
            <div className="grid grid-cols-3 gap-3 text-sm">
              <div>
                <p className="text-xs text-gray-500">Market Value</p>
                <p className="font-semibold text-gray-900">{formatCurrency(c.marketValue)}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">Forced Sale Value</p>
                <p className="font-semibold text-gray-900">{formatCurrency(c.forcedSaleValue)}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">Accepted Value</p>
                <p className="font-semibold text-green-700">{formatCurrency(c.valuationAmount)}</p>
              </div>
            </div>
            <div className="mt-3 pt-3 border-t border-gray-100 flex justify-between text-xs text-gray-500">
              <span>Valuer: {c.valuerName}</span>
              {c.valuationDate && <span>Expires: {c.valuationExpiry}</span>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
