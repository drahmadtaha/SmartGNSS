import React, { useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import * as Location from "expo-location";
import { useLocalSearchParams, useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import StatusBar, { useMockedStatus } from "@/src/components/StatusBar";
import { colors, mono } from "@/src/theme";
import { api, type Point } from "@/src/api";
import { getActiveProjectId } from "@/src/activeProject";

function haversine(a: { lat: number; lon: number }, b: { lat: number; lon: number }) {
  const R = 6371000;
  const toRad = (x: number) => (x * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLon = toRad(b.lon - a.lon);
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
}

function bearing(a: { lat: number; lon: number }, b: { lat: number; lon: number }) {
  const toRad = (x: number) => (x * Math.PI) / 180;
  const toDeg = (x: number) => (x * 180) / Math.PI;
  const phi1 = toRad(a.lat);
  const phi2 = toRad(b.lat);
  const dLon = toRad(b.lon - a.lon);
  const y = Math.sin(dLon) * Math.cos(phi2);
  const x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
  return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

export default function Stakeout() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const status = useMockedStatus();
  const [points, setPoints] = useState<Point[]>([]);
  const [target, setTarget] = useState<Point | null>(null);
  const [loc, setLoc] = useState<Location.LocationObject | null>(null);
  const subRef = useRef<Location.LocationSubscription | null>(null);

  useEffect(() => {
    (async () => {
      const pid = await getActiveProjectId();
      if (!pid) return;
      try {
        const list = await api.listPoints(pid);
        setPoints(list);
        if (id) {
          const t = list.find((p) => p.id === id);
          if (t) setTarget(t);
        }
      } catch (e: any) {
        Alert.alert("Error", e.message);
      }
    })();
  }, [id]);

  useEffect(() => {
    (async () => {
      const { status: s } = await Location.requestForegroundPermissionsAsync();
      if (s !== "granted") return;
      const sub = await Location.watchPositionAsync(
        { accuracy: Location.Accuracy.BestForNavigation, timeInterval: 1000, distanceInterval: 0 },
        (pos) => setLoc(pos)
      );
      subRef.current = sub;
    })();
    return () => subRef.current?.remove();
  }, []);

  const distance =
    target && loc
      ? haversine(
          { lat: loc.coords.latitude, lon: loc.coords.longitude },
          { lat: target.latitude, lon: target.longitude }
        )
      : null;

  const brg =
    target && loc
      ? bearing(
          { lat: loc.coords.latitude, lon: loc.coords.longitude },
          { lat: target.latitude, lon: target.longitude }
        )
      : null;

  const heading = loc?.coords.heading ?? 0;
  const relative = brg != null ? (brg - heading + 360) % 360 : 0;
  const cardinal = brg != null ? compass(brg) : "—";

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <StatusBar status={status} />

      {!target ? (
        <View style={{ flex: 1 }}>
          <Text style={styles.pickerTitle}>SELECT TARGET POINT</Text>
          <ScrollView contentContainerStyle={{ padding: 12 }}>
            {points.length === 0 ? (
              <View style={styles.empty}>
                <Ionicons name="locate-outline" size={48} color={colors.textDisabled} />
                <Text style={styles.emptyText}>No points to stake out</Text>
                <TouchableOpacity style={styles.openBtn} onPress={() => router.push("/survey")}>
                  <Text style={styles.openBtnText}>SURVEY POINTS</Text>
                </TouchableOpacity>
              </View>
            ) : (
              points.map((p) => (
                <TouchableOpacity
                  key={p.id}
                  style={styles.pickRow}
                  onPress={() => setTarget(p)}
                  testID={`stakeout-pick-${p.name}`}
                >
                  <View style={styles.pickIcon}>
                    <Ionicons name="flag" size={18} color="#FFF" />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.pickName}>{p.name}</Text>
                    <Text style={styles.pickCoords}>
                      {p.latitude.toFixed(6)}, {p.longitude.toFixed(6)}
                    </Text>
                  </View>
                  <Ionicons name="chevron-forward" size={20} color={colors.textSecondary} />
                </TouchableOpacity>
              ))
            )}
          </ScrollView>
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.scroll}>
          <View style={styles.targetCard}>
            <View style={{ flex: 1 }}>
              <Text style={styles.targetLabel}>TARGET</Text>
              <Text style={styles.targetName}>{target.name}</Text>
              <Text style={styles.targetCoords}>
                {target.latitude.toFixed(6)}, {target.longitude.toFixed(6)}
              </Text>
            </View>
            <TouchableOpacity onPress={() => setTarget(null)} testID="stakeout-change">
              <Text style={styles.changeLink}>CHANGE</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.bullseye}>
            <View style={styles.ring1} />
            <View style={styles.ring2} />
            <View style={styles.ring3} />
            <View
              style={[
                styles.arrow,
                { transform: [{ rotate: `${relative}deg` }] },
              ]}
            >
              <Ionicons name="navigate" size={60} color={colors.primary} />
            </View>
            <View style={styles.center} />
          </View>

          <View style={styles.metricsRow}>
            <View style={styles.metric}>
              <Text style={styles.mLabel}>DISTANCE</Text>
              <Text style={styles.mVal}>
                {distance != null ? distance.toFixed(2) : "—"}
                <Text style={styles.mUnit}> m</Text>
              </Text>
            </View>
            <View style={styles.metric}>
              <Text style={styles.mLabel}>BEARING</Text>
              <Text style={styles.mVal}>
                {brg != null ? brg.toFixed(1) : "—"}
                <Text style={styles.mUnit}>°</Text>
              </Text>
              <Text style={styles.mSub}>{cardinal}</Text>
            </View>
          </View>

          <View style={styles.guidance}>
            <Guide label="N/S" value={target && loc ? offset(loc.coords.latitude, target.latitude, "lat", target.latitude) : "—"} />
            <Guide label="E/W" value={target && loc ? offset(loc.coords.longitude, target.longitude, "lon", target.latitude) : "—"} />
            <Guide label="Δ ALT" value={target && loc?.coords.altitude != null ? `${(target.altitude - loc.coords.altitude).toFixed(2)} m` : "—"} />
          </View>

          {distance != null && distance < 0.05 && (
            <View style={styles.success}>
              <Ionicons name="checkmark-circle" size={24} color="#FFF" />
              <Text style={styles.successText}>ON TARGET</Text>
            </View>
          )}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

function compass(deg: number): string {
  const dirs = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
  return dirs[Math.round(deg / 45) % 8];
}

function offset(curr: number, target: number, kind: "lat" | "lon", refLat: number): string {
  const diff = target - curr;
  const R = 6371000;
  const m =
    kind === "lat"
      ? (diff * Math.PI * R) / 180
      : (diff * Math.PI * R * Math.cos((refLat * Math.PI) / 180)) / 180;
  const dir =
    kind === "lat" ? (m >= 0 ? "N" : "S") : m >= 0 ? "E" : "W";
  return `${Math.abs(m).toFixed(2)} m ${dir}`;
}

function Guide({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.gItem}>
      <Text style={styles.gLabel}>{label}</Text>
      <Text style={styles.gVal}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  pickerTitle: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 12,
    fontWeight: "900",
    letterSpacing: 2,
    backgroundColor: colors.surface,
    color: colors.textSecondary,
    borderBottomWidth: 1,
    borderColor: colors.border,
  },
  empty: { alignItems: "center", paddingVertical: 60 },
  emptyText: { fontSize: 16, fontWeight: "700", color: colors.text, marginTop: 12 },
  openBtn: { marginTop: 16, paddingHorizontal: 18, paddingVertical: 12, backgroundColor: colors.primary, borderRadius: 6 },
  openBtnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1 },
  pickRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    marginBottom: 8,
    backgroundColor: "#FFF",
  },
  pickIcon: {
    width: 36,
    height: 36,
    backgroundColor: colors.primary,
    borderRadius: 6,
    alignItems: "center",
    justifyContent: "center",
  },
  pickName: { fontSize: 15, fontWeight: "800", color: colors.text },
  pickCoords: { fontSize: 11, fontFamily: mono, color: colors.textSecondary, marginTop: 2 },
  scroll: { padding: 14 },
  targetCard: {
    flexDirection: "row",
    alignItems: "center",
    padding: 12,
    borderWidth: 1,
    borderColor: colors.primary,
    borderLeftWidth: 4,
    borderRadius: 6,
    backgroundColor: colors.surface,
    marginBottom: 18,
  },
  targetLabel: { fontSize: 10, fontWeight: "900", letterSpacing: 2, color: colors.primary },
  targetName: { fontSize: 18, fontWeight: "900", color: colors.text, marginTop: 2 },
  targetCoords: { fontSize: 11, fontFamily: mono, color: colors.textSecondary, marginTop: 2 },
  changeLink: { fontSize: 12, fontWeight: "900", color: colors.primary, letterSpacing: 1 },
  bullseye: {
    aspectRatio: 1,
    width: "80%",
    alignSelf: "center",
    alignItems: "center",
    justifyContent: "center",
    marginVertical: 16,
  },
  ring1: { position: "absolute", width: "100%", height: "100%", borderRadius: 9999, borderWidth: 2, borderColor: colors.border },
  ring2: { position: "absolute", width: "66%", height: "66%", borderRadius: 9999, borderWidth: 2, borderColor: colors.border },
  ring3: { position: "absolute", width: "33%", height: "33%", borderRadius: 9999, borderWidth: 2, borderColor: colors.primary },
  arrow: { position: "absolute" },
  center: { position: "absolute", width: 12, height: 12, borderRadius: 6, backgroundColor: colors.single },
  metricsRow: { flexDirection: "row", gap: 10, marginTop: 6 },
  metric: {
    flex: 1,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    borderRadius: 6,
    alignItems: "center",
  },
  mLabel: { fontSize: 10, fontWeight: "900", letterSpacing: 2, color: colors.textSecondary },
  mVal: { fontSize: 32, fontWeight: "900", color: colors.primary, fontFamily: mono, marginTop: 4 },
  mUnit: { fontSize: 16, color: colors.text },
  mSub: { fontSize: 11, color: colors.textSecondary, marginTop: 2, fontWeight: "700", letterSpacing: 1 },
  guidance: {
    flexDirection: "row",
    backgroundColor: colors.black,
    borderRadius: 6,
    padding: 12,
    marginTop: 12,
  },
  gItem: { flex: 1, alignItems: "center" },
  gLabel: { fontSize: 10, fontWeight: "900", letterSpacing: 1.5, color: "#9E9E9E" },
  gVal: { fontSize: 14, fontFamily: mono, color: "#FFF", marginTop: 4, fontWeight: "700" },
  success: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    backgroundColor: colors.fixed,
    padding: 14,
    borderRadius: 6,
    marginTop: 14,
  },
  successText: { color: "#FFF", fontSize: 16, fontWeight: "900", letterSpacing: 2 },
});
