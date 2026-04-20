'use client';

import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';
import {
  Clock,
  AlertTriangle,
  CheckCircle,
  XCircle,
} from 'lucide-react';
import { applicationApi, kycApi } from '@/lib/api';
import type { KycOutcomeResponse, LoanApplication } from '@/types';

type QueueRow = {
  id: string;
  applicationNumber: string;
  borrowerName: string;
  product: string;
  status: string;
  kycOutcome: string;
  bureauScore: number;
};

type QueueBucket = {
  key: string;
  title: string;
  description: string;
  icon: ReactNode;
  rows: QueueRow[];
};

export default function WorkflowsPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [apps, setApps] = useState<LoanApplication[]>([]);
  const [outcomesById, setOutcomesById] = useState<Record<string, KycOutcomeResponse | null>>({});

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const page = await applicationApi.list({ page: 0, size: 500 });
        const content = (page.content ?? []) as LoanApplication[];

        const pairs = await Promise.all(
          content.map(async (app: LoanApplication) => {
            try {
              const outcome = (await kycApi.getOutcome(String(app.id))) as KycOutcomeResponse;
              return [String(app.id), outcome] as const;
            } catch (e) {
              return [String(app.id), null] as const;
            }
          })
        );

        const map: Record<string, KycOutcomeResponse | null> = {};
        for (const [id, outcome] of pairs) map[id] = outcome;

        if (!cancelled) {
          setApps(content);
          setOutcomesById(map);
        }
      } catch (e: any) {
        if (!cancelled) {
          setError(e?.message ? String(e.message) : 'Failed to load workflows/queues');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const buckets = useMemo(() => {
    const rows: QueueRow[] = apps.map((app) => {
      const personalInfo = (app.personalInfo ?? {}) as any;
      const borrowerName =
        String(personalInfo.fullName ?? personalInfo.name ?? `${personalInfo.firstName ?? ''} ${personalInfo.lastName ?? ''}`)
          .trim() ||
        '—';

      const outcome = outcomesById[String(app.id)];

      return {
        id: String(app.id),
        applicationNumber: String((app as any).applicationNumber ?? '—'),
        borrowerName,
        product: String(app.loanProduct ?? '—'),
        status: String(app.status ?? '—'),
        kycOutcome: String(outcome?.outcome ?? 'INCOMPLETE'),
        bureauScore: Number((app as any).bureauScore ?? 0),
      };
    });

    const awaitingKyc = rows.filter((r) => ['DRAFT', 'CONSENT_PENDING', 'KYC_IN_PROGRESS'].includes(r.status) && r.kycOutcome === 'INCOMPLETE');
    const kycIssues = rows.filter((r) => r.kycOutcome === 'FAIL' || r.kycOutcome === 'INCOMPLETE');
    const readyForBureau = rows.filter((r) => r.kycOutcome === 'PASS' && (!r.bureauScore || r.bureauScore <= 0));
    const readyForUnderwriting = rows.filter((r) => r.kycOutcome === 'PASS' && r.bureauScore > 0 && ['KYC_IN_PROGRESS', 'KYC_FAILED', 'ON_HOLD'].includes(r.status));
    const approvedAwaitingSanction = rows.filter((r) => r.status === 'APPROVED');
    const sanctionedAwaitingEsign = rows.filter((r) => r.status === 'SANCTION_ISSUED');
    const esignCompletedAwaitingDisbursement = rows.filter((r) => r.status === 'ESIGN_COMPLETED');

    const mk = (key: string, title: string, description: string, icon: ReactNode, rs: QueueRow[]): QueueBucket => ({
      key,
      title,
      description,
      icon,
      rows: rs,
    });

    return [
      mk('awaiting-kyc', 'Submitted / awaiting KYC', 'Applications awaiting KYC completion', <Clock size={16} className="text-slate-500" />, awaitingKyc),
      mk('kyc-issues', 'KYC incomplete / failed', 'KYC not PASS (blocked for bureau/underwriting)', <AlertTriangle size={16} className="text-amber-600" />, kycIssues),
      mk('ready-bureau', 'Ready for bureau', 'KYC PASS and bureau not pulled', <CheckCircle size={16} className="text-green-600" />, readyForBureau),
      mk('ready-underwrite', 'Ready for underwriting', 'KYC PASS and bureau score present', <CheckCircle size={16} className="text-green-600" />, readyForUnderwriting),
      mk('approved-sanction', 'Approved awaiting sanction', 'Status APPROVED', <CheckCircle size={16} className="text-green-600" />, approvedAwaitingSanction),
      mk('sanction-esign', 'Sanctioned awaiting eSign', 'Status SANCTION_ISSUED', <Clock size={16} className="text-slate-500" />, sanctionedAwaitingEsign),
      mk('esign-disburse', 'eSign completed awaiting disbursement', 'Status ESIGN_COMPLETED', <Clock size={16} className="text-slate-500" />, esignCompletedAwaitingDisbursement),
    ];
  }, [apps, outcomesById]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold text-slate-900">Workflows / Queues</h1>
        <p className="text-sm text-slate-500 mt-0.5">Operational queues derived from live application state</p>
      </div>

      {loading ? (
        <div className="bg-card-bg rounded-xl border border-border p-6 text-sm text-slate-600">Loading…</div>
      ) : error ? (
        <div className="bg-card-bg rounded-xl border border-border p-6 text-sm text-red-600">{error}</div>
      ) : (
        <div className="space-y-4">
          {buckets.map((b) => (
            <div key={b.key} className="bg-card-bg rounded-xl border border-border overflow-hidden">
              <div className="px-5 py-4 border-b border-border flex items-center justify-between">
                <div className="flex items-center gap-2">
                  {b.icon}
                  <div>
                    <div className="text-sm font-semibold text-slate-900">{b.title}</div>
                    <div className="text-xs text-slate-500">{b.description}</div>
                  </div>
                </div>
                <div className="text-sm font-bold text-slate-900">{b.rows.length}</div>
              </div>

              {b.rows.length === 0 ? (
                <div className="p-5 text-sm text-slate-500">No items</div>
              ) : (
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-border bg-slate-50/50">
                      <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Application</th>
                      <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Borrower</th>
                      <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Product</th>
                      <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Status</th>
                      <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">KYC</th>
                      <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Bureau</th>
                    </tr>
                  </thead>
                  <tbody>
                    {b.rows.map((r) => (
                      <tr key={r.id} className="border-b border-border last:border-0 hover:bg-slate-50">
                        <td className="px-5 py-3">
                          <a href={`/applications/${r.id}`} className="text-sm text-primary hover:underline font-medium">
                            {r.applicationNumber}
                          </a>
                        </td>
                        <td className="px-5 py-3 text-sm text-slate-700">{r.borrowerName}</td>
                        <td className="px-5 py-3 text-sm text-slate-600">{r.product}</td>
                        <td className="px-5 py-3 text-sm text-slate-600">{r.status}</td>
                        <td className="px-5 py-3 text-sm text-slate-600">{r.kycOutcome}</td>
                        <td className="px-5 py-3 text-sm text-slate-600">{r.bureauScore > 0 ? r.bureauScore : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
