import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { bankStatementApi, type TransactionResponse } from '../lib/api';
import { formatCurrency, formatDate, categoryLabel } from '../lib/format';

export default function TransactionsPage() {
  const { id } = useParams<{ id: string }>();
  const [transactions, setTransactions] = useState<TransactionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [filter, setFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    bankStatementApi.getTransactions(parseInt(id), page, 50).then((res) => {
      setTransactions(res.data.content);
      setTotalPages(res.data.totalPages);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [id, page]);

  const categories = ['ALL', ...new Set(transactions.map((t) => t.category || 'OTHER'))];

  const filtered = transactions.filter((t) => {
    const matchesText = !filter || t.narration?.toLowerCase().includes(filter.toLowerCase());
    const matchesCat = categoryFilter === 'ALL' || t.category === categoryFilter;
    return matchesText && matchesCat;
  });

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-bold text-gray-900">Transactions</h2>
        <div className="flex gap-2">
          <Link
            to={`/analysis/${id}`}
            className="px-4 py-2 text-sm font-medium text-blue-600 bg-blue-50 rounded-lg hover:bg-blue-100"
          >
            Back to Analysis
          </Link>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl border border-gray-200 p-4 mb-4 flex gap-4">
        <input
          type="text"
          placeholder="Search narration..."
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
        />
        <select
          value={categoryFilter}
          onChange={(e) => setCategoryFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
        >
          {categories.map((cat) => (
            <option key={cat} value={cat}>
              {cat === 'ALL' ? 'All Categories' : categoryLabel(cat)}
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <div className="text-center py-12 text-gray-500">Loading transactions...</div>
      ) : (
        <>
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200">
                  <th className="text-left px-3 py-2.5 font-medium text-gray-500">Date</th>
                  <th className="text-left px-3 py-2.5 font-medium text-gray-500">Narration</th>
                  <th className="text-left px-3 py-2.5 font-medium text-gray-500">Category</th>
                  <th className="text-left px-3 py-2.5 font-medium text-gray-500">Channel</th>
                  <th className="text-right px-3 py-2.5 font-medium text-gray-500">Debit</th>
                  <th className="text-right px-3 py-2.5 font-medium text-gray-500">Credit</th>
                  <th className="text-right px-3 py-2.5 font-medium text-gray-500">Balance</th>
                  <th className="text-center px-3 py-2.5 font-medium text-gray-500">Flags</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.map((t) => (
                  <tr key={t.id} className="hover:bg-gray-50">
                    <td className="px-3 py-2.5 text-gray-600 whitespace-nowrap">{formatDate(t.transactionDate)}</td>
                    <td className="px-3 py-2.5 text-gray-900 max-w-xs truncate" title={t.narration}>
                      {t.narration}
                    </td>
                    <td className="px-3 py-2.5">
                      <span className="px-2 py-0.5 bg-gray-100 text-gray-700 text-xs rounded-full">
                        {categoryLabel(t.category)}
                      </span>
                    </td>
                    <td className="px-3 py-2.5 text-gray-500 text-xs">{t.channel || '-'}</td>
                    <td className="px-3 py-2.5 text-right text-red-700">
                      {t.debitAmount > 0 ? formatCurrency(t.debitAmount) : ''}
                    </td>
                    <td className="px-3 py-2.5 text-right text-green-700">
                      {t.creditAmount > 0 ? formatCurrency(t.creditAmount) : ''}
                    </td>
                    <td className="px-3 py-2.5 text-right text-gray-600">
                      {t.runningBalance != null ? formatCurrency(t.runningBalance) : '-'}
                    </td>
                    <td className="px-3 py-2.5 text-center">
                      <div className="flex gap-1 justify-center">
                        {t.isBounce && <span className="px-1.5 py-0.5 bg-red-100 text-red-700 text-xs rounded">B</span>}
                        {t.isReversal && <span className="px-1.5 py-0.5 bg-yellow-100 text-yellow-700 text-xs rounded">R</span>}
                        {t.isCircular && <span className="px-1.5 py-0.5 bg-orange-100 text-orange-700 text-xs rounded">C</span>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 mt-4">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg disabled:opacity-50"
              >
                Previous
              </button>
              <span className="text-sm text-gray-600">
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg disabled:opacity-50"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
