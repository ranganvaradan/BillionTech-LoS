import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { bankStatementApi, type StatementResponse } from '../lib/api';
import { formatCurrency, formatDate } from '../lib/format';

export default function DashboardPage() {
  const [statements, setStatements] = useState<StatementResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    bankStatementApi.list(0, 50).then((res) => {
      setStatements(res.data.content);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  const statusBadge = (status: string) => {
    const colors: Record<string, string> = {
      ANALYSIS_COMPLETE: 'bg-green-100 text-green-800',
      PARSED: 'bg-blue-100 text-blue-800',
      PROCESSING: 'bg-yellow-100 text-yellow-800',
      ANALYZING: 'bg-yellow-100 text-yellow-800',
      FAILED: 'bg-red-100 text-red-800',
      UPLOADED: 'bg-gray-100 text-gray-800',
    };
    return (
      <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${colors[status] || colors.UPLOADED}`}>
        {status.replace(/_/g, ' ')}
      </span>
    );
  };

  if (loading) {
    return <div className="flex items-center justify-center h-64 text-gray-500">Loading...</div>;
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-bold text-gray-900">Uploaded Statements</h2>
        <Link
          to="/"
          className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700"
        >
          Upload New
        </Link>
      </div>

      {statements.length === 0 ? (
        <div className="bg-white rounded-xl border border-gray-200 p-12 text-center">
          <p className="text-gray-500">No statements uploaded yet</p>
          <Link to="/" className="text-blue-600 hover:underline text-sm mt-2 inline-block">
            Upload your first statement
          </Link>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="text-left px-4 py-3 font-medium text-gray-500">File</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Bank</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Account</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Period</th>
                <th className="text-right px-4 py-3 font-medium text-gray-500">Transactions</th>
                <th className="text-right px-4 py-3 font-medium text-gray-500">Credits</th>
                <th className="text-right px-4 py-3 font-medium text-gray-500">Debits</th>
                <th className="text-center px-4 py-3 font-medium text-gray-500">Status</th>
                <th className="text-right px-4 py-3 font-medium text-gray-500">Uploaded</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {statements.map((s) => (
                <tr key={s.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <Link to={`/analysis/${s.id}`} className="text-blue-600 hover:underline font-medium">
                      {s.fileName}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-gray-600">{s.bankName || '-'}</td>
                  <td className="px-4 py-3 text-gray-600">{s.accountNumberMasked || '-'}</td>
                  <td className="px-4 py-3 text-gray-600 text-xs">
                    {s.statementFromDate && s.statementToDate
                      ? `${formatDate(s.statementFromDate)} - ${formatDate(s.statementToDate)}`
                      : '-'}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-600">{s.totalTransactions || 0}</td>
                  <td className="px-4 py-3 text-right text-green-700">{formatCurrency(s.totalCreditAmount)}</td>
                  <td className="px-4 py-3 text-right text-red-700">{formatCurrency(s.totalDebitAmount)}</td>
                  <td className="px-4 py-3 text-center">{statusBadge(s.parsingStatus)}</td>
                  <td className="px-4 py-3 text-right text-gray-500 text-xs">{formatDate(s.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
