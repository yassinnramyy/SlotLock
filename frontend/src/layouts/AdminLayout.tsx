import { Outlet } from "react-router"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/auth/AuthContext"

export function AdminLayout() {
  const { logout } = useAuth()

  return (
    <div className="min-h-svh flex flex-col">
      <header className="flex items-center justify-between border-b px-6 py-4">
        <span className="font-semibold">SlotLock Admin</span>
        <Button variant="outline" onClick={logout}>
          Log out
        </Button>
      </header>
      <main className="flex-1 p-6">
        <Outlet />
      </main>
    </div>
  )
}
