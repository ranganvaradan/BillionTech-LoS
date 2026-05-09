import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { installBorrowerDraftDemoClearListener } from '@/lib/borrowerWizardDraft'
import { RequireStaff } from '@/auth/RequireStaff'
import { BorrowerPortalLayout } from '@/layouts/BorrowerPortalLayout'
import { MainLayout } from '@/layouts/MainLayout'
import { RequireBorrower } from '@/auth/RequireBorrower'
import { BorrowerApplicationDetailPage } from '@/pages/borrower/BorrowerApplicationDetailPage'
import { BorrowerApplyPage } from '@/pages/borrower/BorrowerApplyPage'
import { BorrowerDashboardPage } from '@/pages/borrower/BorrowerDashboardPage'
import { BorrowerApplicationsListPage } from '@/pages/borrower/BorrowerApplicationsListPage'
import { BorrowerLoginPage } from '@/pages/borrower/BorrowerLoginPage'
import { BorrowerRegisterPage } from '@/pages/borrower/BorrowerRegisterPage'
import { BorrowerForgotPasswordPage } from '@/pages/borrower/BorrowerForgotPasswordPage'
import { BorrowerProfilePage } from '@/pages/borrower/BorrowerProfilePage'
import { BorrowerSignedDocumentsPage } from '@/pages/borrower/BorrowerSignedDocumentsPage'
import { BorrowerLoanSubPage } from '@/pages/borrower/BorrowerLoanSubPage'
import { BorrowerStatusRedirectPage } from '@/pages/borrower/BorrowerStatusRedirectPage'
import { SalesNewApplicationPage } from '@/pages/sales/SalesNewApplicationPage'
import { ApplicationDetailPage } from '@/pages/ApplicationDetailPage'
import { ApplicationsPage } from '@/pages/ApplicationsPage'
import { NewApplicationPage } from '@/pages/NewApplicationPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { KycQueuePage } from '@/pages/KycQueuePage'
import { UnderwritingQueuePage } from '@/pages/UnderwritingQueuePage'
import { WorkflowsPage } from '@/pages/WorkflowsPage'
import { UnderwritingRulesPage } from '@/pages/UnderwritingRulesPage'
import { ScorecardsPage } from '@/pages/ScorecardsPage'
import { AssignmentRulesPage } from '@/pages/AssignmentRulesPage'
import { UserRoleMappingsPage } from '@/pages/UserRoleMappingsPage'
import { UsersPage } from '@/pages/UsersPage'
import { IntegrationProviderMatrixPage } from '@/pages/IntegrationProviderMatrixPage'
import { AdminConfigGate } from '@/auth/AdminConfigGate'
import { LoginPage } from '@/pages/auth/LoginPage'
import { ForgotPasswordPage } from '@/pages/auth/ForgotPasswordPage'
import { ResetPasswordPage } from '@/pages/auth/ResetPasswordPage'

export default function App() {
  useEffect(() => installBorrowerDraftDemoClearListener(), [])

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/borrower/login" element={<BorrowerLoginPage />} />
      <Route path="/borrower/register" element={<BorrowerRegisterPage />} />
      <Route path="/borrower/forgot-password" element={<BorrowerForgotPasswordPage />} />
      <Route path="/borrower/status/:applicationId" element={<BorrowerStatusRedirectPage />} />
      <Route
        path="/borrower"
        element={
          <RequireBorrower>
            <BorrowerPortalLayout />
          </RequireBorrower>
        }
      >
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<BorrowerDashboardPage />} />
        <Route path="apply" element={<BorrowerApplyPage />} />
        <Route path="applications" element={<BorrowerApplicationsListPage />} />
        <Route path="applications/:id" element={<BorrowerApplicationDetailPage />} />
        <Route path="documents" element={<BorrowerSignedDocumentsPage />} />
        <Route path="profile" element={<BorrowerProfilePage />} />
        <Route path="loans/:loanId/repayment" element={<BorrowerLoanSubPage mode="repayment" />} />
        <Route path="loans/:loanId/statement" element={<BorrowerLoanSubPage mode="statement" />} />
        <Route path="loans/:loanId/transactions" element={<BorrowerLoanSubPage mode="transactions" />} />
      </Route>
      <Route
        path="/"
        element={
          <RequireStaff>
            <MainLayout />
          </RequireStaff>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path="applications/new" element={<NewApplicationPage />} />
        <Route path="sales/applications/new" element={<SalesNewApplicationPage />} />
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
        <Route path="kyc" element={<KycQueuePage />} />
        <Route path="underwriting" element={<UnderwritingQueuePage />} />
        <Route
          path="workflows"
          element={
            <AdminConfigGate>
              <WorkflowsPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="integrations/provider-matrix"
          element={
            <AdminConfigGate>
              <IntegrationProviderMatrixPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="underwriting-rules"
          element={
            <AdminConfigGate>
              <UnderwritingRulesPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="underwriting-scorecards"
          element={
            <AdminConfigGate>
              <ScorecardsPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="assignment-rules"
          element={
            <AdminConfigGate>
              <AssignmentRulesPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="users"
          element={
            <AdminConfigGate>
              <UsersPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="user-role-mappings"
          element={
            <AdminConfigGate>
              <UserRoleMappingsPage />
            </AdminConfigGate>
          }
        />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  )
}
