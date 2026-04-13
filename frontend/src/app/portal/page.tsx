'use client';

import { useState } from 'react';
import {
  Phone,
  Shield,
  FileText,
  PenTool,
  CreditCard,
  CheckCircle,
  ArrowRight,
  ArrowLeft,
  Upload,
  Camera,
  Eye,
  Clock,
  AlertCircle,
  Home,
  User,
  LogOut,
} from 'lucide-react';

type PortalStep = 'login' | 'dashboard' | 'kyc' | 'documents' | 'esign' | 'status';

type LoanAccount = {
  applicationNumber: string;
  loanProduct: string;
  status: string;
  sanctionedAmount: number;
  disbursedAmount: number;
  outstandingBalance: number;
  nextEmiDate: string;
  nextEmiAmount: number;
  emiPaid: number;
  totalEmis: number;
};

const MOCK_LOANS: LoanAccount[] = [
  {
    applicationNumber: 'LOS-IND-20260409-00001',
    loanProduct: 'Personal Loan',
    status: 'ACTIVE',
    sanctionedAmount: 500000,
    disbursedAmount: 500000,
    outstandingBalance: 485000,
    nextEmiDate: '2026-05-05',
    nextEmiAmount: 16250,
    emiPaid: 1,
    totalEmis: 36,
  },
  {
    applicationNumber: 'LOS-IND-20260320-00045',
    loanProduct: 'Home Loan',
    status: 'ESIGN_PENDING',
    sanctionedAmount: 4500000,
    disbursedAmount: 0,
    outstandingBalance: 0,
    nextEmiDate: '-',
    nextEmiAmount: 45200,
    emiPaid: 0,
    totalEmis: 240,
  },
];

const KYC_STEPS = [
  { type: 'AADHAAR_OTP', label: 'Aadhaar Verification', status: 'COMPLETED', icon: Shield },
  { type: 'PAN_VERIFY', label: 'PAN Verification', status: 'COMPLETED', icon: CreditCard },
  { type: 'FACE_MATCH', label: 'Face Match', status: 'IN_PROGRESS', icon: Camera },
  { type: 'LIVENESS', label: 'Liveness Check', status: 'PENDING', icon: Eye },
  { type: 'BANK_PENNY_DROP', label: 'Bank Verification', status: 'PENDING', icon: CreditCard },
];

export default function CustomerPortalPage() {
  const [step, setStep] = useState<PortalStep>('login');
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [activeTab, setActiveTab] = useState<'loans' | 'applications'>('loans');

  const formatCurrency = (n: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);

  // Login Screen
  if (step === 'login') {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <div className="w-full max-w-md">
          <div className="text-center mb-8">
            <div className="w-16 h-16 rounded-2xl bg-blue-600 flex items-center justify-center text-white font-bold text-xl mx-auto mb-4">
              LOS
            </div>
            <h1 className="text-2xl font-bold text-gray-900">Customer Portal</h1>
            <p className="text-gray-500 mt-1">Track your loan applications</p>
          </div>

          <div className="bg-white rounded-2xl shadow-lg p-6 space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Mobile Number</label>
              <div className="flex gap-2">
                <span className="flex items-center px-3 bg-gray-100 border border-gray-300 rounded-lg text-sm text-gray-600">+91</span>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))}
                  placeholder="Enter 10-digit mobile"
                  className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
              </div>
            </div>

            {!otpSent ? (
              <button
                onClick={() => { if (phone.length === 10) setOtpSent(true); }}
                disabled={phone.length !== 10}
                className="w-full py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                <Phone size={16} /> Send OTP
              </button>
            ) : (
              <>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Enter OTP</label>
                  <input
                    type="text"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    placeholder="6-digit OTP"
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm text-center tracking-[0.5em] focus:ring-2 focus:ring-blue-500"
                    maxLength={6}
                  />
                  <p className="text-xs text-gray-500 mt-1">OTP sent to +91 {phone}</p>
                </div>
                <button
                  onClick={() => { if (otp.length === 6) setStep('dashboard'); }}
                  disabled={otp.length !== 6}
                  className="w-full py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Verify & Login
                </button>
              </>
            )}

            <p className="text-xs text-center text-gray-400">
              Demo: Any 10-digit number + any 6-digit OTP
            </p>
          </div>
        </div>
      </div>
    );
  }

  // Portal Header (for authenticated pages)
  const PortalHeader = () => (
    <header className="bg-white shadow-sm border-b border-gray-200">
      <div className="max-w-5xl mx-auto px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center text-white font-bold text-xs">
            LOS
          </div>
          <span className="font-semibold text-gray-900">My Loans</span>
        </div>
        <div className="flex items-center gap-4">
          <button
            onClick={() => setStep('dashboard')}
            className={`text-sm ${step === 'dashboard' ? 'text-blue-600 font-medium' : 'text-gray-500 hover:text-gray-700'}`}
          >
            <Home size={16} className="inline mr-1" /> Home
          </button>
          <button
            onClick={() => setStep('kyc')}
            className={`text-sm ${step === 'kyc' ? 'text-blue-600 font-medium' : 'text-gray-500 hover:text-gray-700'}`}
          >
            <Shield size={16} className="inline mr-1" /> KYC
          </button>
          <button
            onClick={() => setStep('documents')}
            className={`text-sm ${step === 'documents' ? 'text-blue-600 font-medium' : 'text-gray-500 hover:text-gray-700'}`}
          >
            <FileText size={16} className="inline mr-1" /> Documents
          </button>
          <button
            onClick={() => setStep('status')}
            className={`text-sm ${step === 'status' ? 'text-blue-600 font-medium' : 'text-gray-500 hover:text-gray-700'}`}
          >
            <Clock size={16} className="inline mr-1" /> Status
          </button>
          <div className="flex items-center gap-2 pl-4 border-l border-gray-200">
            <User size={16} className="text-gray-400" />
            <span className="text-sm text-gray-600">+91 {phone}</span>
            <button onClick={() => setStep('login')} className="text-gray-400 hover:text-red-500">
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </div>
    </header>
  );

  // Dashboard
  if (step === 'dashboard') {
    return (
      <div>
        <PortalHeader />
        <div className="max-w-5xl mx-auto px-4 py-6 space-y-6">
          <div>
            <h1 className="text-xl font-bold text-gray-900">Welcome back!</h1>
            <p className="text-sm text-gray-500">Here&apos;s an overview of your loan accounts</p>
          </div>

          {/* Tabs */}
          <div className="flex gap-4 border-b border-gray-200">
            <button
              onClick={() => setActiveTab('loans')}
              className={`pb-2 text-sm font-medium border-b-2 ${activeTab === 'loans' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500'}`}
            >
              Active Loans ({MOCK_LOANS.filter((l) => l.status === 'ACTIVE').length})
            </button>
            <button
              onClick={() => setActiveTab('applications')}
              className={`pb-2 text-sm font-medium border-b-2 ${activeTab === 'applications' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500'}`}
            >
              Applications ({MOCK_LOANS.filter((l) => l.status !== 'ACTIVE').length})
            </button>
          </div>

          {/* Loan Cards */}
          <div className="space-y-4">
            {MOCK_LOANS.filter((l) =>
              activeTab === 'loans' ? l.status === 'ACTIVE' : l.status !== 'ACTIVE'
            ).map((loan) => (
              <div key={loan.applicationNumber} className="bg-white rounded-xl shadow-sm border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-3">
                  <div>
                    <p className="text-sm font-mono text-gray-500">{loan.applicationNumber}</p>
                    <h3 className="text-lg font-semibold text-gray-900">{loan.loanProduct}</h3>
                  </div>
                  <span className={`px-3 py-1 rounded-full text-xs font-medium ${
                    loan.status === 'ACTIVE'
                      ? 'bg-green-100 text-green-700'
                      : 'bg-yellow-100 text-yellow-700'
                  }`}>
                    {loan.status.replace(/_/g, ' ')}
                  </span>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div>
                    <p className="text-xs text-gray-500">Sanctioned</p>
                    <p className="text-sm font-semibold text-gray-900">{formatCurrency(loan.sanctionedAmount)}</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500">Outstanding</p>
                    <p className="text-sm font-semibold text-gray-900">{formatCurrency(loan.outstandingBalance)}</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500">Next EMI</p>
                    <p className="text-sm font-semibold text-gray-900">
                      {loan.nextEmiDate !== '-' ? `${formatCurrency(loan.nextEmiAmount)} on ${loan.nextEmiDate}` : 'N/A'}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500">EMIs Paid</p>
                    <p className="text-sm font-semibold text-gray-900">{loan.emiPaid} / {loan.totalEmis}</p>
                  </div>
                </div>

                {loan.status === 'ESIGN_PENDING' && (
                  <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <AlertCircle size={16} className="text-yellow-600" />
                      <span className="text-sm text-yellow-700">eSign pending — please complete to proceed</span>
                    </div>
                    <button
                      onClick={() => setStep('esign')}
                      className="px-4 py-1.5 bg-yellow-600 text-white rounded-lg text-xs font-medium hover:bg-yellow-700"
                    >
                      Complete eSign <ArrowRight size={12} className="inline ml-1" />
                    </button>
                  </div>
                )}

                {loan.status === 'ACTIVE' && (
                  <div className="mt-4">
                    <div className="w-full bg-gray-200 rounded-full h-2">
                      <div
                        className="bg-blue-600 h-2 rounded-full transition-all"
                        style={{ width: `${(loan.emiPaid / loan.totalEmis) * 100}%` }}
                      />
                    </div>
                    <p className="text-xs text-gray-500 mt-1">
                      {((loan.emiPaid / loan.totalEmis) * 100).toFixed(1)}% complete
                    </p>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // KYC Page
  if (step === 'kyc') {
    return (
      <div>
        <PortalHeader />
        <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-bold text-gray-900">Complete Your KYC</h1>
              <p className="text-sm text-gray-500">
                {KYC_STEPS.filter((s) => s.status === 'COMPLETED').length} of {KYC_STEPS.length} steps completed
              </p>
            </div>
            <div className="text-right">
              <div className="text-2xl font-bold text-blue-600">
                {Math.round((KYC_STEPS.filter((s) => s.status === 'COMPLETED').length / KYC_STEPS.length) * 100)}%
              </div>
              <p className="text-xs text-gray-500">Progress</p>
            </div>
          </div>

          {/* Progress Bar */}
          <div className="w-full bg-gray-200 rounded-full h-3">
            <div
              className="bg-blue-600 h-3 rounded-full transition-all"
              style={{ width: `${(KYC_STEPS.filter((s) => s.status === 'COMPLETED').length / KYC_STEPS.length) * 100}%` }}
            />
          </div>

          {/* Steps */}
          <div className="space-y-3">
            {KYC_STEPS.map((kycStep, idx) => {
              const Icon = kycStep.icon;
              return (
                <div
                  key={kycStep.type}
                  className={`bg-white rounded-xl border p-4 flex items-center gap-4 ${
                    kycStep.status === 'IN_PROGRESS'
                      ? 'border-blue-300 ring-2 ring-blue-100'
                      : kycStep.status === 'COMPLETED'
                      ? 'border-green-200'
                      : 'border-gray-200 opacity-60'
                  }`}
                >
                  <div
                    className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${
                      kycStep.status === 'COMPLETED'
                        ? 'bg-green-100 text-green-600'
                        : kycStep.status === 'IN_PROGRESS'
                        ? 'bg-blue-100 text-blue-600'
                        : 'bg-gray-100 text-gray-400'
                    }`}
                  >
                    {kycStep.status === 'COMPLETED' ? <CheckCircle size={20} /> : <Icon size={20} />}
                  </div>
                  <div className="flex-1">
                    <p className="text-sm font-medium text-gray-900">
                      Step {idx + 1}: {kycStep.label}
                    </p>
                    <p className="text-xs text-gray-500">
                      {kycStep.status === 'COMPLETED' && 'Verified successfully'}
                      {kycStep.status === 'IN_PROGRESS' && 'Please complete this step'}
                      {kycStep.status === 'PENDING' && 'Waiting for previous steps'}
                    </p>
                  </div>
                  {kycStep.status === 'IN_PROGRESS' && (
                    <button className="px-4 py-2 bg-blue-600 text-white rounded-lg text-xs font-medium hover:bg-blue-700">
                      Start <ArrowRight size={12} className="inline ml-1" />
                    </button>
                  )}
                  {kycStep.status === 'COMPLETED' && (
                    <span className="text-xs text-green-600 font-medium">Done</span>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    );
  }

  // Documents Page
  if (step === 'documents') {
    const documents = [
      { name: 'Aadhaar Card', type: 'AADHAAR', uploaded: true, fileName: 'aadhaar_front.jpg' },
      { name: 'PAN Card', type: 'PAN', uploaded: true, fileName: 'pan_card.pdf' },
      { name: 'Salary Slip (Last 3 months)', type: 'SALARY_SLIP', uploaded: false, fileName: '' },
      { name: 'Bank Statement (Last 6 months)', type: 'BANK_STATEMENT', uploaded: false, fileName: '' },
      { name: 'Address Proof', type: 'ADDRESS_PROOF', uploaded: true, fileName: 'electricity_bill.pdf' },
      { name: 'Passport Photo', type: 'PHOTO', uploaded: true, fileName: 'photo.jpg' },
    ];

    return (
      <div>
        <PortalHeader />
        <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
          <div>
            <h1 className="text-xl font-bold text-gray-900">Document Upload</h1>
            <p className="text-sm text-gray-500">
              {documents.filter((d) => d.uploaded).length} of {documents.length} documents uploaded
            </p>
          </div>

          <div className="space-y-3">
            {documents.map((doc) => (
              <div key={doc.type} className="bg-white rounded-xl border border-gray-200 p-4 flex items-center gap-4">
                <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${
                  doc.uploaded ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-400'
                }`}>
                  {doc.uploaded ? <CheckCircle size={20} /> : <FileText size={20} />}
                </div>
                <div className="flex-1">
                  <p className="text-sm font-medium text-gray-900">{doc.name}</p>
                  {doc.uploaded && <p className="text-xs text-green-600">{doc.fileName}</p>}
                  {!doc.uploaded && <p className="text-xs text-gray-500">Required</p>}
                </div>
                {!doc.uploaded && (
                  <div className="flex gap-2">
                    <button className="px-3 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-medium hover:bg-blue-700 flex items-center gap-1">
                      <Upload size={12} /> Upload
                    </button>
                    <button className="px-3 py-1.5 bg-gray-100 text-gray-700 rounded-lg text-xs font-medium hover:bg-gray-200 flex items-center gap-1">
                      <Camera size={12} /> Camera
                    </button>
                  </div>
                )}
                {doc.uploaded && (
                  <button className="text-xs text-blue-600 hover:underline">Re-upload</button>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // eSign Page
  if (step === 'esign') {
    return (
      <div>
        <PortalHeader />
        <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
          <div>
            <h1 className="text-xl font-bold text-gray-900">eSign Documents</h1>
            <p className="text-sm text-gray-500">Sign your loan agreement and KFS digitally</p>
          </div>

          <div className="space-y-4">
            {[
              { name: 'Key Fact Statement (KFS)', status: 'SIGNED', desc: 'RBI-mandated disclosure of loan terms' },
              { name: 'Sanction Letter', status: 'PENDING', desc: 'Loan sanction terms and conditions' },
              { name: 'Loan Agreement', status: 'PENDING', desc: 'Complete loan agreement document' },
              { name: 'NACH Mandate', status: 'NOT_READY', desc: 'Auto-debit mandate for EMI collection' },
            ].map((doc) => (
              <div key={doc.name} className="bg-white rounded-xl border border-gray-200 p-5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <PenTool size={20} className={doc.status === 'SIGNED' ? 'text-green-500' : 'text-gray-400'} />
                    <div>
                      <p className="text-sm font-medium text-gray-900">{doc.name}</p>
                      <p className="text-xs text-gray-500">{doc.desc}</p>
                    </div>
                  </div>
                  {doc.status === 'SIGNED' && (
                    <span className="px-3 py-1 bg-green-100 text-green-700 rounded-full text-xs font-medium">Signed</span>
                  )}
                  {doc.status === 'PENDING' && (
                    <button className="px-4 py-2 bg-blue-600 text-white rounded-lg text-xs font-medium hover:bg-blue-700">
                      Sign Now
                    </button>
                  )}
                  {doc.status === 'NOT_READY' && (
                    <span className="px-3 py-1 bg-gray-100 text-gray-500 rounded-full text-xs font-medium">Not Ready</span>
                  )}
                </div>
              </div>
            ))}
          </div>

          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4">
            <h3 className="text-sm font-medium text-blue-800 mb-1">72-Hour Cooling-Off Period</h3>
            <p className="text-xs text-blue-600">
              As per RBI Digital Lending Directions, you have a 72-hour cooling-off period after signing the KFS
              during which you may exit the loan without penalty.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // Status Tracking
  if (step === 'status') {
    const statusTimeline = [
      { step: 'Application Submitted', date: '2026-04-09 08:30 AM', completed: true },
      { step: 'KYC Verification', date: '2026-04-09 10:15 AM', completed: true },
      { step: 'Document Collection', date: '2026-04-10 02:00 PM', completed: true },
      { step: 'Credit Assessment', date: '2026-04-11 09:00 AM', completed: true },
      { step: 'Sanction Approved', date: '2026-04-11 04:30 PM', completed: true },
      { step: 'KFS Signing', date: '2026-04-12 10:00 AM', completed: true },
      { step: 'Agreement eSign', date: 'Pending', completed: false },
      { step: 'NACH Mandate', date: 'Pending', completed: false },
      { step: 'Disbursement', date: 'Pending', completed: false },
    ];

    return (
      <div>
        <PortalHeader />
        <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
          <div>
            <h1 className="text-xl font-bold text-gray-900">Application Status</h1>
            <p className="text-sm text-gray-500">Track your loan application progress</p>
          </div>

          <div className="bg-white rounded-xl border border-gray-200 p-5">
            <div className="flex items-center justify-between mb-4">
              <div>
                <p className="text-xs text-gray-500 font-mono">LOS-IND-20260320-00045</p>
                <h3 className="text-lg font-semibold text-gray-900">Home Loan Application</h3>
              </div>
              <span className="px-3 py-1 bg-yellow-100 text-yellow-700 rounded-full text-xs font-medium">
                ESIGN PENDING
              </span>
            </div>

            <div className="relative pl-8 space-y-0">
              {statusTimeline.map((item, idx) => (
                <div key={item.step} className="relative pb-6">
                  {/* Line */}
                  {idx < statusTimeline.length - 1 && (
                    <div className={`absolute left-[-20px] top-3 w-0.5 h-full ${
                      item.completed ? 'bg-green-400' : 'bg-gray-200'
                    }`} />
                  )}
                  {/* Dot */}
                  <div className={`absolute left-[-24px] top-1 w-2.5 h-2.5 rounded-full border-2 ${
                    item.completed
                      ? 'bg-green-500 border-green-500'
                      : 'bg-white border-gray-300'
                  }`} />
                  <div>
                    <p className={`text-sm font-medium ${item.completed ? 'text-gray-900' : 'text-gray-400'}`}>
                      {item.step}
                    </p>
                    <p className={`text-xs ${item.completed ? 'text-gray-500' : 'text-gray-400'}`}>
                      {item.date}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <button
            onClick={() => setStep('dashboard')}
            className="flex items-center gap-2 text-sm text-blue-600 hover:underline"
          >
            <ArrowLeft size={14} /> Back to Dashboard
          </button>
        </div>
      </div>
    );
  }

  return null;
}
