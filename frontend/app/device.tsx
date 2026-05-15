import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Alert,
  Switch,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { colors, mono } from "@/src/theme";
import { gnss, bleSupported, isExpoGo, BleDevice } from "@/src/gnssConnector";
import type { NmeaState } from "@/src/nmea";
import * as Haptics from "expo-haptics";

export default function DeviceScreen() {
  const [devices, setDevices] = useState<BleDevice[]>([]);
  const [scanning, setScanning] = useState(false);
  const [simulator, setSimulator] = useState(!bleSupported); // default ON when BLE unsupported
  const [connectedId, setConnectedId] = useState<string | null>(null);
  const [state, setState] = useState<NmeaState | null>(null);
  const [status, setStatus] = useState<string>("idle");
  const [raw, setRaw] = useState<string[]>([]);

  useEffect(() => {
    const off = gnss.on((s, st, r) => {
      setState(s);
      setStatus(st);
      setRaw([...r]);
    });
    return () => {
      off();
    };
  }, []);

  const scan = async () => {
    setScanning(true);
    setDevices([]);
    try {
      await gnss.scan((d) => {
        setDevices((prev) => (prev.find((p) => p.id === d.id) ? prev : [...prev, d]));
      }, 8000);
    } catch (e: any) {
      Alert.alert("Scan failed", e.message);
    } finally {
      setScanning(false);
    }
  };

  useEffect(() => {
    if (!simulator) scan();
    // simulator launches via toggle effect below
  }, []);

  useEffect(() => {
    if (simulator) {
      gnss.startSimulator();
      setConnectedId("simulator");
    } else {
      if (connectedId === "simulator") {
        gnss.stop();
        setConnectedId(null);
      }
    }
  }, [simulator]);

  const connect = async (d: BleDevice) => {
    Haptics.selectionAsync().catch(() => {});
    if (connectedId === d.id) {
      await gnss.stop();
      setConnectedId(null);
      return;
    }
    try {
      await gnss.connectToReal(d.id);
      setConnectedId(d.id);
    } catch (e: any) {
      Alert.alert("Connect failed", e.message);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <View style={styles.header}>
        <View style={{ flex: 1 }}>
          <Text style={styles.title}>BLUETOOTH GNSS RECEIVERS</Text>
          <Text style={styles.sub}>
            {connectedId ? `Connected • ${status}` : "Not connected — using phone GPS"}
          </Text>
        </View>
        <TouchableOpacity
          style={[styles.scanBtn, simulator && { opacity: 0.5 }]}
          onPress={scan}
          disabled={scanning || simulator}
          testID="device-scan-btn"
        >
          {scanning ? (
            <ActivityIndicator color="#FFF" />
          ) : (
            <Ionicons name="refresh" size={18} color="#FFF" />
          )}
          <Text style={styles.scanBtnText}>{scanning ? "SCANNING" : "SCAN"}</Text>
        </TouchableOpacity>
      </View>

      {!bleSupported && (
        <View style={styles.warnBox}>
          <Ionicons name="warning" size={16} color={colors.float} />
          <Text style={styles.warnText}>
            Real Bluetooth is only available in a Development Build. {isExpoGo ? "Expo Go" : "Web"} is using the Simulator.
          </Text>
        </View>
      )}

      <View style={styles.simRow} testID="device-simulator-row">
        <View style={{ flex: 1 }}>
          <Text style={styles.simTitle}>SIMULATOR</Text>
          <Text style={styles.simSub}>Synthetic NMEA stream for testing the UI</Text>
        </View>
        <Switch
          value={simulator}
          onValueChange={setSimulator}
          trackColor={{ true: colors.primary, false: colors.surfaceAlt }}
          thumbColor="#FFF"
          testID="device-simulator-switch"
        />
      </View>

      {state && (connectedId || simulator) && (
        <View style={styles.liveCard}>
          <Text style={styles.liveLabel}>LIVE NMEA</Text>
          <View style={styles.liveGrid}>
            <Stat k="FIX" v={state.fixLabel} />
            <Stat k="SAT" v={String(state.satellites)} />
            <Stat k="HDOP" v={state.hdop?.toFixed(2) ?? "—"} />
            <Stat k="ALT" v={state.altitude != null ? `${state.altitude.toFixed(2)} m` : "—"} />
            <Stat
              k="LAT"
              v={state.latitude != null ? state.latitude.toFixed(7) : "—"}
              wide
            />
            <Stat
              k="LON"
              v={state.longitude != null ? state.longitude.toFixed(7) : "—"}
              wide
            />
          </View>
          {raw.length > 0 && (
            <View style={styles.console}>
              {raw.slice(-4).map((l, i) => (
                <Text key={i} style={styles.consoleLine} numberOfLines={1}>
                  {l}
                </Text>
              ))}
            </View>
          )}
        </View>
      )}

      <ScrollView contentContainerStyle={{ padding: 12 }}>
        <Text style={styles.sectionTitle}>{devices.length} DEVICE{devices.length === 1 ? "" : "S"} NEARBY</Text>
        {devices.length === 0 && !scanning && (
          <Text style={styles.emptyLine}>No devices detected. Tap SCAN.</Text>
        )}
        {devices.map((d) => {
          const connected = connectedId === d.id;
          return (
            <TouchableOpacity
              key={d.id}
              style={[styles.devCard, connected && styles.devCardActive]}
              onPress={() => connect(d)}
              testID={`device-${d.id}`}
            >
              <View
                style={[
                  styles.devIcon,
                  { backgroundColor: connected ? colors.fixed : colors.black },
                ]}
              >
                <Ionicons name="bluetooth" size={20} color="#FFF" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.devName}>{d.name}</Text>
                <Text style={styles.devType}>
                  ID {d.id.slice(0, 10)}{d.rssi != null ? ` • RSSI ${d.rssi} dBm` : ""}{d.isSimulated ? " • SIM" : ""}
                </Text>
              </View>
              <View
                style={[
                  styles.statusPill,
                  { backgroundColor: connected ? colors.fixed : colors.surfaceAlt },
                ]}
              >
                <Text
                  style={[
                    styles.statusText,
                    { color: connected ? "#FFF" : colors.text },
                  ]}
                >
                  {connected ? "CONNECTED" : "CONNECT"}
                </Text>
              </View>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </SafeAreaView>
  );
}

function Stat({ k, v, wide }: { k: string; v: string; wide?: boolean }) {
  return (
    <View style={[styles.stat, wide && { width: "50%" }]}>
      <Text style={styles.statK}>{k}</Text>
      <Text style={styles.statV}>{v}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: {
    flexDirection: "row",
    alignItems: "center",
    padding: 12,
    borderBottomWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  title: { fontSize: 13, fontWeight: "900", letterSpacing: 1.5, color: colors.text },
  sub: { fontSize: 11, color: colors.textSecondary, marginTop: 2, fontFamily: mono },
  scanBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: colors.primary,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 4,
  },
  scanBtnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1, fontSize: 12 },
  warnBox: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    padding: 10,
    backgroundColor: "#FEF5E7",
    borderBottomWidth: 1,
    borderColor: "#F8DDA4",
  },
  warnText: { color: colors.text, fontSize: 11, flex: 1, lineHeight: 16 },
  simRow: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    borderBottomWidth: 1,
    borderColor: colors.border,
    gap: 12,
  },
  simTitle: { fontSize: 11, fontWeight: "900", letterSpacing: 1.5, color: colors.text },
  simSub: { fontSize: 11, color: colors.textSecondary, marginTop: 2 },
  liveCard: {
    margin: 12,
    backgroundColor: colors.black,
    padding: 12,
    borderRadius: 6,
  },
  liveLabel: { color: colors.fixed, fontWeight: "900", letterSpacing: 2, fontSize: 10, marginBottom: 6 },
  liveGrid: { flexDirection: "row", flexWrap: "wrap" },
  stat: { width: "25%", paddingVertical: 4 },
  statK: { color: "#9E9E9E", fontSize: 9, fontWeight: "900", letterSpacing: 1 },
  statV: { color: "#FFF", fontFamily: mono, fontWeight: "700", marginTop: 2, fontSize: 13 },
  console: {
    marginTop: 8,
    padding: 8,
    backgroundColor: "#191919",
    borderRadius: 4,
  },
  consoleLine: { color: "#7FE787", fontFamily: mono, fontSize: 10 },
  sectionTitle: { fontSize: 10, fontWeight: "900", letterSpacing: 2, color: colors.textSecondary, marginBottom: 8 },
  emptyLine: { color: colors.textSecondary, fontSize: 12, textAlign: "center", padding: 20 },
  devCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 14,
    backgroundColor: "#FFF",
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    marginBottom: 8,
  },
  devCardActive: { borderColor: colors.fixed, borderLeftWidth: 4 },
  devIcon: { width: 40, height: 40, borderRadius: 6, alignItems: "center", justifyContent: "center" },
  devName: { fontSize: 15, fontWeight: "800", color: colors.text },
  devType: { fontSize: 11, fontFamily: mono, color: colors.textSecondary, marginTop: 2 },
  statusPill: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 4 },
  statusText: { fontSize: 10, fontWeight: "900", letterSpacing: 1 },
});
