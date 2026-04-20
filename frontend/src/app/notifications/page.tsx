'use client';

import { useState } from 'react';
import { Bell, Mail, MessageSquare, Smartphone, Search, RefreshCw, CheckCircle, XCircle, Clock } from 'lucide-react';
import { formatDateTime } from '@/lib/utils';

interface NotificationItem {
  id: string;
  channel: 'SMS' | 'EMAIL' | 'WHATSAPP';
  recipient: string;
  templateCode: string;
  eventType: string;
  status: 'SENT' | 'FAILED' | 'PENDING' | 'PERMANENTLY_FAILED';
  applicationNumber: string;
  borrowerName: string;
  createdAt: string;
  sentAt?: string;
  errorMessage?: string;
  retryCount: number;
}

const MOCK_NOTIFICATIONS: NotificationItem[] = [
  { id: 'n1', channel: 'EMAIL', recipient: 'amit.patel@email.com', templateCode: 'APPLICATION_CREATED', eventType: 'APPLICATION_CREATED', status: 'SENT', applicationNumber: 'LOS-IND-20260413-00001', borrowerName: 'Amit Patel', createdAt: '2026-04-13T09:00:00Z', sentAt: '2026-04-13T09:00:02Z', retryCount: 0 },
  { id: 'n2', channel: 'SMS', recipient: '+919876543210', templateCode: 'APPLICATION_CREATED', eventType: 'APPLICATION_CREATED', status: 'SENT', applicationNumber: 'LOS-IND-20260413-00001', borrowerName: 'Amit Patel', createdAt: '2026-04-13T09:00:00Z', sentAt: '2026-04-13T09:00:01Z', retryCount: 0 },
  { id: 'n3', channel: 'WHATSAPP', recipient: '+919876543210', templateCode: 'APPLICATION_CREATED', eventType: 'APPLICATION_CREATED', status: 'SENT', applicationNumber: 'LOS-IND-20260413-00001', borrowerName: 'Amit Patel', createdAt: '2026-04-13T09:00:00Z', sentAt: '2026-04-13T09:00:03Z', retryCount: 0 },
  { id: 'n4', channel: 'EMAIL', recipient: 'priya@enterprise.com', templateCode: 'KYC_COMPLETED', eventType: 'KYC_COMPLETED', status: 'SENT', applicationNumber: 'LOS-PRP-20260411-00002', borrowerName: 'Priya Enterprises', createdAt: '2026-04-12T14:00:00Z', sentAt: '2026-04-12T14:00:05Z', retryCount: 0 },
  { id: 'n5', channel: 'SMS', recipient: '+919812345678', templateCode: 'APPLICATION_APPROVED', eventType: 'APPLICATION_APPROVED', status: 'SENT', applicationNumber: 'LOS-IND-20260410-00005', borrowerName: 'Rahul Sharma', createdAt: '2026-04-11T16:00:00Z', sentAt: '2026-04-11T16:00:01Z', retryCount: 0 },
  { id: 'n6', channel: 'EMAIL', recipient: 'rahul.sharma@gmail.com', templateCode: 'APPLICATION_APPROVED', eventType: 'APPLICATION_APPROVED', status: 'FAILED', applicationNumber: 'LOS-IND-20260410-00005', borrowerName: 'Rahul Sharma', createdAt: '2026-04-11T16:00:00Z', errorMessage: 'SMTP connection timeout', retryCount: 2 },
  { id: 'n7', channel: 'EMAIL', recipient: 'green.energy@partners.com', templateCode: 'SANCTION_ISSUED', eventType: 'SANCTION_ISSUED', status: 'SENT', applicationNumber: 'LOS-PRT-20260408-00001', borrowerName: 'Green Energy Partners', createdAt: '2026-04-10T10:00:00Z', sentAt: '2026-04-10T10:00:04Z', retryCount: 0 },
  { id: 'n8', channel: 'SMS', recipient: '+919900112233', templateCode: 'DISBURSEMENT_COMPLETED', eventType: 'DISBURSEMENT_COMPLETED', status: 'SENT', applicationNumber: 'LOS-IND-20260410-00005', borrowerName: 'Amit Patel', createdAt: '2026-04-10T12:30:00Z', sentAt: '2026-04-10T12:30:01Z', retryCount: 0 },
  { id: 'n9', channel: 'WHATSAPP', recipient: '+919900112233', templateCode: 'DISBURSEMENT_COMPLETED', eventType: 'DISBURSEMENT_COMPLETED', status: 'PENDING', applicationNumber: 'LOS-IND-20260410-00005', borrowerName: 'Amit Patel', createdAt: '2026-04-10T12:30:00Z', retryCount: 0 },
  { id: 'n10', channel: 'EMAIL', recipient: 'techcorp@pvtltd.com', templateCode: 'KYC_FAILED', eventType: 'KYC_FAILED', status: 'PERMANENTLY_FAILED', applicationNumber: 'LOS-CMP-20260412-00003', borrowerName: 'TechCorp Pvt Ltd', createdAt: '2026-04-12T11:00:00Z', errorMessage: 'Invalid email address', retryCount: 3 },
];

const channelIcon = (channel: string) => {
  switch (channel) {
    case 'SMS': return <Smartphone size={14} />;
    case 'EMAIL': return <Mail size={14} />;
    case 'WHATSAPP': return <MessageSquare size={14} />;
    default: return <Bell size={14} />;
  }
};

const statusBadge = (status: string) => {
  switch (status) {
    case 'SENT': return <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-green-50 text-green-700 font-medium"><CheckCircle size={10} /> Sent</span>;
    case 'FAILED': return <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-red-50 text-red-700 font-medium"><XCircle size={10} /> Failed</span>;
    case 'PENDING': return <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 font-medium"><Clock size={10} /> Pending</span>;
    case 'PERMANENTLY_FAILED': return <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium"><XCircle size={10} /> Perm. Failed</span>;
    default: return <span className="text-xs text-slate-500">{status}</span>;
  }
};

export default function NotificationsPage() {
  const [channelFilter, setChannelFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [searchTerm, setSearchTerm] = useState('');

  const filtered = MOCK_NOTIFICATIONS.filter((n) => {
    if (channelFilter && n.channel !== channelFilter) return false;
    if (statusFilter && n.status !== statusFilter) return false;
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      return n.applicationNumber.toLowerCase().includes(term) ||
        n.borrowerName.toLowerCase().includes(term) ||
        n.recipient.toLowerCase().includes(term);
    }
    return true;
  });

  const totalSent = MOCK_NOTIFICATIONS.filter(n => n.status === 'SENT').length;
  const totalFailed = MOCK_NOTIFICATIONS.filter(n => n.status === 'FAILED' || n.status === 'PERMANENTLY_FAILED').length;
  const totalPending = MOCK_NOTIFICATIONS.filter(n => n.status === 'PENDING').length;
  const successRate = MOCK_NOTIFICATIONS.length > 0 ? ((totalSent / MOCK_NOTIFICATIONS.length) * 100).toFixed(1) : '0';

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-900">Notifications</h1>
        <p className="text-sm text-slate-500 mt-0.5">SMS, Email & WhatsApp notification history and management</p>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-card-bg rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 text-slate-400 mb-2">
            <Bell size={16} /> <span className="text-xs font-medium">Total Notifications</span>
          </div>
          <p className="text-xl font-bold text-slate-900">{MOCK_NOTIFICATIONS.length}</p>
        </div>
        <div className="bg-card-bg rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 text-green-500 mb-2">
            <CheckCircle size={16} /> <span className="text-xs font-medium">Sent</span>
          </div>
          <p className="text-xl font-bold text-green-700">{totalSent}</p>
        </div>
        <div className="bg-card-bg rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 text-red-400 mb-2">
            <XCircle size={16} /> <span className="text-xs font-medium">Failed</span>
          </div>
          <p className="text-xl font-bold text-red-600">{totalFailed}</p>
        </div>
        <div className="bg-card-bg rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 text-slate-400 mb-2">
            <RefreshCw size={16} /> <span className="text-xs font-medium">Success Rate</span>
          </div>
          <p className="text-xl font-bold text-primary">{successRate}%</p>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-card-bg rounded-xl border border-border p-4 flex gap-3 items-center">
        <div className="flex items-center gap-2 bg-slate-50 rounded-lg px-3 py-2 flex-1">
          <Search size={16} className="text-slate-400" />
          <input
            type="text"
            placeholder="Search by application, borrower, or recipient..."
            className="bg-transparent text-sm outline-none w-full placeholder-slate-400"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <select
          value={channelFilter}
          onChange={(e) => setChannelFilter(e.target.value)}
          className="text-sm border border-border rounded-lg px-3 py-2 bg-white outline-none"
        >
          <option value="">All Channels</option>
          <option value="SMS">SMS</option>
          <option value="EMAIL">Email</option>
          <option value="WHATSAPP">WhatsApp</option>
        </select>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="text-sm border border-border rounded-lg px-3 py-2 bg-white outline-none"
        >
          <option value="">All Statuses</option>
          <option value="SENT">Sent</option>
          <option value="FAILED">Failed</option>
          <option value="PENDING">Pending</option>
          <option value="PERMANENTLY_FAILED">Perm. Failed</option>
        </select>
      </div>

      {/* Table */}
      <div className="bg-card-bg rounded-xl border border-border overflow-hidden">
        <div className="px-5 py-3 border-b border-border bg-slate-50/50">
          <span className="text-xs text-slate-500">{filtered.length} notification{filtered.length !== 1 ? 's' : ''}</span>
        </div>
        <table className="w-full">
          <thead>
            <tr className="border-b border-border">
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Channel</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Recipient</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Application</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Event</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Status</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Sent At</th>
              <th className="text-left text-xs font-medium text-slate-500 px-5 py-3">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((n) => (
              <tr key={n.id} className="border-b border-border last:border-0 hover:bg-slate-50">
                <td className="px-5 py-3">
                  <span className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-700">
                    {channelIcon(n.channel)} {n.channel}
                  </span>
                </td>
                <td className="px-5 py-3 text-sm text-slate-700">{n.recipient}</td>
                <td className="px-5 py-3">
                  <div>
                    <p className="text-sm text-primary font-medium">{n.applicationNumber}</p>
                    <p className="text-xs text-slate-400">{n.borrowerName}</p>
                  </div>
                </td>
                <td className="px-5 py-3 text-xs text-slate-600 font-mono">{n.templateCode}</td>
                <td className="px-5 py-3">
                  {statusBadge(n.status)}
                  {n.errorMessage && <p className="text-[10px] text-red-400 mt-0.5">{n.errorMessage}</p>}
                </td>
                <td className="px-5 py-3 text-xs text-slate-500">{n.sentAt ? formatDateTime(n.sentAt) : '—'}</td>
                <td className="px-5 py-3">
                  {(n.status === 'FAILED' || n.status === 'PERMANENTLY_FAILED') && (
                    <button className="text-xs text-primary hover:underline flex items-center gap-1">
                      <RefreshCw size={10} /> Resend
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
