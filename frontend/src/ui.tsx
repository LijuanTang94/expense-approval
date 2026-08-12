import { Link, Navigate, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuth } from "./auth";
import type { RequestStatus } from "./types";

export function StatusBadge({ status }: { status: string }) {
  return <span className={`badge ${status}`}>{status.replace(/_/g, " ").toLowerCase()}</span>;
}

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  return (
    <>
      <nav className="nav">
        <div className="nav-inner">
          <Link to="/" className="brand" style={{ color: "var(--ink)" }}>
            Expense Approvals
          </Link>
          <div className="nav-right">
            {user && (
              <>
                <span>
                  {user.fullName} · <strong>{user.role}</strong>
                  {user.departmentName ? ` · ${user.departmentName}` : ""}
                </span>
                <button className="ghost" onClick={logout} style={{ padding: "0.35rem 0.7rem" }}>
                  Log out
                </button>
              </>
            )}
          </div>
        </div>
      </nav>
      <div className="container">{children}</div>
    </>
  );
}

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="container muted">Loading…</div>;
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  return <>{children}</>;
}

/** What the list shows for each role. */
export function scopeLabel(role: string): { title: string; subtitle: string } {
  switch (role) {
    case "EMPLOYEE":
      return { title: "My requests", subtitle: "Expense requests you have created." };
    case "MANAGER":
      return {
        title: "Department requests",
        subtitle: "Everything in your department — approve or reject submitted requests.",
      };
    case "FINANCE":
      return {
        title: "All requests",
        subtitle: "Organisation-wide — give final approval to manager-approved requests.",
      };
    default:
      return { title: "Requests", subtitle: "" };
  }
}

export const STATUSES: (RequestStatus | "")[] = [
  "",
  "DRAFT",
  "SUBMITTED",
  "MANAGER_APPROVED",
  "FINANCE_APPROVED",
  "REJECTED",
];
