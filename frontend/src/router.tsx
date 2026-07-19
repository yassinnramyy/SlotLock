import { createBrowserRouter, Navigate } from "react-router"
import { ProtectedRoute } from "@/auth/ProtectedRoute"
import { CustomerLayout } from "@/layouts/CustomerLayout"
import { AdminLayout } from "@/layouts/AdminLayout"
import { LoginPage } from "@/features/auth/LoginPage"
import { RegisterPage } from "@/features/auth/RegisterPage"

function CustomerHome() {
  return <p>Customer dashboard — coming Day 2</p>
}

function AdminHome() {
  return <p>Admin dashboard — coming Day 4</p>
}

export const router = createBrowserRouter([
  { path: "/", element: <Navigate to="/login" replace /> },
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  {
    path: "/app",
    element: <ProtectedRoute allowedRoles={["CUSTOMER"]} />,
    children: [
      {
        element: <CustomerLayout />,
        children: [{ index: true, element: <CustomerHome /> }],
      },
    ],
  },
  {
    path: "/admin",
    element: <ProtectedRoute allowedRoles={["SUPER_ADMIN", "ADMIN", "STAFF"]} />,
    children: [
      {
        element: <AdminLayout />,
        children: [{ index: true, element: <AdminHome /> }],
      },
    ],
  },
])
