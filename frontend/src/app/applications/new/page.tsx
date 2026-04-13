'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, ArrowRight, Check } from 'lucide-react';
import { applicationApi } from '@/lib/api';
import type { BorrowerType, CreateApplicationRequest } from '@/types';

const STEPS = ['Borrower Info', 'Loan Details', 'Financial Info', 'Review & Submit'];
const BORROWER_TYPES: { value: BorrowerType; label: string; desc: string }[] = [
  { value: 'INDIVIDUAL', label: 'Individual', desc: 'Salaried or self-employed individual' },
  { value: 'PROPRIETOR', label: 'Proprietorship', desc: 'Sole proprietor business' },
  { value: 'PARTNERSHIP', label: 'Partnership', desc: 'Partnership firm' },
  { value: 'COMPANY', label: 'Company', desc: 'Private or public limited company' },
];
const LOAN_PRODUCTS = ['Term Loan', 'Business Loan', 'Personal Loan', 'Working Capital', 'LAP'];

export default function NewApplicationPage() {
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [form, setForm] = useState({
    borrowerType: '' as BorrowerType | '',
    loanProduct: '',
    requestedAmount: '',
    interestRate: '',
    tenureMonths: '',
    fullName: '',
    email: '',
    mobile: '',
    panNumber: '',
    aadhaarNumber: '',
    dateOfBirth: '',
    address: '',
    businessName: '',
    gstin: '',
    monthlyIncome: '',
    existingEmi: '',
    employmentType: '',
    yearsInBusiness: '',
  });

  const updateForm = (field: string, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const canProceed = () => {
    switch (step) {
      case 0: return form.borrowerType && form.fullName && form.mobile;
      case 1: return form.loanProduct && form.requestedAmount;
      case 2: return form.monthlyIncome;
      case 3: return true;
      default: return false;
    }
  };

  const handleSubmit = async () => {
    setError('');
    setSubmitting(true);
    try {
      const request: CreateApplicationRequest = {
        borrowerType: form.borrowerType as BorrowerType,
        loanProduct: form.loanProduct,
        requestedAmount: parseFloat(form.requestedAmount),
        interestRate: form.interestRate ? parseFloat(form.interestRate) : undefined,
        tenureMonths: form.tenureMonths ? parseInt(form.tenureMonths) : undefined,
        personalInfo: {
          fullName: form.fullName,
          email: form.email,
          mobile: form.mobile,
          panNumber: form.panNumber,
          aadhaarNumber: form.aadhaarNumber,
          dateOfBirth: form.dateOfBirth,
          address: form.address,
        },
        businessInfo: form.businessName ? {
          businessName: form.businessName,
          gstin: form.gstin,
        } : undefined,
        financialInfo: {
          monthlyIncome: parseFloat(form.monthlyIncome),
          existingEmi: form.existingEmi ? parseFloat(form.existingEmi) : 0,
          employmentType: form.employmentType,
          yearsInBusiness: form.yearsInBusiness ? parseInt(form.yearsInBusiness) : undefined,
        },
      };
      const result = await applicationApi.create(request);
      router.push(`/applications/${result.id}`);
    } catch {
      setError('Failed to create application. Please try again.');
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <button onClick={() => router.back()} className="p-2 text-slate-400 hover:text-slate-600">
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 className="text-xl font-bold text-slate-900">New Loan Application</h1>
          <p className="text-sm text-slate-500">Fill in the details to create a new application</p>
        </div>
      </div>

      {/* Progress Steps */}
      <div className="bg-card-bg rounded-xl border border-border p-5">
        <div className="flex items-center justify-between">
          {STEPS.map((label, i) => (
            <div key={label} className="flex items-center gap-2">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium ${
                i < step ? 'bg-green-100 text-green-700' :
                i === step ? 'bg-primary text-white' :
                'bg-slate-100 text-slate-400'
              }`}>
                {i < step ? <Check size={14} /> : i + 1}
              </div>
              <span className={`text-sm hidden sm:inline ${i === step ? 'text-slate-900 font-medium' : 'text-slate-400'}`}>
                {label}
              </span>
              {i < STEPS.length - 1 && <div className="w-8 lg:w-16 h-px bg-slate-200 mx-2" />}
            </div>
          ))}
        </div>
      </div>

      {/* Form Steps */}
      <div className="bg-card-bg rounded-xl border border-border p-6 space-y-5">
        {step === 0 && (
          <>
            <h2 className="text-base font-semibold text-slate-900">Borrower Information</h2>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">Borrower Type</label>
              <div className="grid grid-cols-2 gap-3">
                {BORROWER_TYPES.map((bt) => (
                  <button
                    key={bt.value}
                    onClick={() => updateForm('borrowerType', bt.value)}
                    className={`text-left p-3 rounded-lg border-2 transition-colors ${
                      form.borrowerType === bt.value ? 'border-primary bg-blue-50' : 'border-border hover:border-slate-300'
                    }`}
                  >
                    <p className="text-sm font-medium text-slate-900">{bt.label}</p>
                    <p className="text-xs text-slate-500 mt-0.5">{bt.desc}</p>
                  </button>
                ))}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Full Name *</label>
                <input type="text" value={form.fullName} onChange={(e) => updateForm('fullName', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="Enter full name" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Mobile Number *</label>
                <input type="tel" value={form.mobile} onChange={(e) => updateForm('mobile', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="10-digit mobile" maxLength={10} />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Email</label>
                <input type="email" value={form.email} onChange={(e) => updateForm('email', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="email@example.com" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Date of Birth</label>
                <input type="date" value={form.dateOfBirth} onChange={(e) => updateForm('dateOfBirth', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">PAN Number</label>
                <input type="text" value={form.panNumber} onChange={(e) => updateForm('panNumber', e.target.value.toUpperCase())}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="ABCDE1234F" maxLength={10} />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Aadhaar Number</label>
                <input type="text" value={form.aadhaarNumber} onChange={(e) => updateForm('aadhaarNumber', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="12-digit Aadhaar" maxLength={12} />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Address</label>
              <textarea value={form.address} onChange={(e) => updateForm('address', e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" rows={2} placeholder="Full address" />
            </div>
            {(form.borrowerType === 'PROPRIETOR' || form.borrowerType === 'PARTNERSHIP' || form.borrowerType === 'COMPANY') && (
              <div className="grid grid-cols-2 gap-4 pt-2 border-t border-border">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Business Name</label>
                  <input type="text" value={form.businessName} onChange={(e) => updateForm('businessName', e.target.value)}
                    className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="Business name" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">GSTIN</label>
                  <input type="text" value={form.gstin} onChange={(e) => updateForm('gstin', e.target.value.toUpperCase())}
                    className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="15-digit GSTIN" maxLength={15} />
                </div>
              </div>
            )}
          </>
        )}

        {step === 1 && (
          <>
            <h2 className="text-base font-semibold text-slate-900">Loan Details</h2>
            <div className="grid grid-cols-2 gap-4">
              <div className="col-span-2">
                <label className="block text-sm font-medium text-slate-700 mb-2">Loan Product *</label>
                <div className="grid grid-cols-3 gap-2">
                  {LOAN_PRODUCTS.map((product) => (
                    <button key={product} onClick={() => updateForm('loanProduct', product)}
                      className={`p-3 rounded-lg border-2 text-sm font-medium transition-colors ${
                        form.loanProduct === product ? 'border-primary bg-blue-50 text-primary' : 'border-border text-slate-600 hover:border-slate-300'
                      }`}>
                      {product}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Requested Amount (INR) *</label>
                <input type="number" value={form.requestedAmount} onChange={(e) => updateForm('requestedAmount', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="e.g. 500000" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Interest Rate (%)</label>
                <input type="number" step="0.1" value={form.interestRate} onChange={(e) => updateForm('interestRate', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="e.g. 12.5" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Tenure (Months)</label>
                <input type="number" value={form.tenureMonths} onChange={(e) => updateForm('tenureMonths', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="e.g. 36" />
              </div>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <h2 className="text-base font-semibold text-slate-900">Financial Information</h2>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Monthly Income (INR) *</label>
                <input type="number" value={form.monthlyIncome} onChange={(e) => updateForm('monthlyIncome', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="e.g. 50000" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Existing EMI (INR)</label>
                <input type="number" value={form.existingEmi} onChange={(e) => updateForm('existingEmi', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="e.g. 10000" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Employment Type</label>
                <select value={form.employmentType} onChange={(e) => updateForm('employmentType', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary">
                  <option value="">Select</option>
                  <option value="SALARIED">Salaried</option>
                  <option value="SELF_EMPLOYED">Self Employed</option>
                  <option value="BUSINESS">Business Owner</option>
                  <option value="PROFESSIONAL">Professional</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Years in Business/Employment</label>
                <input type="number" value={form.yearsInBusiness} onChange={(e) => updateForm('yearsInBusiness', e.target.value)}
                  className="w-full px-3 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary" placeholder="e.g. 5" />
              </div>
            </div>
          </>
        )}

        {step === 3 && (
          <>
            <h2 className="text-base font-semibold text-slate-900">Review & Submit</h2>
            <div className="space-y-4">
              <div className="bg-slate-50 rounded-lg p-4 space-y-3">
                <h3 className="text-sm font-medium text-slate-700">Borrower</h3>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <div><span className="text-slate-500">Name:</span> <span className="font-medium">{form.fullName}</span></div>
                  <div><span className="text-slate-500">Type:</span> <span className="font-medium">{form.borrowerType}</span></div>
                  <div><span className="text-slate-500">Mobile:</span> <span className="font-medium">{form.mobile}</span></div>
                  <div><span className="text-slate-500">PAN:</span> <span className="font-medium">{form.panNumber || '—'}</span></div>
                </div>
              </div>
              <div className="bg-slate-50 rounded-lg p-4 space-y-3">
                <h3 className="text-sm font-medium text-slate-700">Loan</h3>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <div><span className="text-slate-500">Product:</span> <span className="font-medium">{form.loanProduct}</span></div>
                  <div><span className="text-slate-500">Amount:</span> <span className="font-medium">&#8377;{parseInt(form.requestedAmount || '0').toLocaleString('en-IN')}</span></div>
                  <div><span className="text-slate-500">Rate:</span> <span className="font-medium">{form.interestRate || '—'}%</span></div>
                  <div><span className="text-slate-500">Tenure:</span> <span className="font-medium">{form.tenureMonths || '—'} months</span></div>
                </div>
              </div>
              <div className="bg-slate-50 rounded-lg p-4 space-y-3">
                <h3 className="text-sm font-medium text-slate-700">Financial</h3>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <div><span className="text-slate-500">Monthly Income:</span> <span className="font-medium">&#8377;{parseInt(form.monthlyIncome || '0').toLocaleString('en-IN')}</span></div>
                  <div><span className="text-slate-500">Existing EMI:</span> <span className="font-medium">&#8377;{parseInt(form.existingEmi || '0').toLocaleString('en-IN')}</span></div>
                  <div><span className="text-slate-500">Employment:</span> <span className="font-medium">{form.employmentType || '—'}</span></div>
                </div>
              </div>
            </div>
            {error && <p className="text-sm text-danger bg-red-50 px-3 py-2 rounded-lg">{error}</p>}
          </>
        )}
      </div>

      {/* Navigation Buttons */}
      <div className="flex items-center justify-between">
        <button
          onClick={() => setStep((s) => Math.max(0, s - 1))}
          disabled={step === 0}
          className="flex items-center gap-2 px-4 py-2 text-sm text-slate-600 hover:text-slate-900 disabled:opacity-30 transition-colors"
        >
          <ArrowLeft size={16} /> Previous
        </button>
        {step < STEPS.length - 1 ? (
          <button
            onClick={() => setStep((s) => s + 1)}
            disabled={!canProceed()}
            className="flex items-center gap-2 bg-primary text-white px-5 py-2 rounded-lg text-sm font-medium hover:bg-primary-hover transition-colors disabled:opacity-50"
          >
            Next <ArrowRight size={16} />
          </button>
        ) : (
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="flex items-center gap-2 bg-green-600 text-white px-5 py-2 rounded-lg text-sm font-medium hover:bg-green-700 transition-colors disabled:opacity-50"
          >
            {submitting ? 'Submitting...' : 'Submit Application'}
          </button>
        )}
      </div>
    </div>
  );
}
