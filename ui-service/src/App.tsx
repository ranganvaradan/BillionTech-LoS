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
import { BorrowerChangePasswordPage } from '@/pages/borrower/BorrowerChangePasswordPage'
import { BorrowerForgotPasswordPage } from '@/pages/borrower/BorrowerForgotPasswordPage'
import { BorrowerProfilePage } from '@/pages/borrower/BorrowerProfilePage'
import { BorrowerSignedDocumentsPage } from '@/pages/borrower/BorrowerSignedDocumentsPage'
import { BorrowerLoanSubPage } from '@/pages/borrower/BorrowerLoanSubPage'
import { BorrowerLoanAccountPage } from '@/pages/borrower/BorrowerLoanAccountPage'
import { BorrowerInvoiceDiscountingPage } from '@/pages/borrower/BorrowerInvoiceDiscountingPage'
import {
  BorrowerPurchaseOrderDiscountingPage,
  BorrowerSalesBillDiscountingPage,
} from '@/pages/borrower/BorrowerFlowDiscountingPages'
import { BorrowerSellerInitiatedInvoiceCreatePage } from '@/pages/borrower/BorrowerSellerInitiatedInvoiceCreatePage'
import { BorrowerPaymentCartPage } from '@/pages/borrower/payments/BorrowerPaymentCartPage'
import { BorrowerPayuCheckoutPage } from '@/pages/borrower/payments/BorrowerPayuCheckoutPage'
import { BorrowerLoanPayuCheckoutPage } from '@/pages/borrower/payments/BorrowerLoanPayuCheckoutPage'
import { BorrowerLoanPaymentResultPage } from '@/pages/borrower/payments/BorrowerLoanPaymentResultPage'
import { BorrowerPaymentResultPage } from '@/pages/borrower/payments/BorrowerPaymentResultPage'
import { BorrowerCreditNotesPage } from '@/pages/borrower/BorrowerCreditNotesPage'
import { BorrowerCreditNoteViewPage } from '@/pages/borrower/BorrowerCreditNoteViewPage'
import { BorrowerProgramsPage } from '@/pages/borrower/BorrowerProgramsPage'
import { BorrowerStatusRedirectPage } from '@/pages/borrower/BorrowerStatusRedirectPage'
import { SalesNewApplicationPage } from '@/pages/sales/SalesNewApplicationPage'
import { ApplicationDetailPage } from '@/pages/ApplicationDetailPage'
import { ApplicationsPage } from '@/pages/ApplicationsPage'
import { NewApplicationPage } from '@/pages/NewApplicationPage'
import { ApplicationIntakeEditPage } from '@/pages/ApplicationIntakeEditPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { KycQueuePage } from '@/pages/KycQueuePage'
import { BorrowerSubmittedQueuePage } from '@/pages/BorrowerSubmittedQueuePage'
import { UnderwritingQueuePage } from '@/pages/UnderwritingQueuePage'
import { WorkflowsPage } from '@/pages/WorkflowsPage'
import { UnderwritingRulesPage } from '@/pages/UnderwritingRulesPage'
import { RepaymentDefaultsPage } from '@/pages/RepaymentDefaultsPage'
import { PgSettlementsPage } from '@/pages/PgSettlementsPage'
import { AnchorRatingTemplatesPage } from '@/pages/AnchorRatingTemplatesPage'
import { ScorecardsPage } from '@/pages/ScorecardsPage'
import { CustomerCategoriesPage } from '@/pages/CustomerCategoriesPage'
import { PolicySetsPage } from '@/pages/PolicySetsPage'
import { AssignmentRulesPage } from '@/pages/AssignmentRulesPage'
import { UserRoleMappingsPage } from '@/pages/UserRoleMappingsPage'
import { UsersPage } from '@/pages/UsersPage'
import { IntegrationProviderMatrixPage } from '@/pages/IntegrationProviderMatrixPage'
import { AdminConfigGate } from '@/auth/AdminConfigGate'
import { LoginPage } from '@/pages/auth/LoginPage'
import { StaffChangePasswordPage } from '@/pages/auth/StaffChangePasswordPage'
import { ForgotPasswordPage } from '@/pages/auth/ForgotPasswordPage'
import { ResetPasswordPage } from '@/pages/auth/ResetPasswordPage'
import { PlpProgramsPage } from '@/pages/plp/PlpProgramsPage'
import { PlpProgramDetailPage } from '@/pages/plp/PlpProgramDetailPage'
import { ProgramApprovalsPage } from '@/pages/ProgramApprovalsPage'
import { ApplicationDeletionsPage } from '@/pages/ApplicationDeletionsPage'
import { AuditTrailPage } from '@/pages/AuditTrailPage'
import { ReportsPage } from '@/pages/ReportsPage'
import { AdministrationPage } from '@/pages/AdministrationPage'
import { DataParametersPage } from '@/pages/DataParametersPage'
import { ProductConfigurationPage } from '@/pages/ProductConfigurationPage'
import { CiApplicationsPage } from '@/pages/creditIntelligence/CiApplicationsPage'
import { CiWorkspacePage } from '@/pages/creditIntelligence/CiWorkspacePage'
import { CiPolicyStudioPage } from '@/pages/creditIntelligence/CiPolicyStudioPage'
import { CiPolicyCataloguePage } from '@/pages/creditIntelligence/CiPolicyCataloguePage'
import { CiP2ValidationDashboardPage } from '@/pages/creditIntelligence/CiP2ValidationDashboardPage'
import { CiDualRunPage } from '@/pages/creditIntelligence/CiDualRunPage'
import { CiValidationPage } from '@/pages/creditIntelligence/CiValidationPage'

export default function App() {
  useEffect(() => installBorrowerDraftDemoClearListener(), [])

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/change-password" element={<StaffChangePasswordPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/borrower/login" element={<BorrowerLoginPage />} />
      <Route path="/borrower/change-password" element={<BorrowerChangePasswordPage />} />
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
        <Route path="programs" element={<BorrowerProgramsPage />} />
        <Route
          path="invoice-discounting"
          element={
            <BorrowerInvoiceDiscountingPage flowType="PURCHASE_BILL_DISCOUNTING" title="Invoice discounting" />
          }
        />
        <Route path="sales-bill-discounting" element={<BorrowerSalesBillDiscountingPage />} />
        <Route
          path="sales-bill-discounting/create"
          element={
            <BorrowerSellerInitiatedInvoiceCreatePage
              flowType="SALES_BILL_DISCOUNTING"
              title="Create sales bill"
              backPath="/borrower/sales-bill-discounting"
            />
          }
        />
        <Route path="purchase-order-discounting" element={<BorrowerPurchaseOrderDiscountingPage />} />
        <Route
          path="purchase-order-discounting/create"
          element={
            <BorrowerSellerInitiatedInvoiceCreatePage
              flowType="PURCHASE_ORDER_DISCOUNTING"
              title="Create purchase order"
              backPath="/borrower/purchase-order-discounting"
            />
          }
        />
        <Route path="invoice-discounting/payments/cart" element={<BorrowerPaymentCartPage />} />
        <Route path="invoice-discounting/payments/payu" element={<BorrowerPayuCheckoutPage />} />
        <Route path="invoice-discounting/payments/result" element={<BorrowerPaymentResultPage />} />
        <Route path="credit-notes" element={<BorrowerCreditNotesPage />} />
        <Route path="credit-notes/:id" element={<BorrowerCreditNoteViewPage />} />
        <Route path="documents" element={<BorrowerSignedDocumentsPage />} />
        <Route path="profile" element={<BorrowerProfilePage />} />
        <Route path="loans/:loanId/account" element={<BorrowerLoanAccountPage />} />
        <Route path="loans/:loanId/payments/payu" element={<BorrowerLoanPayuCheckoutPage />} />
        <Route path="loans/:loanId/payments/result" element={<BorrowerLoanPaymentResultPage />} />
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
        <Route path="borrower-submissions" element={<BorrowerSubmittedQueuePage />} />
        <Route path="applications/new" element={<NewApplicationPage />} />
        <Route path="applications/:id/intake" element={<ApplicationIntakeEditPage />} />
        <Route path="applications/new-anchor" element={<Navigate to="/applications/new" replace />} />
        <Route path="sales/applications/new" element={<SalesNewApplicationPage />} />
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
        <Route path="plp/programs" element={<PlpProgramsPage />} />
        <Route path="plp/programs/:id" element={<PlpProgramDetailPage />} />
        <Route path="plp/program-approvals" element={<ProgramApprovalsPage />} />
        <Route path="kyc" element={<KycQueuePage />} />
        <Route path="underwriting" element={<UnderwritingQueuePage />} />
        <Route path="credit-intelligence/applications" element={<CiApplicationsPage />} />
        <Route path="credit-intelligence/workspace/:caseCode" element={<CiWorkspacePage />} />
        <Route path="credit-intelligence/policy-studio" element={<CiPolicyStudioPage />} />
        <Route path="credit-intelligence/policy-catalogue" element={<CiPolicyCataloguePage />} />
        <Route
          path="credit-intelligence/p2-validation"
          element={
            <AdminConfigGate>
              <CiP2ValidationDashboardPage />
            </AdminConfigGate>
          }
        />
        <Route path="credit-intelligence/dual-run" element={<CiDualRunPage />} />
        <Route
          path="credit-intelligence/validation"
          element={
            <AdminConfigGate>
              <CiValidationPage />
            </AdminConfigGate>
          }
        />
        <Route path="reports" element={<ReportsPage />} />
        <Route
          path="administration"
          element={
            <AdminConfigGate>
              <AdministrationPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="data-parameters"
          element={
            <AdminConfigGate>
              <DataParametersPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="product-configuration"
          element={
            <AdminConfigGate>
              <ProductConfigurationPage />
            </AdminConfigGate>
          }
        />
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
          path="repayment-config"
          element={
            <AdminConfigGate>
              <RepaymentDefaultsPage />
            </AdminConfigGate>
          }
        />
        <Route path="pg-settlements" element={<PgSettlementsPage />} />
        <Route
          path="anchor-rating-templates"
          element={
            <AdminConfigGate>
              <AnchorRatingTemplatesPage />
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
          path="customer-categories"
          element={
            <AdminConfigGate>
              <CustomerCategoriesPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="policy-sets"
          element={
            <AdminConfigGate>
              <PolicySetsPage />
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
        <Route
          path="application-deletions"
          element={
            <AdminConfigGate>
              <ApplicationDeletionsPage />
            </AdminConfigGate>
          }
        />
        <Route
          path="audit"
          element={
            <AdminConfigGate>
              <AuditTrailPage />
            </AdminConfigGate>
          }
        />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  )
}
