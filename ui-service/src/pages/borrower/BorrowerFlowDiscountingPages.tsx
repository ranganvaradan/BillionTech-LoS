import { BorrowerInvoiceDiscountingPage } from './BorrowerInvoiceDiscountingPage'

export function BorrowerSalesBillDiscountingPage() {
  return (
    <BorrowerInvoiceDiscountingPage
      flowType="SALES_BILL_DISCOUNTING"
      title="Sales Bill Discounting"
      description="Create sales bills for anchor approval, then request finance once eligible."
      createPath="/borrower/sales-bill-discounting/create"
      createLabel="Create invoice"
    />
  )
}

export function BorrowerPurchaseOrderDiscountingPage() {
  return (
    <BorrowerInvoiceDiscountingPage
      flowType="PURCHASE_ORDER_DISCOUNTING"
      title="Purchase Order Discounting"
      description="Create purchase orders for anchor approval, then request finance once eligible."
      createPath="/borrower/purchase-order-discounting/create"
      createLabel="Create purchase order"
    />
  )
}
