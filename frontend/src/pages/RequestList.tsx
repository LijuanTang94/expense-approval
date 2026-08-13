import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, ApiError } from "../api";
import { useAuth } from "../auth";
import { StatusBadge, scopeLabel, STATUSES } from "../ui";
import type { Page, RequestStatus, RequestSummary } from "../types";

// The status an approver can act on: a manager approves SUBMITTED requests in their department;
// finance gives final approval to MANAGER_APPROVED ones. Employees have no approval queue.
const APPROVER_STATUS: Record<string, RequestStatus | null> = {
  MANAGER: "SUBMITTED",
  FINANCE: "MANAGER_APPROVED",
  EMPLOYEE: null,
};

export function RequestList() {
  const { user } = useAuth();
  const role = user!.role;
  const approverStatus = APPROVER_STATUS[role] ?? null;

  const [status, setStatus] = useState<RequestStatus | "">("");
  const [data, setData] = useState<Page<RequestSummary> | null>(null);
  const [inbox, setInbox] = useState<RequestSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [actingId, setActingId] = useState<number | null>(null);

  const loadInbox = useCallback(() => {
    if (!approverStatus) {
      setInbox([]);
      return;
    }
    api<Page<RequestSummary>>(`/api/requests?status=${approverStatus}`)
      .then((p) => setInbox(p.content))
      .catch(() => setInbox([]));
  }, [approverStatus]);

  const loadList = useCallback(() => {
    setLoading(true);
    setError(null);
    const q = status ? `?status=${status}` : "";
    api<Page<RequestSummary>>(`/api/requests${q}`)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [status]);

  useEffect(() => {
    loadInbox();
  }, [loadInbox]);
  useEffect(() => {
    loadList();
  }, [loadList]);

  const decide = async (id: number, action: "approve" | "reject") => {
    const comment = action === "reject" ? window.prompt("Reason for rejection (optional):") ?? "" : "";
    setActingId(id);
    setError(null);
    try {
      await api(`/api/requests/${id}/${action}`, { method: "POST", body: { comment } });
      loadInbox();
      loadList();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Action failed");
    } finally {
      setActingId(null);
    }
  };

  const scope = scopeLabel(role);

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

      {/* Approval inbox — only managers and finance have one; this is what makes their view a
          genuine "approval page" rather than the same list an employee sees. */}
      {approverStatus && (
        <div
          className="card card-pad stack"
          style={{ marginBottom: "1.25rem", borderColor: "var(--accent)" }}
        >
          <div className="row">
            <strong>
              Awaiting your approval{inbox.length > 0 ? ` (${inbox.length})` : ""}
            </strong>
            <span className="hint">
              {role === "MANAGER"
                ? "Submitted requests from your department"
                : "Requests your managers have approved"}
            </span>
          </div>
          {error && <div className="error">{error}</div>}
          {inbox.length === 0 ? (
            <p className="muted">Nothing awaiting your approval right now.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Title</th>
                  <th>Requester</th>
                  <th className="right">Amount</th>
                  <th className="right">Decision</th>
                </tr>
              </thead>
              <tbody>
                {inbox.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <Link to={`/requests/${r.id}`}>{r.id}</Link>
                    </td>
                    <td>
                      <Link to={`/requests/${r.id}`}>{r.title}</Link>
                    </td>
                    <td>{r.requesterName}</td>
                    <td className="right">
                      {r.currency} {r.totalAmount.toFixed(2)}
                    </td>
                    <td className="right" style={{ whiteSpace: "nowrap" }}>
                      <button disabled={actingId === r.id} onClick={() => decide(r.id, "approve")}>
                        Approve
                      </button>{" "}
                      <button
                        className="danger"
                        disabled={actingId === r.id}
                        onClick={() => decide(r.id, "reject")}
                      >
                        Reject
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

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

      {error && !inbox.length ? (
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
                {role !== "EMPLOYEE" && <th>Requester</th>}
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
                  {role !== "EMPLOYEE" && <td>{r.requesterName}</td>}
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
