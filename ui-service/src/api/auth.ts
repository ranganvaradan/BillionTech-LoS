import { http } from './http'
import type { SessionUser } from '@/auth/types'

export interface LoginResponseBody {
  userId: string
  name: string
  email: string
  role: string
  institution: string
}

export async function postLogin(email: string, password: string): Promise<SessionUser> {
  const { data } = await http.post<LoginResponseBody>('/auth/login', { email, password })
  return {
    userId: data.userId,
    name: data.name,
    email: data.email,
    role: data.role,
    institution: data.institution,
  }
}

export interface RegisterRequestBody {
  name: string
  email: string
  mobile: string
  password: string
  confirmPassword: string
}

export async function postRegister(body: RegisterRequestBody): Promise<SessionUser> {
  const { data } = await http.post<LoginResponseBody>('/auth/register', body)
  return {
    userId: data.userId,
    name: data.name,
    email: data.email,
    role: data.role,
    institution: data.institution,
  }
}

export interface ForgotPasswordResponseBody {
  message: string
  resetToken?: string
  resetPath?: string
  resetLink?: string
  expiresAt?: string
}

export async function postForgotPassword(email: string): Promise<ForgotPasswordResponseBody> {
  const { data } = await http.post<ForgotPasswordResponseBody>('/auth/forgot-password', { email })
  return data
}

export async function postResetPassword(token: string, newPassword: string): Promise<void> {
  await http.post('/auth/reset-password', { token, newPassword })
}
