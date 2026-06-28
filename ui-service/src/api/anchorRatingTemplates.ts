import { http } from './http'

export type AnchorRatingOption = {
  value: string
  label: string
  score: number
}

export type AnchorRatingQuestion = {
  key: string
  label: string
  hint?: string
  options: AnchorRatingOption[]
}

export type AnchorRatingBand = {
  rating: string
  minScore: number
  maxScore: number
  label: string
}

export type AnchorRatingTemplateConfig = {
  questions: AnchorRatingQuestion[]
  ratingBands: AnchorRatingBand[]
}

export interface AnchorRatingTemplateResponse {
  id: string
  name: string
  version: number
  active: boolean
  configJson: AnchorRatingTemplateConfig
  createdAt: string | null
  updatedAt: string | null
}

export interface AnchorRatingTemplateRequest {
  name: string
  version: number
  active: boolean
  configJson: AnchorRatingTemplateConfig
}

export async function listAnchorRatingTemplates(): Promise<AnchorRatingTemplateResponse[]> {
  const { data } = await http.get<AnchorRatingTemplateResponse[]>('/underwriting/anchor-rating-templates')
  return data
}

export async function createAnchorRatingTemplate(
  payload: AnchorRatingTemplateRequest,
): Promise<AnchorRatingTemplateResponse> {
  const { data } = await http.post<AnchorRatingTemplateResponse>('/underwriting/anchor-rating-templates', payload)
  return data
}

export async function updateAnchorRatingTemplate(
  id: string,
  payload: AnchorRatingTemplateRequest,
): Promise<AnchorRatingTemplateResponse> {
  const { data } = await http.put<AnchorRatingTemplateResponse>(`/underwriting/anchor-rating-templates/${id}`, payload)
  return data
}

export async function activateAnchorRatingTemplate(id: string): Promise<AnchorRatingTemplateResponse> {
  const { data } = await http.post<AnchorRatingTemplateResponse>(`/underwriting/anchor-rating-templates/${id}/activate`)
  return data
}

export async function deleteAnchorRatingTemplate(id: string): Promise<void> {
  await http.delete(`/underwriting/anchor-rating-templates/${id}`)
}
