import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import './index.css';
import App from './App';
import UploadPage from './pages/UploadPage';
import DashboardPage from './pages/DashboardPage';
import AnalysisPage from './pages/AnalysisPage';
import TransactionsPage from './pages/TransactionsPage';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />}>
          <Route index element={<UploadPage />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="analysis/:id" element={<AnalysisPage />} />
          <Route path="transactions/:id" element={<TransactionsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
);
