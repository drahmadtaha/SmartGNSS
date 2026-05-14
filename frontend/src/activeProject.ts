import { storage } from "@/src/utils/storage";

const ACTIVE_KEY = "smartgnss.active_project_id";

export async function getActiveProjectId(): Promise<string | null> {
  return (await storage.getItem<string>(ACTIVE_KEY, "")) || null;
}

export async function setActiveProjectId(id: string | null): Promise<void> {
  if (id) await storage.setItem(ACTIVE_KEY, id);
  else await storage.removeItem(ACTIVE_KEY);
}
