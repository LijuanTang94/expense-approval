import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import { ApiError } from "../api";

const DEMO = [
  ["alice@acme.com", "Employee · Engineering"],
  ["bob@acme.com", "Manager · Engineering"],
  ["fiona@acme.com", "Finance"],
];

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation() as { state?: { from?: string } };
  const [email, setEmail] = useState("alice@acme.com");
  const [password, setPassword] = useState("password123");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(email, password);
      navigate(location.state?.from ?? "/", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Login failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ maxWidth: 380, margin: "6rem auto" }}>
      <h1>Expense Approvals</h1>
      <p className="subtitle">Sign in to submit and approve expense reimbursements.</p>
      <div className="card card-pad">
        <form onSubmit={submit} className="stack">
          <div>
            <label>Email</label>
            <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" required />
          </div>
          <div>
            <label>Password</label>
            <input
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type="password"
              required
            />
          </div>
          {error && <div className="error">{error}</div>}
          <button type="submit" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>
      </div>
      <div className="card card-pad" style={{ marginTop: "1rem" }}>
        <p className="hint" style={{ marginTop: 0 }}>
          Demo accounts (password <code>password123</code>):
        </p>
        {DEMO.map(([mail, desc]) => (
          <div key={mail} className="hint">
            <button
              className="ghost"
              style={{ padding: "0.15rem 0.5rem", marginRight: 8, fontSize: "0.8rem" }}
              onClick={() => setEmail(mail)}
              type="button"
            >
              use
            </button>
            <code>{mail}</code> — {desc}
          </div>
        ))}
      </div>
    </div>
  );
}
