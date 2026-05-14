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
};
