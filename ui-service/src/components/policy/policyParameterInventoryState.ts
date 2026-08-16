export type InvRow = {
  canonicalParameterId?: string | null
  originalToken?: string
  resolutionStatus?: string
  usageType?: string
  overallReadiness?: string
  productionReady?: boolean
  policyTestReady?: boolean
  runtimeReady?: boolean
  calculationRequired?: boolean
  calculationDefined?: boolean
  calculationDefinitionStatus?: string
  sourceFamily?: string
  businessName?: string
  primaryStatus?: string | null
  primaryStatusLabel?: string | null
  calculationExplanation?: string | null
  nextAction?: string | null
  parameterStateAuthority?: string | null
}

export type InventoryLoadState =
  | 'LOADING'
  | 'TECHNICAL_ERROR'
  | 'NO_POLICY_GRAPH'
  | 'NO_PARAMETERS'
  | 'LOADED_WITH_UNRESOLVED'
  | 'LOADED_WITH_PARAMETERS'

export function classifyInventoryLoad(args: {
  busy: boolean
  error: string | null
  graphPresent: boolean | null
  rows: InvRow[]
  unresolvedCount: number
}): InventoryLoadState {
  if (args.busy) return 'LOADING'
  if (args.error) return 'TECHNICAL_ERROR'
  if (args.graphPresent === false) return 'NO_POLICY_GRAPH'
  if (args.rows.length === 0) return 'NO_PARAMETERS'
  if (args.unresolvedCount > 0) return 'LOADED_WITH_UNRESOLVED'
  return 'LOADED_WITH_PARAMETERS'
}

export function unresolvedTokens(rows: InvRow[]): string[] {
  return rows
    .filter(
      (r) =>
        !r.canonicalParameterId ||
        String(r.resolutionStatus || '').toUpperCase().includes('UNRESOLVED'),
    )
    .map((r) => r.originalToken || r.canonicalParameterId || '(unknown)')
    .filter(Boolean) as string[]
}
