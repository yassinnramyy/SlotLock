import { Navigate, Outlet } from "react-router"
import { useAuth } from "@/auth/AuthContext"
import { getHomePathForRole } from "@/auth/roleHome"
import type { UserRole } from "@/types/auth"

interface ProtectedRouteProps {
  allowedRoles?: UserRole[]
}

export function ProtectedRoute({ allowedRoles }: ProtectedRouteProps) {
  const { user } = useAuth()

  if (!user) {
    return <Navigate to="/login" replace />
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={getHomePathForRole(user.role)} replace />
  }

  return <Outlet />
}
