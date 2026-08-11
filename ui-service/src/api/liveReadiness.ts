import { http } from './http'

const BASE = 'admin/live-readiness'

export async function getDataParametersOverview(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/data-parameters`)
  return data
}

export async function getDataParametersBySource(source: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/data-parameters/by-source`, {
    params: { source },
  })
  return data
}

export async function searchDataParameters(q: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/data-parameters/search`, {
    params: { q },
  })
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
