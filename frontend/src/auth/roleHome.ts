import type { UserRole } from "@/types/auth"

export function getHomePathForRole(role: UserRole): string {
  return role === "CUSTOMER" ? "/app" : "/admin"
}
