// Real Bluetooth BLE connector via react-native-ble-plx.
// Falls back to a software simulator when running inside Expo Go or on web.

import { Platform } from "react-native";
import Constants from "expo-constants";
import { NmeaBuffer, NmeaState, makeInitialNmeaState, parseNmeaSentence } from "./nmea";

export const isExpoGo = Constants.appOwnership === "expo";
export const bleSupported =
  Platform.OS !== "web" && !isExpoGo;

let BleManagerCtor: any = null;
if (bleSupported) {
  try {
     
    BleManagerCtor = require("react-native-ble-plx").BleManager;
  } catch {
    // module not built in -> stay in simulator
  }
}

export type BleDevice = {
  id: string;
  name: string;
  rssi: number | null;
  isSimulated?: boolean;
};

export type BleStatus = "idle" | "scanning" | "connecting" | "connected" | "error";

type Listener = (state: NmeaState, status: BleStatus, raw: string[]) => void;

class BleGnssConnector {
  private manager: any = null;
  private nmeaState: NmeaState = makeInitialNmeaState();
  private status: BleStatus = "idle";
  private listeners = new Set<Listener>();
  private connectedDevice: any = null;
  private monitorSub: any = null;
  private simulatorTimer: any = null;
  private recentRaw: string[] = [];
  private buffer = new NmeaBuffer();

  on(l: Listener) {
    this.listeners.add(l);
    l(this.nmeaState, this.status, this.recentRaw);
    return () => this.listeners.delete(l);
  }

  private emit() {
    for (const l of this.listeners) l(this.nmeaState, this.status, this.recentRaw);
  }

  private pushRaw(line: string) {
    this.recentRaw.push(line);
    if (this.recentRaw.length > 40) this.recentRaw.shift();
  }

  /** Start a software simulator that synthesises NMEA sentences. */
  startSimulator() {
    this.stop();
    this.status = "connected";
    const baseLat = 40.71284;
    const baseLon = -74.00601;
    let i = 0;
    this.simulatorTimer = setInterval(() => {
      i++;
      const drift = (Math.sin(i / 5) * 0.000002).toFixed(7);
      const lat = (baseLat + parseFloat(drift)).toFixed(7);
      const lon = (baseLon - parseFloat(drift)).toFixed(7);
      const quality = [1, 1, 2, 5, 4, 4, 4][i % 7];
      const sats = 14 + Math.floor(Math.random() * 10);
      const hdop = (0.6 + Math.random() * 0.7).toFixed(2);
      const alt = (12 + Math.random() * 0.3).toFixed(2);
      const utc = new Date()
        .toISOString()
        .slice(11, 19)
        .replace(/:/g, "")
        .padEnd(9, ".00");
      const latNmea = degToNmea(parseFloat(lat), true);
      const lonNmea = degToNmea(parseFloat(lon), false);
      const ggaBody = `GPGGA,${utc},${latNmea.value},${latNmea.hemi},${lonNmea.value},${lonNmea.hemi},${quality},${String(sats).padStart(2,"0")},${hdop},${alt},M,-34.2,M,${quality === 2 || quality >= 4 ? "1.0" : ""},0000`;
      const gga = `$${ggaBody}*${nmeaChecksum(ggaBody)}`;
      const rmcBody = `GPRMC,${utc},A,${latNmea.value},${latNmea.hemi},${lonNmea.value},${lonNmea.hemi},0.05,${(Math.random()*360).toFixed(1)},${ddmmyy()},,,A`;
      const rmc = `$${rmcBody}*${nmeaChecksum(rmcBody)}`;
      this.handleLine(gga);
      this.handleLine(rmc);
      this.emit();
    }, 1000);
    this.emit();
  }

  private handleLine(line: string) {
    this.pushRaw(line);
    const upd = parseNmeaSentence(line);
    if (upd) {
      this.nmeaState = { ...this.nmeaState, ...filterDefined(upd) };
    }
  }

  async scan(onDevice: (d: BleDevice) => void, timeoutMs = 8000): Promise<void> {
    if (!bleSupported || !BleManagerCtor) {
      // emit a few simulated devices
      ["CHC i73+ (sim)", "South G1 (sim)", "Stonex S850A (sim)"].forEach((n, i) => {
        onDevice({ id: `sim-${i}`, name: n, rssi: -55 - i * 6, isSimulated: true });
      });
      return;
    }
    this.manager ??= new BleManagerCtor();
    this.status = "scanning";
    this.emit();
    const seen = new Set<string>();
    return new Promise<void>((resolve) => {
      this.manager.startDeviceScan(null, null, (error: any, device: any) => {
        if (error) {
          this.status = "error";
          this.emit();
          resolve();
          return;
        }
        if (device && device.id && !seen.has(device.id)) {
          seen.add(device.id);
          onDevice({
            id: device.id,
            name: device.name || device.localName || "Unknown",
            rssi: device.rssi ?? null,
          });
        }
      });
      setTimeout(() => {
        try {
          this.manager?.stopDeviceScan();
        } catch {}
        this.status = "idle";
        this.emit();
        resolve();
      }, timeoutMs);
    });
  }

  async connectToReal(deviceId: string): Promise<void> {
    if (!bleSupported || !BleManagerCtor) {
      throw new Error("Bluetooth requires a development build (not Expo Go / web)");
    }
    this.manager ??= new BleManagerCtor();
    this.status = "connecting";
    this.emit();
    try {
      const device = await this.manager.connectToDevice(deviceId, { timeout: 12000 });
      await device.discoverAllServicesAndCharacteristics();
      this.connectedDevice = device;
      // Find first notifiable characteristic and subscribe
      const services = await device.services();
      let subscribed = false;
      for (const svc of services) {
        const chars = await svc.characteristics();
        for (const ch of chars) {
          if (ch.isNotifiable || ch.isIndicatable) {
            this.monitorSub = ch.monitor((err: any, c: any) => {
              if (err) return;
              if (!c?.value) return;
              try {
                const decoded = atobSafe(c.value);
                const lines = this.buffer.push(decoded);
                for (const l of lines) this.handleLine(l);
                this.emit();
              } catch {}
            });
            subscribed = true;
            break;
          }
        }
        if (subscribed) break;
      }
      this.status = "connected";
      this.emit();
    } catch (e: any) {
      this.status = "error";
      this.emit();
      throw e;
    }
  }

  async stop() {
    if (this.simulatorTimer) {
      clearInterval(this.simulatorTimer);
      this.simulatorTimer = null;
    }
    if (this.monitorSub) {
      try {
        this.monitorSub.remove();
      } catch {}
      this.monitorSub = null;
    }
    if (this.connectedDevice) {
      try {
        await this.connectedDevice.cancelConnection();
      } catch {}
      this.connectedDevice = null;
    }
    this.status = "idle";
    this.nmeaState = makeInitialNmeaState();
    this.emit();
  }

  getState() {
    return this.nmeaState;
  }
  getStatus() {
    return this.status;
  }
  getRaw() {
    return this.recentRaw;
  }
}

function filterDefined<T extends object>(obj: T): Partial<T> {
  const out: any = {};
  for (const k in obj) {
    const v = (obj as any)[k];
    if (v !== undefined && v !== null) out[k] = v;
  }
  return out;
}

function degToNmea(deg: number, isLat: boolean) {
  const hemi = isLat ? (deg >= 0 ? "N" : "S") : deg >= 0 ? "E" : "W";
  const abs = Math.abs(deg);
  const d = Math.floor(abs);
  const min = (abs - d) * 60;
  const dStr = isLat ? String(d).padStart(2, "0") : String(d).padStart(3, "0");
  return { value: `${dStr}${min.toFixed(6).padStart(9, "0")}`, hemi };
}

function nmeaChecksum(body: string): string {
  let cs = 0;
  for (let i = 0; i < body.length; i++) cs ^= body.charCodeAt(i);
  return cs.toString(16).toUpperCase().padStart(2, "0");
}

function ddmmyy() {
  const d = new Date();
  return (
    String(d.getUTCDate()).padStart(2, "0") +
    String(d.getUTCMonth() + 1).padStart(2, "0") +
    String(d.getUTCFullYear() % 100).padStart(2, "0")
  );
}

function atobSafe(b64: string): string {
  // React Native lacks a global atob in some environments
   
  if (typeof atob === "function") return atob(b64);
  // simple base64 decoder
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  let str = b64.replace(/=+$/, "");
  let output = "";
  for (let bc = 0, bs = 0, buffer, idx = 0; (buffer = str.charAt(idx++)); ) {
    buffer = chars.indexOf(buffer);
    if (buffer < 0) continue;
    bs = bc % 4 ? (bs << 6) + buffer : buffer;
    if (bc++ % 4) output += String.fromCharCode((bs >> ((-2 * bc) & 6)) & 0xff);
  }
  return output;
}

export const gnss = new BleGnssConnector();
