import { http } from './http'
import type { SessionUser } from '@/auth/types'

export interface LoginResponseBody {
  userId: string
  name: string
  email: string
  role: string
  institution: string
  passwordResetRequired?: boolean
  accessToken?: string
  tokenType?: string
}

function toSessionUser(data: LoginResponseBody): SessionUser {
  return {
    userId: data.userId,
    name: data.name,
    email: data.email,
    role: data.role,
    institution: data.institution,
    passwordResetRequired: Boolean(data.passwordResetRequired),
    accessToken: data.accessToken,
    tokenType: data.tokenType,
  }
}

export async function postLogin(email: string, password: string): Promise<SessionUser> {
  const { data } = await http.post<LoginResponseBody>('/auth/login', { email, password })
  return toSessionUser(data)
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
  return toSessionUser(data)
}

export interface ChangePasswordRequestBody {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

/** Authenticated password change (used by the forced first-login reset). Uses the session X-User-Id header. */
export async function postChangePassword(body: ChangePasswordRequestBody): Promise<SessionUser> {
  const { data } = await http.post<LoginResponseBody>('/auth/change-password', body)
  return toSessionUser(data)
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
