import { useEffect, useMemo, useState } from "react";
import { loadShell, safeModulePage, type Module, type ShellState } from "./api";

function Symbol({ text }: { text: string }) {
  return <span className="symbol" aria-hidden="true">{text}</span>;
}

function ModuleCard({ module }: { module: Module }) {
  const page = safeModulePage(module.page);
  return (
    <article className="module-card">
      <div className="module-icon"><Symbol text="◈" /></div>
      <div>
        <h3>{module.name}</h3>
        <p className="secondary">{module.id} · Version {module.version}</p>
        <p className="small">Status: {module.state || "registriert"}</p>
      </div>
      {page ? <a className="subtle-link" href={page}>Öffnen →</a> :
        <span className="secondary small">Noch keine aktive Seite</span>}
    </article>
  );
}

export default function App() {
  const [state, setState] = useState<ShellState>({ kind: "loading" });
  const [section, setSection] = useState("home");

  useEffect(() => {
    const controller = new AbortController();
    void loadShell(controller.signal).then(next => {
      if (!controller.signal.aborted) setState(next);
    });
    return () => controller.abort();
  }, []);

  const features = useMemo(() => state.kind === "ready"
    ? state.modules.filter(m => m.type === "feature") : [], [state]);
  const dependencies = state.kind === "ready"
    ? state.modules.filter(m => m.type === "dependency") : [];

  if (state.kind === "loading") {
    return <main className="center-state"><span className="spinner" />Lade Sibyl …</main>;
  }
  if (state.kind === "login") {
    return <main className="center-state"><div className="login-panel">
      <div className="brand-logo">S</div><h1>Sibyl System</h1>
      <p className="secondary">Bitte melde dich an, um auf deine Module zugreifen zu können.</p>
      <a className="primary-button" href="/login.html">Zur Anmeldung</a>
    </div></main>;
  }
  if (state.kind === "error") {
    return <main className="center-state"><div className="login-panel">
      <div className="brand-logo">S</div><h1>Backend nicht erreichbar</h1>
      <p className="secondary">{state.message}</p>
      <button className="primary-button" onClick={() => window.location.reload()}>Erneut versuchen</button>
    </div></main>;
  }

  const selected = features.find(m => section === m.id);
  return <div className="app-shell">
    <aside className="sidebar" aria-label="Hauptnavigation">
      <div className="brand"><div className="brand-logo">S</div>
        <div><strong>Sibyl System</strong><span>Modulare Plattform</span></div></div>
      <div className="side-heading">PLATTFORM</div>
      <button className={"nav-item " + (section === "home" ? "selected" : "")}
              onClick={() => setSection("home")}><Symbol text="⌂" /> Übersicht</button>
      {state.admin && <>
        <a className="nav-item" href="/addons.html"><Symbol text="▦" /><span>Addon-Browser</span></a>
        <a className="nav-item" href="/admin.html"><Symbol text="⚙" /><span>Administration</span></a>
      </>}
      {features.length > 0 && <div className="side-heading">DEINE MODULE</div>}
      {features.map(m => <button key={m.id}
        className={"nav-item " + (section === m.id ? "selected" : "")}
        onClick={() => setSection(m.id)}><Symbol text="◈" /><span>{m.name}</span></button>)}
      <div className="side-footer">V2 · Entwicklungsansicht<br />Erweiterungen sind isoliert zu aktivieren.</div>
    </aside>
    <div className="workspace">
      <header className="topbar"><div><strong>Sibyl</strong><span className="separator"> / </span>
        <span className="secondary">{selected?.name || (section === "admin" ? "Administration" : "Übersicht")}</span></div>
        <span className="environment-tag">{import.meta.env.VITE_SIBYL_ENV_LABEL || "Vorschau"}</span>
      </header>
      <main className="content">
        {section === "admin" ? <>
          <p className="eyebrow">VERWALTUNG</p><h1>Administration</h1>
          <p className="lead">Konten, Addons und Einstellungen bleiben serverseitig geschützt.</p>
          <div className="panel"><h2>Addons & Berechtigungen</h2>
            <p>{dependencies.length} registrierte Integrationen ohne eigene Navigationsseite.</p>
            <p>Neue Berechtigungen und Deployments erfordern eine administrative Freigabe.</p>
            <a className="subtle-link" href="/admin.html">Bestehende Administration öffnen →</a></div>
        </> : selected ? <>
          <p className="eyebrow">ERWEITERUNG</p><h1>{selected.name}</h1>
          <p className="lead">Die Erweiterung wird vom Core verwaltet. Der Worker läuft getrennt vom Core.</p>
          <ModuleCard module={selected} />
        </> : <>
          <p className="eyebrow">WILLKOMMEN ZURÜCK</p>
          <h1>Deine Arbeitsumgebung.</h1>
          <p className="lead">Alle installierten Funktionen an einem Ort – modular, individuell und sicher.</p>
          <div className="metric-grid">
            <div className="metric"><span>Aktive Funktionsmodule</span><strong>{features.length}</strong></div>
            <div className="metric"><span>Integrationen</span><strong>{dependencies.length}</strong></div>
            <div className="metric"><span>Plattform</span><strong>V2</strong></div>
          </div>
          <div className="section-title"><h2>Installierte Module</h2><span className="secondary small">Automatisch aus dem Core geladen</span></div>
          {features.length ? <div className="module-grid">{features.map(m =>
              <ModuleCard key={m.id} module={m} />)}</div> :
              <div className="panel empty"><h3>Noch keine Funktionsmodule aktiviert</h3>
                <p>Ein Administrator kann geprüfte GitHub-Release-Addons installieren.</p></div>}
        </>}
      </main>
    </div>
  </div>;
}
