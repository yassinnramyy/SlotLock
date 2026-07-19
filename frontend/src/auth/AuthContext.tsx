import { createContext, useContext, useState, type ReactNode } from "react"
import { login as loginRequest, register as registerRequest } from "@/api/auth"
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from "@/api/client"
import type { AuthResponse, AuthUser } from "@/types/auth"

interface AuthContextValue {
  user: AuthUser | null
  token: string | null
  login: (email: string, password: string) => Promise<AuthResponse>
  register: (email: string, password: string) => Promise<AuthResponse>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function persistAuth(response: AuthResponse) {
  const user: AuthUser = {
    userId: response.userId,
    tenantId: response.tenantId,
    role: response.role,
  }
  localStorage.setItem(TOKEN_STORAGE_KEY, response.token)
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user))
  return user
}

function readStoredAuth(): { user: AuthUser | null; token: string | null } {
  const storedToken = localStorage.getItem(TOKEN_STORAGE_KEY)
  const storedUser = localStorage.getItem(USER_STORAGE_KEY)
  if (storedToken && storedUser) {
    return { user: JSON.parse(storedUser) as AuthUser, token: storedToken }
  }
  return { user: null, token: null }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => readStoredAuth().user)
  const [token, setToken] = useState<string | null>(() => readStoredAuth().token)

  async function login(email: string, password: string) {
    const response = await loginRequest({ email, password })
    setUser(persistAuth(response))
    setToken(response.token)
    return response
  }

  async function register(email: string, password: string) {
    const response = await registerRequest({ email, password })
    setUser(persistAuth(response))
    setToken(response.token)
    return response
  }

  function logout() {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    localStorage.removeItem(USER_STORAGE_KEY)
    setUser(null)
    setToken(null)
  }

  return (
    <AuthContext.Provider value={{ user, token, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
