// Centralised API client for SmartGNSS
const BASE_URL = process.env.EXPO_PUBLIC_BACKEND_URL;
const API = `${BASE_URL}/api`;

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API}${path}`, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`HTTP ${res.status}: ${text || res.statusText}`);
  }
  const ct = res.headers.get("content-type") || "";
  if (ct.includes("application/json")) return res.json() as Promise<T>;
  return (await res.text()) as unknown as T;
}

export type Project = {
  id: string;
  name: string;
  description?: string;
  coordinate_system: string;
  created_at: string;
  point_count: number;
};

export type Point = {
  id: string;
  project_id: string;
  name: string;
  code?: string;
  latitude: number;
  longitude: number;
  altitude: number;
  accuracy_h: number;
  accuracy_v: number;
  antenna_height: number;
  fix_type: string;
  satellites: number;
  note?: string;
  created_at: string;
};

export const api = {
  // Projects
  listProjects: () => request<Project[]>("/projects"),
  getProject: (id: string) => request<Project>(`/projects/${id}`),
  createProject: (body: { name: string; description?: string; coordinate_system?: string }) =>
    request<Project>("/projects", { method: "POST", body: JSON.stringify(body) }),
  deleteProject: (id: string) =>
    request<{ deleted: boolean }>(`/projects/${id}`, { method: "DELETE" }),

  // Points
  listPoints: (projectId: string) =>
    request<Point[]>(`/points?project_id=${encodeURIComponent(projectId)}`),
  createPoint: (body: Partial<Point> & { project_id: string; name: string; latitude: number; longitude: number }) =>
    request<Point>("/points", { method: "POST", body: JSON.stringify(body) }),
  deletePoint: (id: string) =>
    request<{ deleted: boolean }>(`/points/${id}`, { method: "DELETE" }),

  // Export
  exportCsv: (projectId: string) => request<string>(`/export/csv/${projectId}`),
  exportKml: (projectId: string) => request<string>(`/export/kml/${projectId}`),

  // COGO
  cogoInverse: (body: { lat1: number; lon1: number; lat2: number; lon2: number }) =>
    request<{ distance_m: number; bearing_deg: number }>("/cogo/inverse", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  cogoArea: (body: { points: { lat: number; lon: number }[] }) =>
    request<{ area_m2: number; perimeter_m: number }>("/cogo/area", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  // Devices
  scanDevices: () =>
    request<{ devices: { id: string; name: string; type: string; rssi: number }[] }>("/devices/scan"),

  // NTRIP
  ntripSourcetable: (body: { host: string; port: number; username?: string; password?: string }) =>
    request<{ caster: string; raw_lines: number; mountpoints: NtripMount[] }>("/ntrip/sourcetable", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  ntripConnect: (body: { host: string; port: number; mountpoint: string; username?: string; password?: string }) =>
    request<{ session_id: string; status: string }>("/ntrip/connect", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  ntripStatus: (sessionId: string) => request<NtripSession>(`/ntrip/status/${sessionId}`),
  ntripSessions: () => request<{ sessions: NtripSession[] }>("/ntrip/sessions"),
  ntripDisconnect: (sessionId: string) =>
    request<{ disconnected: boolean }>(`/ntrip/session/${sessionId}`, { method: "DELETE" }),

  // CAD
  uploadCad: async (
    projectId: string,
    file: { uri: string; name: string; mimeType?: string },
    extras?: { dbf?: { uri: string; name: string }; shx?: { uri: string; name: string } }
  ) => {
    const form = new FormData();
    form.append("project_id", projectId);
    form.append("file", {
      uri: file.uri,
      name: file.name,
      type: file.mimeType || "application/octet-stream",
    } as any);
    if (extras?.dbf) {
      form.append("dbf", { uri: extras.dbf.uri, name: extras.dbf.name, type: "application/octet-stream" } as any);
    }
    if (extras?.shx) {
      form.append("shx", { uri: extras.shx.uri, name: extras.shx.name, type: "application/octet-stream" } as any);
    }
    const res = await fetch(`${API}/cad/upload`, { method: "POST", body: form as any });
    if (!res.ok) {
      const t = await res.text().catch(() => "");
      throw new Error(`HTTP ${res.status}: ${t}`);
    }
    return res.json() as Promise<CadOverlay>;
  },
  listCad: (projectId: string) =>
    request<CadOverlay[]>(`/cad?project_id=${encodeURIComponent(projectId)}`),
  getCad: (overlayId: string) => request<CadOverlay>(`/cad/${overlayId}`),
  deleteCad: (overlayId: string) =>
    request<{ deleted: boolean }>(`/cad/${overlayId}`, { method: "DELETE" }),
};

export type NtripMount = {
  mountpoint: string;
  identifier: string;
  format: string;
  format_details: string;
  carrier: string;
  nav_system: string;
  network: string;
  country: string;
  latitude: number | null;
  longitude: number | null;
};

export type NtripSession = {
  id: string;
  host: string;
  port: number;
  mountpoint: string;
  username: string;
  started_at: string;
  bytes: number;
  msg_types: Record<string, number>;
  status: "connecting" | "streaming" | "disconnected" | "error";
  last_rx: string | null;
  error: string | null;
};

export type CadOverlay = {
  id: string;
  project_id: string;
  filename: string;
  file_type: "dxf" | "shp";
  feature_count: number;
  geojson: any;
  bounds: [number, number, number, number] | null;
  created_at: string;
};
