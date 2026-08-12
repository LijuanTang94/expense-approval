import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../api";
import type { ItemInput, RequestDetail } from "../types";

const emptyItem = (): ItemInput => ({
  description: "",
  category: "Travel",
  amount: 0,
  incurredOn: new Date().toISOString().slice(0, 10),
});

const CATEGORIES = ["Travel", "Lodging", "Meals", "Supplies", "Software", "Other"];

export function NewRequest() {
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [currency, setCurrency] = useState("USD");
  const [items, setItems] = useState<ItemInput[]>([emptyItem()]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const total = items.reduce((s, i) => s + (Number(i.amount) || 0), 0);

  const updateItem = (idx: number, patch: Partial<ItemInput>) =>
    setItems((prev) => prev.map((it, i) => (i === idx ? { ...it, ...patch } : it)));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const created = await api<RequestDetail>("/api/requests", {
        method: "POST",
        body: { title, description, currency, items: items.map((i) => ({ ...i, amount: Number(i.amount) })) },
      });
      navigate(`/requests/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not create request");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <h1>New expense request</h1>
      <p className="subtitle">Created as a draft — you can review and submit it on the next screen.</p>

      <form onSubmit={submit} className="stack" style={{ maxWidth: 720 }}>
        <div className="card card-pad stack">
          <div>
            <label>Title</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} />
          </div>
          <div>
            <label>Description</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} />
          </div>
          <div style={{ maxWidth: 120 }}>
            <label>Currency</label>
            <input value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} maxLength={3} />
          </div>
        </div>

        <div className="card card-pad stack">
          <div className="row">
            <strong>Line items</strong>
            <button type="button" className="ghost" onClick={() => setItems((p) => [...p, emptyItem()])}>
              + Add item
            </button>
          </div>
          {items.map((it, idx) => (
            <div key={idx} className="grid2" style={{ gridTemplateColumns: "2fr 1fr 1fr 1fr auto", alignItems: "end" }}>
              <div>
                <label>Description</label>
                <input value={it.description} onChange={(e) => updateItem(idx, { description: e.target.value })} required />
              </div>
              <div>
                <label>Category</label>
                <select value={it.category} onChange={(e) => updateItem(idx, { category: e.target.value })}>
                  {CATEGORIES.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              </div>
              <div>
                <label>Amount</label>
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={it.amount}
                  onChange={(e) => updateItem(idx, { amount: Number(e.target.value) })}
                  required
                />
              </div>
              <div>
                <label>Date</label>
                <input type="date" value={it.incurredOn} onChange={(e) => updateItem(idx, { incurredOn: e.target.value })} required />
              </div>
              <button
                type="button"
                className="ghost"
                disabled={items.length === 1}
                onClick={() => setItems((p) => p.filter((_, i) => i !== idx))}
                title="Remove"
              >
                ✕
              </button>
            </div>
          ))}
          <div className="right">
            <strong>Total: {currency} {total.toFixed(2)}</strong>
          </div>
        </div>

        {error && <div className="error">{error}</div>}
        <div>
          <button type="submit" disabled={busy}>
            {busy ? "Creating…" : "Create draft"}
          </button>
        </div>
      </form>
    </>
  );
}
