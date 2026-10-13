export type Module = {
  id: string;
  version: string;
  name: string;
  type: "feature" | "dependency" | string;
  page?: string;
  state?: string;
};

export type ShellState =
  | { kind: "loading" }
  | { kind: "login" }
  | { kind: "ready"; modules: Module[]; admin: boolean }
  | { kind: "error"; message: string };

async function request(path: string, signal?: AbortSignal): Promise<Response> {
  return fetch(path, {
    credentials: "include",
    redirect: "error",
    cache: "no-store",
    headers: { Accept: "application/json" },
    signal,
  });
}

export async function loadShell(signal: AbortSignal): Promise<ShellState> {
  try {
    const response = await request("/api/v1/modules", signal);
    if (response.status === 401 || response.status === 403) return { kind: "login" };
    if (!response.ok) throw new Error("Die Modulübersicht ist derzeit nicht erreichbar.");
    const json: unknown = await response.json();
    if (typeof json !== "object" || json === null || !("modules" in json) ||
        !Array.isArray(json.modules)) throw new Error("Ungültige Antwort vom Backend.");
    const modules: Module[] = json.modules.filter((m: unknown): m is Module =>
      typeof m === "object" && m !== null &&
      "id" in m && typeof m.id === "string" &&
      "name" in m && typeof m.name === "string" &&
      "version" in m && typeof m.version === "string" &&
      "type" in m && typeof m.type === "string"
    ).filter((m: Module) => m.id.length <= 100 && m.name.length <= 120);
    // The published Core release has /api/v1/auth/me but may predate the
    // optional v2 capabilities endpoint. Role comes from the authenticated server.
    const account = await request("/api/v1/auth/me", signal);
    if (account.status === 401 || account.status === 403) return { kind: "login" };
    if (!account.ok) throw new Error("Kontoinformationen konnten nicht geladen werden.");
    const details: unknown = await account.json();
    if (typeof details !== "object" || details === null) throw new Error("Ungültiger Kontostatus.");
    const role = "role" in details ? details.role : undefined;
    const mustChange = "mustChangePassword" in details ? details.mustChangePassword : false;
    if (mustChange === true) return { kind: "login" };
    return { kind: "ready", modules, admin: role === "ADMIN" };
  } catch (error) {
    if (signal.aborted) return { kind: "loading" };
    return { kind: "error", message: error instanceof Error ? error.message : "Verbindungsfehler" };
  }
}

export function safeModulePage(page: string | undefined): string | undefined {
  if (!page || page.length > 200 || !/^\/(?!\/)[A-Za-z0-9/_?=&.%+-]+$/.test(page)) return undefined;
  return page;
}
