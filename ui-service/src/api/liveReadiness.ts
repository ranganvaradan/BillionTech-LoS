import { http } from './http'

const BASE = 'admin/live-readiness'

export async function getDataParametersOverview(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/data-parameters`, {
    params: { authorableOnly: true },
  })
  return data
}

export async function getDataParametersBySource(source: string, authorableOnly = true): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/data-parameters/by-source`, {
    params: { source, authorableOnly },
  })
  return data
}

export async function searchDataParameters(q: string, authorableOnly = true): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/data-parameters/search`, {
    params: { q, authorableOnly },
  })
  return data
}

export async function getDataParametersDetail(parameterId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/data-parameters/${encodeURIComponent(parameterId)}`,
  )
  return data
}

export async function getWorkflowProvides(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/workflow-provides`)
  return data
}

export async function getProductConfigurationOptions(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/product-configuration/options`)
  return data
}

export async function composeProductConfiguration(
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/product-configuration/compose`,
    body,
  )
  return data
}

export async function getGoldenProductConfiguration(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/product-configuration/golden`)
  return data
}

// external_product_mapping admin (Product Configuration surface)
export async function listExternalProductMappings(params: {
  losProductCode: string
  externalSystem?: string
  bookType?: string
  asOf?: string
}): Promise<any[]> {
  const { losProductCode, externalSystem, bookType, asOf } = params
  const { data: res } = await http.get(`${BASE}/external-product-mappings`, {
    params: {
      losProductCode,
      externalSystem: externalSystem || undefined,
      bookType: bookType || undefined,
      asOf: asOf || undefined,
    },
  })
  return res
}

export async function listExternalProductSystems(losProductCode: string): Promise<string[]> {
  const { data } = await http.get<string[]>(`${BASE}/external-product-mappings/systems`, {
    params: { losProductCode },
  })
  return data
}

export async function createExternalProductMapping(req: {
  losProductCode: string
  externalSystem: string
  bookType: string
  externalProductCode: string
  version: number
  status?: string
  effectiveFrom: string
  effectiveTo: string
  metadataJson?: Record<string, unknown>
}): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`${BASE}/external-product-mappings`, req)
  return data
}

export async function patchExternalProductMappingStatus(
  mappingId: string,
  status: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.patch<Record<string, unknown>>(`${BASE}/external-product-mappings/${encodeURIComponent(mappingId)}/status`, {
    status,
  })
  return data
}
