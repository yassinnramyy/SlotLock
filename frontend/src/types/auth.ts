export type UserRole = "SUPER_ADMIN" | "ADMIN" | "STAFF" | "CUSTOMER"

export interface AuthUser {
  userId: number
  tenantId: number | null
  role: UserRole
}

export interface AuthResponse {
  userId: number
  tenantId: number | null
  role: UserRole
  token: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
}

export interface ApiError {
  status: number
  errorCode: string
  message: string
  timestamp: string
}
