import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, ApiError } from "../api";
import { useAuth } from "../auth";
import { StatusBadge } from "../ui";
import type { RequestDetail as Detail } from "../types";

export function RequestDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const [detail, setDetail] = useState<Detail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    // Clear first: without this, navigating from one request to another shows the previous
    // request's line items (and any stale error) until the new response lands.
    setDetail(null);
    setError(null);
    api<Detail>(`/api/requests/${id}`)
      .then(setDetail)
      .catch((e) => setError(e.message));
  }, [id]);

  useEffect(load, [load]);

  const act = async (action: "submit" | "approve" | "reject") => {
    setError(null);
    setBusy(true);
    try {
      const body = action === "submit" ? undefined : { comment };
      const updated = await api<Detail>(`/api/requests/${id}/${action}`, { method: "POST", body });
      // Don't blindly trust the action to echo the request back — an empty (204) response would
      // otherwise wipe the page into a permanent "Loading…".
      if (updated) setDetail(updated);
      else load();
      setComment("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Action failed");
    } finally {
      setBusy(false);
    }
  };

  if (error && !detail) return <div className="error">{error}</div>;
  if (!detail) return <p className="muted">Loading…</p>;

  const isOwner = user!.id === detail.requesterId;
  const canSubmit = isOwner && detail.status === "DRAFT";
  const canDecide =
    !isOwner &&
    ((detail.status === "SUBMITTED" &&
      user!.role === "MANAGER" &&
      user!.departmentName === detail.departmentName) ||
      (detail.status === "MANAGER_APPROVED" && user!.role === "FINANCE"));

  return (
    <>
      <p>
        <Link to="/">← Back to requests</Link>
      </p>
      <div className="row">
        <div>
          <h1>{detail.title}</h1>
          <p className="subtitle">
            #{detail.id} · {detail.requesterName} · {detail.departmentName}
          </p>
        </div>
        <StatusBadge status={detail.status} />
      </div>

      {detail.description && <p>{detail.description}</p>}

      <div className="card" style={{ marginBottom: "1.25rem" }}>
        <table>
          <thead>
            <tr>
              <th>Description</th>
              <th>Category</th>
              <th>Date</th>
              <th className="right">Amount</th>
            </tr>
          </thead>
          <tbody>
            {detail.items.map((it) => (
              <tr key={it.id}>
                <td>{it.description}</td>
                <td>{it.category}</td>
                <td className="muted">{it.incurredOn}</td>
                <td className="right">{it.amount.toFixed(2)}</td>
              </tr>
            ))}
            <tr>
              <td colSpan={3} className="right">
                <strong>Total</strong>
              </td>
              <td className="right">
                <strong>
                  {detail.currency} {detail.totalAmount.toFixed(2)}
                </strong>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      {(canSubmit || canDecide) && (
        <div className="card card-pad stack" style={{ marginBottom: "1.25rem" }}>
          <strong>Actions</strong>
          {canDecide && (
            <div>
              <label>Comment (optional)</label>
              <input value={comment} onChange={(e) => setComment(e.target.value)} maxLength={500} />
            </div>
          )}
          {error && <div className="error">{error}</div>}
          <div style={{ display: "flex", gap: "0.6rem" }}>
            {canSubmit && (
              <button disabled={busy} onClick={() => act("submit")}>
                Submit for approval
              </button>
            )}
            {canDecide && (
              <>
                <button disabled={busy} onClick={() => act("approve")}>
                  Approve
                </button>
                <button className="danger" disabled={busy} onClick={() => act("reject")}>
                  Reject
                </button>
              </>
            )}
          </div>
        </div>
      )}

      <h3 style={{ marginBottom: "0.5rem" }}>Approval trail</h3>
      {detail.approvals.length === 0 ? (
        <p className="muted">Not submitted yet.</p>
      ) : (
        <div className="card">
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>Who</th>
                <th>Action</th>
                <th>Transition</th>
                <th>Comment</th>
              </tr>
            </thead>
            <tbody>
              {detail.approvals.map((a) => (
                <tr key={a.id}>
                  <td className="muted">{new Date(a.createdAt).toLocaleString()}</td>
                  <td>{a.actorName}</td>
                  <td>{a.action}</td>
                  <td className="muted">
                    {a.fromStatus.replace(/_/g, " ").toLowerCase()} →{" "}
                    {a.toStatus.replace(/_/g, " ").toLowerCase()}
                  </td>
                  <td>{a.comment || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
