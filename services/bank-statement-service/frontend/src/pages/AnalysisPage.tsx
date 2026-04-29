import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { bankStatementApi, type SummaryResponse, type AnalysisResponse } from '../lib/api';
import { formatCurrency, formatPercent, severityColor } from '../lib/format';
import MetricCard from '../components/MetricCard';
import ScoreGauge from '../components/ScoreGauge';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';

export default function AnalysisPage() {
  const { id } = useParams<{ id: string }>();
  const [summary, setSummary] = useState<SummaryResponse | null>(null);
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    if (!id) return;
    const stmtId = parseInt(id);

    const fetchData = async () => {
      try {
        const summaryRes = await bankStatementApi.getSummary(stmtId);
        setSummary(summaryRes.data);

        try {
          const analysisRes = await bankStatementApi.getAnalysis(stmtId);
          setAnalysis(analysisRes.data);
        } catch {
          // Analysis may not be ready yet; retry
          if (retryCount < 10) {
            setTimeout(() => setRetryCount((c) => c + 1), 2000);
          }
        }

        setLoading(false);
      } catch {
        if (retryCount < 10) {
          setTimeout(() => setRetryCount((c) => c + 1), 2000);
        } else {
          setLoading(false);
        }
      }
    };

    fetchData();
  }, [id, retryCount]);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-3">
        <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin" />
        <p className="text-gray-500 text-sm">Analyzing statement...</p>
      </div>
    );
  }

  if (!summary) {
    return <div className="text-center text-gray-500 py-12">Statement not found</div>;
  }

  const monthlyData = summary.monthlySummaries.map((m) => ({
    month: m.monthLabel,
    credits: m.totalCredits || 0,
    debits: m.totalDebits || 0,
    balance: m.avgEodBalance || 0,
  }));

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">
            {summary.bankName || 'Bank Statement'} Analysis
          </h2>
          <p className="text-sm text-gray-500 mt-1">
            {summary.accountHolderName && `${summary.accountHolderName} | `}
            {summary.accountNumberMasked && `A/c: ${summary.accountNumberMasked} | `}
            {summary.statementPeriod}
          </p>
        </div>
        <div className="flex gap-2">
          <Link
            to={`/transactions/${id}`}
            className="px-4 py-2 text-sm font-medium text-blue-600 bg-blue-50 rounded-lg hover:bg-blue-100"
          >
            View Transactions
          </Link>
          <Link
            to="/dashboard"
            className="px-4 py-2 text-sm font-medium text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200"
          >
            Back to Dashboard
          </Link>
        </div>
      </div>

      {/* Scores */}
      <div className="bg-white rounded-2xl border border-gray-200 p-6">
        <h3 className="text-sm font-semibold text-gray-700 mb-4">Credit Assessment Scores</h3>
        <div className="flex items-center justify-around">
          <ScoreGauge score={summary.creditworthinessScore} label="Creditworthiness" size="lg" />
          <ScoreGauge score={analysis?.incomeConfidenceScore} label="Income Confidence" />
          <ScoreGauge score={summary.repaymentCapacityScore} label="Repayment Capacity" />
          <ScoreGauge score={analysis?.cashFlowStability} label="Cash Flow Stability" />
          <ScoreGauge score={analysis?.incomeStabilityScore} label="Income Stability" />
        </div>
      </div>

      {/* Key Metrics */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <MetricCard label="Avg Bank Balance" value={formatCurrency(summary.avgBankBalance)} color="blue" />
        <MetricCard label="Detected Salary" value={formatCurrency(summary.detectedSalaryAmount)} color="green" />
        <MetricCard label="FOIR" value={formatPercent(summary.foir)} color={summary.foir && summary.foir > 60 ? 'red' : 'green'} />
        <MetricCard label="Total Income" value={formatCurrency(summary.totalIncome)} color="green" />
        <MetricCard label="Total Credits" value={formatCurrency(summary.totalCredits)} color="green" />
        <MetricCard label="Total Debits" value={formatCurrency(summary.totalDebits)} color="red" />
        <MetricCard label="Bounces" value={String(summary.bounceCount)} color={summary.bounceCount > 3 ? 'red' : 'gray'} />
        <MetricCard label="Red Flags" value={String(summary.redFlagCount)} color={summary.redFlagCount > 0 ? 'red' : 'green'} />
      </div>

      {/* Balance & Cash Flow Details */}
      {analysis && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <MetricCard label="Min Balance" value={formatCurrency(analysis.minBalance)} />
          <MetricCard label="Max Balance" value={formatCurrency(analysis.maxBalance)} />
          <MetricCard label="Net Cash Flow" value={formatCurrency(analysis.netCashFlow)} color={analysis.netCashFlow && analysis.netCashFlow > 0 ? 'green' : 'red'} />
          <MetricCard label="Credit/Debit Ratio" value={analysis.creditDebitRatio?.toFixed(2) || '-'} />
          <MetricCard label="EMI Count" value={String(analysis.emiCount)} />
          <MetricCard label="Monthly EMI" value={formatCurrency(analysis.totalEmiAmount)} />
          <MetricCard label="Rent" value={formatCurrency(analysis.rentAmount)} />
          <MetricCard label="Cash Deposit Ratio" value={formatPercent(analysis.cashDepositRatio)} />
        </div>
      )}

      {/* Monthly Trend Chart */}
      {monthlyData.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">Monthly Cash Flow Trend</h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={monthlyData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="month" tick={{ fontSize: 12 }} />
              <YAxis tick={{ fontSize: 12 }} tickFormatter={(v) => `${(v / 1000).toFixed(0)}K`} />
              <Tooltip formatter={(v: number) => formatCurrency(v)} />
              <Legend />
              <Bar dataKey="credits" fill="#22c55e" name="Credits" radius={[4, 4, 0, 0]} />
              <Bar dataKey="debits" fill="#ef4444" name="Debits" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Category Breakdown */}
      {summary.categoryBreakdown && Object.keys(summary.categoryBreakdown).length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">Category Breakdown</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {Object.entries(summary.categoryBreakdown)
              .sort(([, a], [, b]) => b - a)
              .map(([cat, amount]) => (
                <div key={cat} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                  <span className="text-xs text-gray-600">{cat.replace(/_/g, ' ')}</span>
                  <span className="text-xs font-semibold text-gray-900">{formatCurrency(amount)}</span>
                </div>
              ))}
          </div>
        </div>
      )}

      {/* Red Flags */}
      {analysis?.redFlags && analysis.redFlags.length > 0 && (
        <div className="bg-white rounded-2xl border border-red-200 p-6">
          <h3 className="text-sm font-semibold text-red-700 mb-4">
            Red Flags ({analysis.redFlags.length})
          </h3>
          <div className="space-y-2">
            {analysis.redFlags.map((flag, i) => (
              <div key={i} className="flex items-start gap-3 p-3 bg-red-50 rounded-lg">
                <span className={`px-2 py-0.5 text-xs font-medium rounded ${severityColor(flag.severity)}`}>
                  {flag.severity}
                </span>
                <div>
                  <p className="text-sm font-medium text-gray-900">{flag.type.replace(/_/g, ' ')}</p>
                  <p className="text-xs text-gray-600 mt-0.5">{flag.description}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
