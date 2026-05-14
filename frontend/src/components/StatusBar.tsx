import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { colors, mono } from "@/src/theme";

type FixType = "Single" | "DGPS" | "Float" | "Fixed" | "NoFix";

const FIX_COLORS: Record<FixType, string> = {
  Fixed: colors.fixed,
  Float: colors.float,
  DGPS: colors.dgps,
  Single: colors.single,
  NoFix: "#888888",
};

export type StatusInfo = {
  fix: FixType;
  satellites: number;
  hAcc: number;
  vAcc: number;
  battery: number;
};

export function useMockedStatus(): StatusInfo {
  const cycle: FixType[] = ["Single", "DGPS", "Float", "Fixed"];
  const [idx, setIdx] = useState(3);
  const [sats, setSats] = useState(18);
  const [hAcc, setHAcc] = useState(0.014);
  const [vAcc, setVAcc] = useState(0.022);
  const [battery, setBattery] = useState(86);

  useEffect(() => {
    const t = setInterval(() => {
      setIdx((i) => (i + 1) % cycle.length);
      setSats((s) => 14 + Math.floor(Math.random() * 10));
      setHAcc(0.008 + Math.random() * 0.03);
      setVAcc(0.012 + Math.random() * 0.05);
      setBattery((b) => Math.max(40, b - (Math.random() > 0.85 ? 1 : 0)));
    }, 4000);
    return () => clearInterval(t);
  }, []);

  return {
    fix: cycle[idx],
    satellites: sats,
    hAcc,
    vAcc,
    battery,
  };
}

export default function StatusBar({ status }: { status: StatusInfo }) {
  return (
    <View style={styles.bar} testID="status-bar">
      <View style={styles.left}>
        <View style={[styles.badge, { backgroundColor: FIX_COLORS[status.fix] }]}>
          <Text style={styles.badgeText} testID="status-rtk-badge">{status.fix.toUpperCase()}</Text>
        </View>
        <Text style={styles.metric}>SAT {status.satellites}</Text>
      </View>
      <View style={styles.right}>
        <Text style={styles.metric}>H {(status.hAcc * 100).toFixed(1)}cm</Text>
        <Text style={styles.metric}>V {(status.vAcc * 100).toFixed(1)}cm</Text>
        <Text style={styles.metric}>{status.battery}%</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    backgroundColor: colors.black,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  left: { flexDirection: "row", alignItems: "center", gap: 8 },
  right: { flexDirection: "row", alignItems: "center", gap: 12 },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 3 },
  badgeText: { color: "#FFF", fontSize: 10, fontWeight: "900", letterSpacing: 1 },
  metric: { color: "#FFF", fontFamily: mono, fontSize: 11, fontWeight: "600" },
});
