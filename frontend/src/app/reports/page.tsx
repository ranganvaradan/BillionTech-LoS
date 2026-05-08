'use client';

import { useState } from 'react';
import { BarChart3, TrendingUp, FileText, Shield, Download, Calendar } from 'lucide-react';
import { formatCurrency } from '@/lib/utils';

type ReportTab = 'analytics' | 'mis' | 'regulatory';

const MOCK_ANALYTICS = {
  totalApplications: 156,
  activePipeline: 49,
  approvedCount: 22,
  rejectedCount: 8,
  disbursedCount: 18,
  todayApplications: 8,
  totalDisbursedAmount: 42500000,
  totalRequestedAmount: 89000000,
  averageLoanSize: 570512,
  approvalRate: 25.6,
  rejectionRate: 5.1,
  conversionRate: 11.5,
  statusDistribution: [
    { status: 'DRAFT', count: 35, percentage: 22.4 },
    { status: 'KYC_IN_PROGRESS', count: 28, percentage: 17.9 },
    { status: 'UNDERWRITING', count: 21, percentage: 13.5 },
    { status: 'APPROVED', count: 22, percentage: 14.1 },
    { status: 'DISBURSED', count: 18, percentage: 11.5 },
    { status: 'REJECTED', count: 8, percentage: 5.1 },
    { status: 'CONSENT_PENDING', count: 12, percentage: 7.7 },
    { status: 'SANCTION_ISSUED', count: 7, percentage: 4.5 },
    { status: 'ESIGN_PENDING', count: 5, percentage: 3.2 },
  ],
  productDistribution: [
    { product: 'Business Loan', count: 45, totalAmount: 28500000 },
    { product: 'Term Loan', count: 38, totalAmount: 22000000 },
    { product: 'Working Capital', count: 32, totalAmount: 18500000 },
    { product: 'MSME Loan', count: 25, totalAmount: 12000000 },
    { product: 'Equipment Finance', count: 16, totalAmount: 8000000 },
  ],
  monthlyTrends: [
    { month: 'Nov 2025', applications: 18, approved: 4, disbursed: 2, disbursedAmount: 4500000 },
    { month: 'Dec 2025', applications: 22, approved: 6, disbursed: 3, disbursedAmount: 5800000 },
    { month: 'Jan 2026', applications: 28, approved: 7, disbursed: 5, disbursedAmount: 8200000 },
    { month: 'Feb 2026', applications: 32, approved: 8, disbursed: 4, disbursedAmount: 7500000 },
    { month: 'Mar 2026', applications: 30, approved: 9, disbursed: 6, disbursedAmount: 9500000 },
    { month: 'Apr 2026', applications: 26, approved: 5, disbursed: 3, disbursedAmount: 7000000 },
  ],
};

const MOCK_REGULATORY = {
  digitalLending: {
    totalDigitalLoans: 156,
    kfsIssued: 30,
    kfsSigned: 25,
    coolingOffComplied: 18,
    esignCompleted: 18,
    digitalComplianceRate: 100,
    rbiCircularRef: 'RBI/2022-23/111 DOR.FIN.REC.66/03.10.038/2022-23',
  },
  kyc: {
    totalKycInitiated: 109,
    kycCompleted: 81,
    kycFailed: 8,
    aadhaarVerified: 81,
    panVerified: 81,
    cKycVerified: 75,
    faceMatchCompleted: 81,
    kycCompletionRate: 74.3,
  },
  dpd: {
    totalActiveLoans: 18,
    dpd0: 15,
    dpd1to30: 2,
    dpd31to60: 1,
    dpd61to90: 0,
    dpd90plus: 0,
    npaCount: 0,
    totalNpaAmount: 0,
    npaPercentage: 0,
  },
};

const statusColors: Record<string, string> = {
  DRAFT: 'bg-slate-100 text-slate-700',
  CONSENT_PENDING: 'bg-yellow-50 text-yellow-700',
  KYC_IN_PROGRESS: 'bg-blue-50 text-blue-700',
  KYC_FAILED: 'bg-red-50 text-red-700',
  UNDERWRITING: 'bg-indigo-50 text-indigo-700',
  APPROVED: 'bg-green-50 text-green-700',
  REJECTED: 'bg-red-50 text-red-700',
  SANCTION_ISSUED: 'bg-purple-50 text-purple-700',
  ESIGN_PENDING: 'bg-orange-50 text-orange-700',
  DISBURSEMENT_PENDING: 'bg-teal-50 text-teal-700',
  DISBURSED: 'bg-emerald-50 text-emerald-700',
};

export default function ReportsPage() {
  const [activeTab, setActiveTab] = useState<ReportTab>('analytics');

  const tabs: { key: ReportTab; label: string; icon: React.ReactNode }[] = [
    { key: 'analytics', label: 'Dashboard Analytics', icon: <BarChart3 size={14} /> },
    { key: 'mis', label: 'MIS Report', icon: <FileText size={14} /> },
    { key: 'regulatory', label: 'Regulatory Compliance', icon: <Shield size={14} /> },
  ];

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Reports</h1>
          <p className="text-sm text-slate-500 mt-0.5">Analytics, MIS reports, and regulatory compliance</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90 transition-colors">
          <Download size={14} /> Export Report
        </button>
      </div>

      {/* Tabs */}
      <div className="bg-card-bg rounded-xl border border-border">
        <div className="flex border-b border-border">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-2 px-5 py-3 text-sm font-medium transition-colors border-b-2 -mb-px ${
                activeTab === tab.key
                  ? 'text-primary border-primary'
                  : 'text-slate-500 border-transparent hover:text-slate-700'
              }`}
            >
              {tab.icon} {tab.label}
            </button>
          ))}
        </div>

        <div className="p-6">
          {activeTab === 'analytics' && <AnalyticsTab />}
          {activeTab === 'mis' && <MisTab />}
          {activeTab === 'regulatory' && <RegulatoryTab />}
        </div>
      </div>
    </div>
  );
}

function AnalyticsTab() {
  const data = MOCK_ANALYTICS;

  return (
    <div className="space-y-6">
      {/* Key Metrics */}
      <div className="grid grid-cols-4 gap-4">
        <MetricCard label="Approval Rate" value={`${data.approvalRate}%`} trend="+2.3%" positive />
        <MetricCard label="Conversion Rate" value={`${data.conversionRate}%`} trend="+1.8%" positive />
        <MetricCard label="Avg Loan Size" value={formatCurrency(data.averageLoanSize)} trend="+5.2%" positive />
        <MetricCard label="Total Disbursed" value={formatCurrency(data.totalDisbursedAmount)} trend="+12%" positive />
      </div>

      {/* Status Distribution */}
      <div>
        <h3 className="text-sm font-semibold text-slate-700 mb-3">Application Status Distribution</h3>
        <div className="grid grid-cols-3 gap-3">
          {data.statusDistribution.map((item) => (
            <div key={item.status} className="flex items-center justify-between bg-slate-50 rounded-lg px-4 py-3">
              <div className="flex items-center gap-2">
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${statusColors[item.status] || 'bg-slate-100 text-slate-600'}`}>
                  {item.status.replace(/_/g, ' ')}
                </span>
              </div>
              <div className="text-right">
                <span className="text-sm font-bold text-slate-900">{item.count}</span>
                <span className="text-xs text-slate-400 ml-1">({item.percentage}%)</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Product Distribution */}
      <div>
        <h3 className="text-sm font-semibold text-slate-700 mb-3">Product Distribution</h3>
        <div className="space-y-2">
          {data.productDistribution.map((item) => {
            const maxCount = data.productDistribution[0].count;
            const widthPct = (item.count / maxCount) * 100;
            return (
              <div key={item.product} className="flex items-center gap-4">
                <span className="text-sm text-slate-700 w-36 shrink-0">{item.product}</span>
                <div className="flex-1 bg-slate-100 rounded-full h-6 overflow-hidden">
                  <div className="bg-primary/80 h-full rounded-full flex items-center px-3" style={{ width: `${widthPct}%` }}>
                    <span className="text-[10px] text-white font-medium">{item.count}</span>
                  </div>
                </div>
                <span className="text-xs text-slate-500 w-24 text-right">{formatCurrency(item.totalAmount)}</span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Monthly Trends */}
      <div>
        <h3 className="text-sm font-semibold text-slate-700 mb-3">Monthly Trends (Last 6 Months)</h3>
        <div className="overflow-hidden rounded-xl border border-border">
          <table className="w-full">
            <thead>
              <tr className="bg-slate-50">
                <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Month</th>
                <th className="text-right text-xs font-medium text-slate-500 px-5 py-3">Applications</th>
                <th className="text-right text-xs font-medium text-slate-500 px-5 py-3">Approved</th>
                <th className="text-right text-xs font-medium text-slate-500 px-5 py-3">Disbursed</th>
                <th className="text-right text-xs font-medium text-slate-500 px-5 py-3">Disbursed Amt</th>
              </tr>
            </thead>
            <tbody>
              {data.monthlyTrends.map((m) => (
                <tr key={m.month} className="border-t border-border">
                  <td className="px-5 py-3 text-sm text-slate-700 font-medium">{m.month}</td>
                  <td className="px-5 py-3 text-sm text-slate-900 text-right">{m.applications}</td>
                  <td className="px-5 py-3 text-sm text-green-700 text-right">{m.approved}</td>
                  <td className="px-5 py-3 text-sm text-primary text-right">{m.disbursed}</td>
                  <td className="px-5 py-3 text-sm text-slate-900 text-right">{formatCurrency(m.disbursedAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function MisTab() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Calendar size={14} className="text-slate-400" />
          <span className="text-sm text-slate-500">Report Period: All Time</span>
        </div>
      </div>

      {/* MIS Summary */}
      <div className="grid grid-cols-5 gap-3">
        <div className="bg-blue-50 rounded-xl p-4 text-center">
          <p className="text-2xl font-bold text-blue-700">156</p>
          <p className="text-xs text-blue-600 mt-1">Total Applications</p>
        </div>
        <div className="bg-green-50 rounded-xl p-4 text-center">
          <p className="text-2xl font-bold text-green-700">40</p>
          <p className="text-xs text-green-600 mt-1">Approved</p>
        </div>
        <div className="bg-red-50 rounded-xl p-4 text-center">
          <p className="text-2xl font-bold text-red-700">8</p>
          <p className="text-xs text-red-600 mt-1">Rejected</p>
        </div>
        <div className="bg-emerald-50 rounded-xl p-4 text-center">
          <p className="text-2xl font-bold text-emerald-700">18</p>
          <p className="text-xs text-emerald-600 mt-1">Disbursed</p>
        </div>
        <div className="bg-amber-50 rounded-xl p-4 text-center">
          <p className="text-2xl font-bold text-amber-700">90</p>
          <p className="text-xs text-amber-600 mt-1">In Pipeline</p>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-slate-50 rounded-xl p-4">
          <p className="text-xs text-slate-500">Total Disbursed Amount</p>
          <p className="text-lg font-bold text-slate-900 mt-1">{formatCurrency(42500000)}</p>
        </div>
        <div className="bg-slate-50 rounded-xl p-4">
          <p className="text-xs text-slate-500">Total Requested Amount</p>
          <p className="text-lg font-bold text-slate-900 mt-1">{formatCurrency(89000000)}</p>
        </div>
        <div className="bg-slate-50 rounded-xl p-4">
          <p className="text-xs text-slate-500">Avg Processing Days</p>
          <p className="text-lg font-bold text-slate-900 mt-1">4.2 days</p>
        </div>
      </div>

      {/* Sample MIS rows */}
      <div>
        <h3 className="text-sm font-semibold text-slate-700 mb-3">Recent Applications (Top 10)</h3>
        <div className="overflow-hidden rounded-xl border border-border">
          <table className="w-full">
            <thead>
              <tr className="bg-slate-50">
                <th className="text-left text-xs font-medium text-slate-500 px-4 py-3">Application #</th>
                <th className="text-left text-xs font-medium text-slate-500 px-4 py-3">Borrower</th>
                <th className="text-left text-xs font-medium text-slate-500 px-4 py-3">Product</th>
                <th className="text-right text-xs font-medium text-slate-500 px-4 py-3">Amount</th>
                <th className="text-left text-xs font-medium text-slate-500 px-4 py-3">Status</th>
                <th className="text-right text-xs font-medium text-slate-500 px-4 py-3">Days</th>
              </tr>
            </thead>
            <tbody>
              {[
                { appNo: 'LOS-IND-20260413-00001', borrower: 'Amit Patel', product: 'Business Loan', amount: 500000, status: 'KYC_IN_PROGRESS', days: 1 },
                { appNo: 'LOS-CMP-20260412-00003', borrower: 'TechCorp Pvt Ltd', product: 'Term Loan', amount: 2500000, status: 'UNDERWRITING', days: 2 },
                { appNo: 'LOS-PRP-20260411-00002', borrower: 'Priya Enterprises', product: 'Working Capital', amount: 900000, status: 'APPROVED', days: 3 },
                { appNo: 'LOS-IND-20260410-00005', borrower: 'Rahul Sharma', product: 'MSME Loan', amount: 300000, status: 'DISBURSED', days: 4 },
                { appNo: 'LOS-PRT-20260408-00001', borrower: 'Green Energy Partners', product: 'Equipment Finance', amount: 4500000, status: 'SANCTION_ISSUED', days: 6 },
              ].map((row) => (
                <tr key={row.appNo} className="border-t border-border">
                  <td className="px-4 py-3 text-sm text-primary font-medium">{row.appNo}</td>
                  <td className="px-4 py-3 text-sm text-slate-700">{row.borrower}</td>
                  <td className="px-4 py-3 text-sm text-slate-600">{row.product}</td>
                  <td className="px-4 py-3 text-sm text-slate-900 text-right font-medium">{formatCurrency(row.amount)}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${statusColors[row.status] || 'bg-slate-100 text-slate-600'}`}>
                      {row.status.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-500 text-right">{row.days}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function RegulatoryTab() {
  const reg = MOCK_REGULATORY;

  return (
    <div className="space-y-6">
      <div className="bg-blue-50 rounded-xl p-4 flex items-start gap-3">
        <Shield size={18} className="text-blue-600 mt-0.5 shrink-0" />
        <div>
          <p className="text-sm font-medium text-blue-800">RBI Digital Lending Guidelines Compliance</p>
          <p className="text-xs text-blue-600 mt-0.5">Reference: {reg.digitalLending.rbiCircularRef}</p>
        </div>
      </div>

      {/* Digital Lending Compliance */}
      <div>
        <h3 className="text-sm font-semibold text-slate-700 mb-3">Digital Lending Compliance</h3>
        <div className="grid grid-cols-3 gap-3">
          <ComplianceCard label="KFS Issued" value={reg.digitalLending.kfsIssued} total={reg.digitalLending.totalDigitalLoans} />
          <ComplianceCard label="KFS Signed" value={reg.digitalLending.kfsSigned} total={reg.digitalLending.kfsIssued} />
          <ComplianceCard label="Cooling-Off Complied" value={reg.digitalLending.coolingOffComplied} total={reg.digitalLending.kfsSigned} />
          <ComplianceCard label="eSign Completed" value={reg.digitalLending.esignCompleted} total={reg.digitalLending.kfsSigned} />
          <ComplianceCard label="Overall Compliance" value={reg.digitalLending.digitalComplianceRate} suffix="%" highlight />
        </div>
      </div>

      {/* KYC Compliance */}
      <div>
        <h3 className="text-sm font-semibold text-slate-700 mb-3">KYC Compliance</h3>
        <div className="grid grid-cols-4 gap-3">
          <ComplianceCard label="KYC Initiated" value={reg.kyc.totalKycInitiated} />
          <ComplianceCard label="KYC Completed" value={reg.kyc.kycCompleted} total={reg.kyc.totalKycInitiated} />
          <ComplianceCard label="KYC Failed" value={reg.kyc.kycFailed} alert />
          <ComplianceCard label="Completion Rate" value={reg.kyc.kycCompletionRate} suffix="%" highlight />
          <ComplianceCard label="Aadhaar Verified" value={reg.kyc.aadhaarVerified} total={reg.kyc.kycCompleted} />
          <ComplianceCard label="PAN Verified" value={reg.kyc.panVerified} total={reg.kyc.kycCompleted} />
          <ComplianceCard label="CKYC Verified" value={reg.kyc.cKycVerified} total={reg.kyc.kycCompleted} />
          <ComplianceCard label="Face Match" value={reg.kyc.faceMatchCompleted} total={reg.kyc.kycCompleted} />
        </div>
      </div>

      {/* DPD Analysis */}
      <div>
        <h3 className="text-sm font-semibold text-slate-700 mb-3">DPD (Days Past Due) Analysis</h3>
        <div className="grid grid-cols-4 gap-3">
          <ComplianceCard label="Active Loans" value={reg.dpd.totalActiveLoans} />
          <ComplianceCard label="DPD 0 (Current)" value={reg.dpd.dpd0} total={reg.dpd.totalActiveLoans} />
          <ComplianceCard label="DPD 1-30" value={reg.dpd.dpd1to30} alert={reg.dpd.dpd1to30 > 0} />
          <ComplianceCard label="DPD 31-60" value={reg.dpd.dpd31to60} alert={reg.dpd.dpd31to60 > 0} />
          <ComplianceCard label="DPD 61-90" value={reg.dpd.dpd61to90} alert={reg.dpd.dpd61to90 > 0} />
          <ComplianceCard label="DPD 90+" value={reg.dpd.dpd90plus} alert={reg.dpd.dpd90plus > 0} />
          <ComplianceCard label="NPA Count" value={reg.dpd.npaCount} alert={reg.dpd.npaCount > 0} />
          <ComplianceCard label="NPA %" value={reg.dpd.npaPercentage} suffix="%" highlight />
        </div>
      </div>
    </div>
  );
}

function MetricCard({ label, value, trend, positive }: { label: string; value: string; trend?: string; positive?: boolean }) {
  return (
    <div className="bg-card-bg rounded-xl border border-border p-5">
      <p className="text-xs text-slate-500 font-medium">{label}</p>
      <p className="text-xl font-bold text-slate-900 mt-1">{value}</p>
      {trend && (
        <p className={`text-xs mt-1 flex items-center gap-1 ${positive ? 'text-green-600' : 'text-red-600'}`}>
          <TrendingUp size={10} /> {trend} vs last month
        </p>
      )}
    </div>
  );
}

function ComplianceCard({ label, value, total, suffix, highlight, alert }: {
  label: string; value: number; total?: number; suffix?: string; highlight?: boolean; alert?: boolean;
}) {
  const displayValue = suffix ? `${value}${suffix}` : String(value);
  return (
    <div className={`rounded-xl p-4 ${highlight ? 'bg-primary/5 border border-primary/20' : alert ? 'bg-red-50 border border-red-100' : 'bg-slate-50'}`}>
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`text-lg font-bold mt-1 ${highlight ? 'text-primary' : alert ? 'text-red-600' : 'text-slate-900'}`}>{displayValue}</p>
      {total !== undefined && (
        <p className="text-[10px] text-slate-400 mt-0.5">of {total}</p>
      )}
    </div>
  );
}
