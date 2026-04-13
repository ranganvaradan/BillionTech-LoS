import type { ApplicationStatus } from '@/types';
import { clsx, type ClassValue } from 'clsx';

export function cn(...inputs: ClassValue[]) {
  return clsx(inputs);
}

export const STATUS_CONFIG: Record<
  ApplicationStatus,
  { label: string; color: string; bg: string }
> = {
  DRAFT: { label: 'Draft', color: 'text-gray-700', bg: 'bg-gray-100' },
  CONSENT_PENDING: { label: 'Consent Pending', color: 'text-amber-700', bg: 'bg-amber-50' },
  KYC_IN_PROGRESS: { label: 'KYC In Progress', color: 'text-blue-700', bg: 'bg-blue-50' },
  KYC_FAILED: { label: 'KYC Failed', color: 'text-red-700', bg: 'bg-red-50' },
  UNDERWRITING: { label: 'Underwriting', color: 'text-purple-700', bg: 'bg-purple-50' },
  APPROVED: { label: 'Approved', color: 'text-green-700', bg: 'bg-green-50' },
  REJECTED: { label: 'Rejected', color: 'text-red-700', bg: 'bg-red-100' },
  SANCTION_ISSUED: { label: 'Sanction Issued', color: 'text-emerald-700', bg: 'bg-emerald-50' },
  ESIGN_PENDING: { label: 'eSign Pending', color: 'text-orange-700', bg: 'bg-orange-50' },
  DISBURSEMENT_PENDING: { label: 'Disbursement Pending', color: 'text-cyan-700', bg: 'bg-cyan-50' },
  DISBURSED: { label: 'Disbursed', color: 'text-green-800', bg: 'bg-green-100' },
  WITHDRAWN: { label: 'Withdrawn', color: 'text-gray-600', bg: 'bg-gray-200' },
  ON_HOLD: { label: 'On Hold', color: 'text-yellow-700', bg: 'bg-yellow-50' },
};

export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

export function formatDateTime(dateStr: string): string {
  return new Date(dateStr).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function getBorrowerLabel(type: string): string {
  const labels: Record<string, string> = {
    INDIVIDUAL: 'Individual',
    PROPRIETOR: 'Proprietorship',
    PARTNERSHIP: 'Partnership',
    COMPANY: 'Company',
  };
  return labels[type] || type;
}
