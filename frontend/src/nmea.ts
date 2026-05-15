// Lightweight NMEA-0183 parser for SmartGNSS.
// Supports: $xxGGA, $xxRMC, $xxGSA, $xxGSV (any talker — GP, GN, GL, GA, BD, GB).
// Returns partial updates which the caller merges into a single live state.

export type NmeaFixQuality = 0 | 1 | 2 | 4 | 5 | 6; // GGA quality
export const FIX_LABEL: Record<NmeaFixQuality, "NoFix" | "Single" | "DGPS" | "Fixed" | "Float" | "DR"> = {
  0: "NoFix",
  1: "Single",
  2: "DGPS",
  4: "Fixed",
  5: "Float",
  6: "DR",
};

export type NmeaState = {
  latitude: number | null;
  longitude: number | null;
  altitude: number | null;
  hdop: number | null;
  vdop: number | null;
  pdop: number | null;
  satellites: number; // satellites in use
  satsInView: number;
  quality: NmeaFixQuality;
  fixLabel: "NoFix" | "Single" | "DGPS" | "Fixed" | "Float" | "DR";
  speed_ms: number | null;
  heading_deg: number | null;
  age_corr_s: number | null;
  utc: string | null;
  lastRawAt: number; // ms
};

export function makeInitialNmeaState(): NmeaState {
  return {
    latitude: null,
    longitude: null,
    altitude: null,
    hdop: null,
    vdop: null,
    pdop: null,
    satellites: 0,
    satsInView: 0,
    quality: 0,
    fixLabel: "NoFix",
    speed_ms: null,
    heading_deg: null,
    age_corr_s: null,
    utc: null,
    lastRawAt: 0,
  };
}

function parseLatLon(value: string, hemi: string): number | null {
  if (!value || !hemi) return null;
  const dotIdx = value.indexOf(".");
  const degLen = dotIdx > 4 ? dotIdx - 2 : 2;
  const deg = parseInt(value.substring(0, degLen), 10);
  const min = parseFloat(value.substring(degLen));
  if (Number.isNaN(deg) || Number.isNaN(min)) return null;
  let v = deg + min / 60;
  if (hemi === "S" || hemi === "W") v = -v;
  return v;
}

function num(s: string): number | null {
  if (s === undefined || s === "") return null;
  const n = parseFloat(s);
  return Number.isNaN(n) ? null : n;
}

export function validateChecksum(sentence: string): boolean {
  const star = sentence.indexOf("*");
  if (star < 1 || star >= sentence.length - 1) return false;
  const body = sentence.startsWith("$") ? sentence.slice(1, star) : sentence.slice(0, star);
  let cs = 0;
  for (let i = 0; i < body.length; i++) cs ^= body.charCodeAt(i);
  const declared = parseInt(sentence.slice(star + 1, star + 3), 16);
  return cs === declared;
}

/** Parse one NMEA sentence and return partial state update (or null). */
export function parseNmeaSentence(raw: string): Partial<NmeaState> | null {
  const line = raw.trim();
  if (!line.startsWith("$")) return null;
  const star = line.indexOf("*");
  const body = star > 0 ? line.slice(1, star) : line.slice(1);
  const fields = body.split(",");
  const id = fields[0]; // e.g. GPGGA
  if (id.length < 5) return null;
  const sentence = id.slice(2); // GGA, RMC, GSA, GSV

  if (sentence === "GGA") {
    const utc = fields[1] || null;
    const lat = parseLatLon(fields[2], fields[3]);
    const lon = parseLatLon(fields[4], fields[5]);
    const q = (parseInt(fields[6] || "0", 10) || 0) as NmeaFixQuality;
    const sats = parseInt(fields[7] || "0", 10) || 0;
    const hdop = num(fields[8]);
    const alt = num(fields[9]);
    const ageCorr = num(fields[13]);
    return {
      utc,
      latitude: lat,
      longitude: lon,
      quality: q,
      fixLabel: FIX_LABEL[q] ?? "NoFix",
      satellites: sats,
      hdop,
      altitude: alt,
      age_corr_s: ageCorr,
      lastRawAt: Date.now(),
    };
  }

  if (sentence === "RMC") {
    const utc = fields[1] || null;
    const lat = parseLatLon(fields[3], fields[4]);
    const lon = parseLatLon(fields[5], fields[6]);
    const knots = num(fields[7]);
    const heading = num(fields[8]);
    return {
      utc,
      latitude: lat ?? undefined as any,
      longitude: lon ?? undefined as any,
      speed_ms: knots != null ? knots * 0.514444 : null,
      heading_deg: heading,
      lastRawAt: Date.now(),
    };
  }

  if (sentence === "GSA") {
    const pdop = num(fields[15]);
    const hdop = num(fields[16]);
    const vdop = num(fields[17]);
    return { pdop, hdop, vdop, lastRawAt: Date.now() };
  }

  if (sentence === "GSV") {
    const inView = parseInt(fields[3] || "0", 10) || 0;
    return { satsInView: inView, lastRawAt: Date.now() };
  }
  return null;
}

/** Feed raw bytes/string from a serial / BLE stream; emit completed sentences. */
export class NmeaBuffer {
  private buf = "";
  push(chunk: string | Uint8Array): string[] {
    const text =
      typeof chunk === "string" ? chunk : String.fromCharCode.apply(null, Array.from(chunk));
    this.buf += text;
    const out: string[] = [];
    while (true) {
      const idx = this.buf.indexOf("\n");
      if (idx < 0) break;
      const line = this.buf.slice(0, idx).replace(/\r$/, "");
      this.buf = this.buf.slice(idx + 1);
      if (line.startsWith("$")) out.push(line);
    }
    return out;
  }
}
