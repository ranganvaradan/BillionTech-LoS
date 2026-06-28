import { http } from '@/api/http'

export interface GeoStateRow {
  id: string
  stateCode: string
  stateName: string
}

export interface GeoCityRow {
  id: string
  cityName: string
  cityCode: string | null
}

export async function fetchGeoStates(): Promise<GeoStateRow[]> {
  const { data } = await http.get<GeoStateRow[]>('/master/states')
  return data
}

export async function fetchGeoCitiesForState(stateId: string): Promise<GeoCityRow[]> {
  const { data } = await http.get<GeoCityRow[]>(`/master/states/${encodeURIComponent(stateId)}/cities`)
  return data
}
