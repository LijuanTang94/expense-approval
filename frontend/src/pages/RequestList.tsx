import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { StatusBadge, scopeLabel, STATUSES } from "../ui";
import type { Page, RequestStatus, RequestSummary } from "../types";

export function RequestList() {
  const { user } = useAuth();
  const [status, setStatus] = useState<RequestStatus | "">("");
  const [data, setData] = useState<Page<RequestSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setError(null);
    const q = status ? `?status=${status}` : "";
    api<Page<RequestSummary>>(`/api/requests${q}`)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [status]);

  const scope = scopeLabel(user!.role);

  return (
    <>
      <div className="row">
        <div>
          <h1>{scope.title}</h1>
          <p className="subtitle">{scope.subtitle}</p>
        </div>
        <Link to="/requests/new">
          <button>New request</button>
        </Link>
      </div>

      <div style={{ marginBottom: "1rem", maxWidth: 260 }}>
        <label>Filter by status</label>
        <select value={status} onChange={(e) => setStatus(e.target.value as RequestStatus | "")}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s === "" ? "All statuses" : s.replace(/_/g, " ").toLowerCase()}
            </option>
          ))}
        </select>
      </div>

      {error ? (
        <div className="error">{error}</div>
      ) : loading ? (
        <p className="muted">Loading…</p>
      ) : !data || data.content.length === 0 ? (
        <div className="card card-pad muted">No requests found.</div>
      ) : (
        <div className="card">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Title</th>
                {user!.role !== "EMPLOYEE" && <th>Requester</th>}
                <th>Department</th>
                <th className="right">Amount</th>
                <th>Status</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((r) => (
                <tr key={r.id}>
                  <td>
                    <Link to={`/requests/${r.id}`}>{r.id}</Link>
                  </td>
                  <td>
                    <Link to={`/requests/${r.id}`}>{r.title}</Link>
                  </td>
                  {user!.role !== "EMPLOYEE" && <td>{r.requesterName}</td>}
                  <td>{r.departmentName}</td>
                  <td className="right">
                    {r.currency} {r.totalAmount.toFixed(2)}
                  </td>
                  <td>
                    <StatusBadge status={r.status} />
                  </td>
                  <td className="muted">{new Date(r.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
