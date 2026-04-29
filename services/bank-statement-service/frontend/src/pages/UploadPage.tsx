import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import FileUpload from '../components/FileUpload';
import { bankStatementApi } from '../lib/api';

export default function UploadPage() {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [password, setPassword] = useState('');
  const [applicationId, setApplicationId] = useState('');
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleUpload = async () => {
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const res = await bankStatementApi.upload(file, password || undefined, applicationId || undefined);
      navigate(`/analysis/${res.data.statementId}`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Upload failed';
      setError(msg);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto">
      <div className="text-center mb-8">
        <h2 className="text-2xl font-bold text-gray-900">Upload Bank Statement</h2>
        <p className="mt-2 text-sm text-gray-500">
          Upload a bank statement in PDF, Excel, or CSV format for automatic extraction and analysis
        </p>
      </div>

      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-6 space-y-6">
        <FileUpload onFileSelect={setFile} />

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              PDF Password (optional)
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="If password-protected"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Application ID (optional)
            </label>
            <input
              type="text"
              value={applicationId}
              onChange={(e) => setApplicationId(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="Link to loan application"
            />
          </div>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
            {error}
          </div>
        )}

        <button
          onClick={handleUpload}
          disabled={!file || uploading}
          className="w-full py-3 px-4 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {uploading ? 'Uploading & Analyzing...' : 'Upload & Analyze'}
        </button>
      </div>

      <div className="mt-8 bg-white rounded-2xl shadow-sm border border-gray-200 p-6">
        <h3 className="text-sm font-semibold text-gray-700 mb-3">Supported Banks (25)</h3>
        <div className="flex flex-wrap gap-2">
          {[
            'SBI', 'HDFC', 'ICICI', 'Axis', 'PNB', 'Kotak', 'Yes Bank',
            'IndusInd', 'BoB', 'Canara', 'Union', 'IDBI', 'Federal', 'RBL',
            'Bandhan', 'IDFC First', 'AU SFB', 'Indian Bank', 'BoI', 'CBI',
            'UCO', 'P&SB', 'IOB', 'Karnataka', 'South Indian',
          ].map((bank) => (
            <span key={bank} className="px-2.5 py-1 bg-gray-100 text-gray-700 text-xs rounded-full">
              {bank}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}
