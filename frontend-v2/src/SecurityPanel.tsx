import { useEffect, useState } from "react";

type Asset = {
  id: number;
  hostname: string;
  ipv4: string;
  os_name: string;
  patch_state: string;
  protection_state: string;
  last_seen: string;
};
type Alert = { id: number; hostname: string; category: string; severity: string; status: string; message: string };
type Network = { cidr: string; label: string; enabled: boolean };
type Summary = { assets: number; openAlerts: number; networks: number };

async function json<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, { credentials: "include", signal, cache: "no-store" });
  if (!response.ok) throw new Error("Sicherheitsdaten nicht erreichbar (HTTP " + response.status + ")");
  return response.json() as Promise<T>;
}

export default function SecurityPanel() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [networks, setNetworks] = useState<Network[]>([]);
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState("");
  const [cidr, setCidr] = useState("");
  const [label, setLabel] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const ctl = new AbortController();
    setError("");
    void Promise.all([
      json<Summary>("/api/v1/security/summary", ctl.signal),
      json<Asset[]>("/api/v1/security/assets", ctl.signal),
      json<Alert[]>("/api/v1/security/alerts", ctl.signal),
      json<Network[]>("/api/v1/security/networks", ctl.signal)
    ]).then(([stats, devices, findings, scopes]) => {
      if (!ctl.signal.aborted) {
        setSummary(stats);
        setAssets(devices);
        setAlerts(findings);
        setNetworks(scopes);
      }
    }).catch((err: unknown) => {
      if (!ctl.signal.aborted) setError(err instanceof Error ? err.message : "Ladefehler");
    });
    return () => ctl.abort();
  }, [refresh]);

  async function addNetwork(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!cidr || !label || saving) return;
    setSaving(true);
    setError("");
    try {
      const csrf = await json<{header: string;token: string}>("/api/v1/auth/csrf");
      const response = await fetch("/api/v1/security/networks", {
        method: "POST", credentials: "include",
        headers: { "Content-Type": "application/json", [csrf.header]: csrf.token },
        body: JSON.stringify({ cidr, label })
      });
      if (!response.ok) throw new Error("Netzwerk konnte nicht angelegt werden (HTTP " + response.status + ")");
      setCidr("");
      setLabel("");
      setRefresh(r => r + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Speichern fehlgeschlagen");
    } finally { setSaving(false); }
  }

  return <section>
    <p className="eyebrow">INFRASTRUKTUR · SICHERHEIT</p>
    <h1>Netzwerk & Endpoints</h1>
    <p className="lead">Nur angemeldete, berechtigte Administratoren. Geräte werden nur aus
      registrierten privaten Netzbereichen und signierten Collector-Meldungen übernommen.</p>
    <div className="metric-grid">
      <div className="metric"><span>Geräte</span><strong>{summary?.assets ?? "–"}</strong></div>
      <div className="metric"><span>Offene Meldungen</span><strong>{summary?.openAlerts ?? "–"}</strong></div>
      <div className="metric"><span>Freigegebene Netze</span><strong>{summary?.networks ?? "–"}</strong></div>
    </div>
    {error && <p role="alert">{error}</p>}
    <button className="primary-button" type="button" onClick={() => setRefresh(r => r + 1)}>Aktualisieren</button>
    <div className="panel">
      <h2>Geräteinventar</h2>
      {assets.length === 0 ? <p>Keine Collector-Geräte gemeldet.</p> : <div className="audit-scroll">
        <table className="audit-table"><thead><tr>
          <th>Gerät</th><th>IPv4</th><th>Betriebssystem</th><th>Patches</th><th>Endpoint-Schutz</th>
        </tr></thead><tbody>{assets.map(a => <tr key={a.id}>
          <td>{a.hostname}</td><td>{a.ipv4}</td><td>{a.os_name}</td>
          <td>{a.patch_state}</td><td>{a.protection_state}</td>
        </tr>)}</tbody></table></div>}
    </div>
    <div className="panel">
      <h2>Sicherheitsmeldungen</h2>
      {alerts.length === 0 ? <p>Keine gemeldeten Sicherheitsereignisse.</p> : <div className="audit-scroll">
        <table className="audit-table"><thead><tr><th>Gerät</th><th>Schweregrad</th><th>Meldung</th><th>Status</th></tr></thead>
          <tbody>{alerts.map(a => <tr key={a.id}>
            <td>{a.hostname}</td><td>{a.severity}</td><td>{a.message}</td><td>{a.status}</td>
          </tr>)}</tbody></table></div>}
    </div>
    <div className="panel">
      <h2>Freigegebene Netzbereiche</h2>
      <p>Nur RFC1918-Netze zwischen /16 und /30, keine pauschalen Internet-Scans.</p>
      {networks.map(n => <p key={n.cidr}>{n.cidr} – {n.label} ({n.enabled ? "aktiv" : "deaktiviert"})</p>)}
      <form onSubmit={addNetwork} className="security-network-form">
        <label>Netzadresse / Präfix
          <input value={cidr} onChange={event => setCidr(event.target.value)}
            placeholder="172.22.120.0/24" maxLength={20} required />
        </label>
        <label>Bezeichnung
          <input value={label} onChange={event => setLabel(event.target.value)}
            placeholder="Testnetz" maxLength={80} required />
        </label>
        <button className="primary-button" type="submit" disabled={saving}>Netzwerk registrieren</button>
      </form>
    </div>
  </section>;
}
