import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TextInput,
  TouchableOpacity,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { api, type Point } from "@/src/api";
import { colors, mono } from "@/src/theme";
import { getActiveProjectId } from "@/src/activeProject";

type Tab = "inverse" | "area";

export default function Cogo() {
  const [tab, setTab] = useState<Tab>("inverse");
  const [points, setPoints] = useState<Point[]>([]);

  // inverse inputs
  const [lat1, setLat1] = useState("");
  const [lon1, setLon1] = useState("");
  const [lat2, setLat2] = useState("");
  const [lon2, setLon2] = useState("");
  const [invResult, setInvResult] = useState<{ distance_m: number; bearing_deg: number } | null>(null);

  // area inputs
  const [vertices, setVertices] = useState<{ lat: string; lon: string }[]>([
    { lat: "", lon: "" },
    { lat: "", lon: "" },
    { lat: "", lon: "" },
  ]);
  const [areaResult, setAreaResult] = useState<{ area_m2: number; perimeter_m: number } | null>(null);

  useEffect(() => {
    (async () => {
      const id = await getActiveProjectId();
      if (!id) return;
      try {
        const list = await api.listPoints(id);
        setPoints(list);
      } catch {}
    })();
  }, []);

  const fillFromPoint = (slot: 1 | 2, p: Point) => {
    if (slot === 1) {
      setLat1(String(p.latitude));
      setLon1(String(p.longitude));
    } else {
      setLat2(String(p.latitude));
      setLon2(String(p.longitude));
    }
  };

  const computeInverse = async () => {
    try {
      const r = await api.cogoInverse({
        lat1: parseFloat(lat1),
        lon1: parseFloat(lon1),
        lat2: parseFloat(lat2),
        lon2: parseFloat(lon2),
      });
      setInvResult(r);
    } catch (e: any) {
      Alert.alert("Error", e.message);
    }
  };

  const computeArea = async () => {
    try {
      const pts = vertices
        .map((v) => ({ lat: parseFloat(v.lat), lon: parseFloat(v.lon) }))
        .filter((v) => !Number.isNaN(v.lat) && !Number.isNaN(v.lon));
      if (pts.length < 3) {
        Alert.alert("At least 3 vertices required");
        return;
      }
      const r = await api.cogoArea({ points: pts });
      setAreaResult(r);
    } catch (e: any) {
      Alert.alert("Error", e.message);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <View style={styles.tabs}>
        <TabBtn label="INVERSE" active={tab === "inverse"} onPress={() => setTab("inverse")} testID="cogo-tab-inverse" />
        <TabBtn label="AREA" active={tab === "area"} onPress={() => setTab("area")} testID="cogo-tab-area" />
      </View>

      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={{ flex: 1 }}
      >
        <ScrollView contentContainerStyle={styles.scroll}>
          {tab === "inverse" ? (
            <>
              <Text style={styles.section}>POINT A</Text>
              <View style={styles.row}>
                <Field label="LAT" value={lat1} onChange={setLat1} testID="cogo-a-lat" />
                <Field label="LON" value={lon1} onChange={setLon1} testID="cogo-a-lon" />
              </View>
              {points.length > 0 && <PickerRow points={points} onPick={(p) => fillFromPoint(1, p)} />}

              <Text style={styles.section}>POINT B</Text>
              <View style={styles.row}>
                <Field label="LAT" value={lat2} onChange={setLat2} testID="cogo-b-lat" />
                <Field label="LON" value={lon2} onChange={setLon2} testID="cogo-b-lon" />
              </View>
              {points.length > 0 && <PickerRow points={points} onPick={(p) => fillFromPoint(2, p)} />}

              <TouchableOpacity style={styles.compute} onPress={computeInverse} testID="cogo-compute-inverse">
                <Ionicons name="git-compare-outline" size={20} color="#FFF" />
                <Text style={styles.computeText}>COMPUTE</Text>
              </TouchableOpacity>

              {invResult && (
                <View style={styles.result}>
                  <ResultRow label="DISTANCE" value={`${invResult.distance_m.toFixed(3)} m`} />
                  <ResultRow label="BEARING" value={`${invResult.bearing_deg.toFixed(4)} °`} />
                </View>
              )}
            </>
          ) : (
            <>
              <Text style={styles.section}>POLYGON VERTICES</Text>
              {vertices.map((v, idx) => (
                <View key={idx} style={styles.vRow}>
                  <Text style={styles.vIdx}>{String(idx + 1).padStart(2, "0")}</Text>
                  <TextInput
                    placeholder="latitude"
                    placeholderTextColor={colors.textDisabled}
                    style={styles.vInput}
                    value={v.lat}
                    keyboardType="decimal-pad"
                    onChangeText={(t) => {
                      const next = [...vertices];
                      next[idx].lat = t;
                      setVertices(next);
                    }}
                  />
                  <TextInput
                    placeholder="longitude"
                    placeholderTextColor={colors.textDisabled}
                    style={styles.vInput}
                    value={v.lon}
                    keyboardType="decimal-pad"
                    onChangeText={(t) => {
                      const next = [...vertices];
                      next[idx].lon = t;
                      setVertices(next);
                    }}
                  />
                  <TouchableOpacity
                    onPress={() => {
                      const next = vertices.filter((_, i) => i !== idx);
                      setVertices(next.length ? next : [{ lat: "", lon: "" }]);
                    }}
                    hitSlop={6}
                  >
                    <Ionicons name="close-circle" size={20} color={colors.single} />
                  </TouchableOpacity>
                </View>
              ))}
              <View style={{ flexDirection: "row", gap: 8 }}>
                <TouchableOpacity
                  style={[styles.addV, { flex: 1 }]}
                  onPress={() => setVertices([...vertices, { lat: "", lon: "" }])}
                >
                  <Ionicons name="add" size={16} color={colors.primary} />
                  <Text style={styles.addVText}>ADD VERTEX</Text>
                </TouchableOpacity>
                {points.length > 0 && (
                  <TouchableOpacity
                    style={[styles.addV, { flex: 1, borderColor: colors.black }]}
                    onPress={() => {
                      setVertices(points.map((p) => ({ lat: String(p.latitude), lon: String(p.longitude) })));
                    }}
                  >
                    <Ionicons name="layers-outline" size={16} color={colors.black} />
                    <Text style={[styles.addVText, { color: colors.black }]}>FROM PROJECT</Text>
                  </TouchableOpacity>
                )}
              </View>

              <TouchableOpacity style={styles.compute} onPress={computeArea} testID="cogo-compute-area">
                <Ionicons name="resize-outline" size={20} color="#FFF" />
                <Text style={styles.computeText}>COMPUTE AREA</Text>
              </TouchableOpacity>

              {areaResult && (
                <View style={styles.result}>
                  <ResultRow label="AREA" value={`${areaResult.area_m2.toLocaleString()} m²`} />
                  <ResultRow label="AREA (Ha)" value={`${(areaResult.area_m2 / 10000).toFixed(4)} ha`} />
                  <ResultRow label="PERIMETER" value={`${areaResult.perimeter_m.toFixed(3)} m`} />
                </View>
              )}
            </>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function TabBtn({ label, active, onPress, testID }: { label: string; active: boolean; onPress: () => void; testID: string }) {
  return (
    <TouchableOpacity onPress={onPress} style={[styles.tab, active && styles.tabActive]} testID={testID}>
      <Text style={[styles.tabText, active && { color: "#FFF" }]}>{label}</Text>
    </TouchableOpacity>
  );
}

function Field({ label, value, onChange, testID }: { label: string; value: string; onChange: (v: string) => void; testID?: string }) {
  return (
    <View style={{ flex: 1 }}>
      <Text style={styles.flabel}>{label}</Text>
      <TextInput
        value={value}
        onChangeText={onChange}
        keyboardType="decimal-pad"
        style={styles.fInput}
        testID={testID}
        placeholder="0.000000"
        placeholderTextColor={colors.textDisabled}
      />
    </View>
  );
}

function ResultRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.rRow}>
      <Text style={styles.rLabel}>{label}</Text>
      <Text style={styles.rVal}>{value}</Text>
    </View>
  );
}

function PickerRow({ points, onPick }: { points: Point[]; onPick: (p: Point) => void }) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginTop: 8 }}>
      {points.slice(0, 20).map((p) => (
        <TouchableOpacity key={p.id} style={styles.chip} onPress={() => onPick(p)}>
          <Ionicons name="locate-outline" size={12} color={colors.primary} />
          <Text style={styles.chipText}>{p.name}</Text>
        </TouchableOpacity>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  tabs: { flexDirection: "row", backgroundColor: colors.surface, borderBottomWidth: 1, borderColor: colors.border },
  tab: { flex: 1, paddingVertical: 14, alignItems: "center" },
  tabActive: { backgroundColor: colors.black },
  tabText: { fontWeight: "900", letterSpacing: 1.5, fontSize: 12, color: colors.text },
  scroll: { padding: 14, paddingBottom: 40 },
  section: { fontSize: 11, fontWeight: "900", letterSpacing: 2, color: colors.textSecondary, marginTop: 12, marginBottom: 8 },
  row: { flexDirection: "row", gap: 10 },
  flabel: { fontSize: 10, fontWeight: "900", letterSpacing: 1.5, color: colors.textSecondary, marginBottom: 5 },
  fInput: {
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: "#FFF",
    borderRadius: 6,
    padding: 12,
    fontFamily: mono,
    color: colors.text,
  },
  chip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 4,
    marginRight: 6,
    backgroundColor: "#FFF",
  },
  chipText: { fontSize: 11, fontFamily: mono, color: colors.text, fontWeight: "700" },
  compute: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    backgroundColor: colors.primary,
    paddingVertical: 16,
    borderRadius: 6,
    marginTop: 18,
  },
  computeText: { color: "#FFF", fontWeight: "900", letterSpacing: 2 },
  result: { marginTop: 16, borderWidth: 1, borderColor: colors.black, borderRadius: 6, overflow: "hidden" },
  rRow: { flexDirection: "row", justifyContent: "space-between", padding: 14, borderBottomWidth: 1, borderColor: colors.border, backgroundColor: "#FFF" },
  rLabel: { fontSize: 11, fontWeight: "900", letterSpacing: 2, color: colors.textSecondary },
  rVal: { fontSize: 16, fontFamily: mono, fontWeight: "800", color: colors.primary },
  vRow: { flexDirection: "row", alignItems: "center", gap: 6, marginBottom: 6 },
  vIdx: { fontFamily: mono, fontWeight: "900", color: colors.textSecondary, width: 28 },
  vInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: "#FFF",
    borderRadius: 4,
    padding: 10,
    fontFamily: mono,
    color: colors.text,
  },
  addV: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    paddingVertical: 10,
    borderWidth: 1,
    borderColor: colors.primary,
    borderRadius: 4,
    borderStyle: "dashed",
    marginTop: 8,
  },
  addVText: { fontSize: 11, fontWeight: "900", letterSpacing: 1, color: colors.primary },
});
