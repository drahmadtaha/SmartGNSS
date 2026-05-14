import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { api } from "@/src/api";
import { colors, mono } from "@/src/theme";
import * as Haptics from "expo-haptics";

type Device = { id: string; name: string; type: string; rssi: number };

export default function DeviceScreen() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [scanning, setScanning] = useState(false);
  const [connectedId, setConnectedId] = useState<string | null>(null);

  const scan = async () => {
    setScanning(true);
    setDevices([]);
    try {
      // Simulate scan delay
      await new Promise((r) => setTimeout(r, 1200));
      const res = await api.scanDevices();
      setDevices(res.devices);
    } catch (e: any) {
      Alert.alert("Scan failed", e.message);
    } finally {
      setScanning(false);
    }
  };

  useEffect(() => {
    scan();
  }, []);

  const toggle = (d: Device) => {
    Haptics.selectionAsync().catch(() => {});
    if (connectedId === d.id) {
      setConnectedId(null);
    } else {
      setConnectedId(d.id);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <View style={styles.header}>
        <View style={{ flex: 1 }}>
          <Text style={styles.title}>BLUETOOTH GNSS RECEIVERS</Text>
          <Text style={styles.sub}>
            {connectedId ? "1 device connected" : "Not connected — using phone GPS"}
          </Text>
        </View>
        <TouchableOpacity style={styles.scanBtn} onPress={scan} disabled={scanning} testID="device-scan-btn">
          {scanning ? <ActivityIndicator color="#FFF" /> : <Ionicons name="refresh" size={18} color="#FFF" />}
          <Text style={styles.scanBtnText}>{scanning ? "SCANNING" : "SCAN"}</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.notice}>
        <Ionicons name="information-circle" size={16} color={colors.primary} />
        <Text style={styles.noticeText}>
          Real GNSS receivers (CHC, South, Stonex, Hi-Target, Sokkia, Leica...) connect via Bluetooth NMEA on installed builds. Phone GPS is used as fallback.
        </Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: 12 }}>
        {devices.map((d) => {
          const connected = connectedId === d.id;
          const bars = d.rssi > -60 ? 4 : d.rssi > -70 ? 3 : d.rssi > -80 ? 2 : 1;
          return (
            <TouchableOpacity
              key={d.id}
              style={[styles.devCard, connected && styles.devCardActive]}
              onPress={() => toggle(d)}
              testID={`device-${d.id}`}
            >
              <View style={[styles.devIcon, { backgroundColor: connected ? colors.fixed : colors.black }]}>
                <Ionicons name={d.type === "Total Station" ? "telescope" : "radio"} size={20} color="#FFF" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.devName}>{d.name}</Text>
                <Text style={styles.devType}>
                  {d.type} • RSSI {d.rssi} dBm • {"▮".repeat(bars)}
                </Text>
              </View>
              <View style={[styles.statusPill, { backgroundColor: connected ? colors.fixed : colors.surfaceAlt }]}>
                <Text style={[styles.statusText, { color: connected ? "#FFF" : colors.text }]}>
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
  notice: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    padding: 10,
    backgroundColor: "#EAF2FE",
    borderBottomWidth: 1,
    borderColor: "#C7DBFA",
  },
  noticeText: { color: colors.text, fontSize: 11, flex: 1, lineHeight: 16 },
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
  devIcon: {
    width: 40,
    height: 40,
    borderRadius: 6,
    alignItems: "center",
    justifyContent: "center",
  },
  devName: { fontSize: 15, fontWeight: "800", color: colors.text },
  devType: { fontSize: 11, fontFamily: mono, color: colors.textSecondary, marginTop: 2 },
  statusPill: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 4 },
  statusText: { fontSize: 10, fontWeight: "900", letterSpacing: 1 },
});
