import React, { useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  Alert,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import * as Location from "expo-location";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import StatusBar, { useMockedStatus } from "@/src/components/StatusBar";
import { colors, mono } from "@/src/theme";
import { api, type Project } from "@/src/api";
import { getActiveProjectId } from "@/src/activeProject";

export default function Survey() {
  const router = useRouter();
  const status = useMockedStatus();
  const [project, setProject] = useState<Project | null>(null);
  const [loc, setLoc] = useState<Location.LocationObject | null>(null);
  const [permState, setPermState] = useState<"unknown" | "granted" | "denied">("unknown");
  const [name, setName] = useState("PT001");
  const [code, setCode] = useState("");
  const [antH, setAntH] = useState("2.000");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [autoSeq, setAutoSeq] = useState(1);
  const subRef = useRef<Location.LocationSubscription | null>(null);

  useEffect(() => {
    (async () => {
      const id = await getActiveProjectId();
      if (!id) return;
      try {
        const p = await api.getProject(id);
        setProject(p);
        setAutoSeq(p.point_count + 1);
        setName(`PT${String(p.point_count + 1).padStart(3, "0")}`);
      } catch (e) {
        console.warn(e);
      }
    })();
  }, []);

  useEffect(() => {
    (async () => {
      const { status: s } = await Location.requestForegroundPermissionsAsync();
      if (s !== "granted") {
        setPermState("denied");
        return;
      }
      setPermState("granted");
      const sub = await Location.watchPositionAsync(
        { accuracy: Location.Accuracy.BestForNavigation, timeInterval: 1000, distanceInterval: 0 },
        (pos) => setLoc(pos)
      );
      subRef.current = sub;
    })();
    return () => {
      subRef.current?.remove();
    };
  }, []);

  const save = async () => {
    if (!project) {
      Alert.alert("No active project", "Create or open a project first.");
      return;
    }
    if (!loc) {
      Alert.alert("No fix", "Waiting for GPS position...");
      return;
    }
    if (!name.trim()) {
      Alert.alert("Name required", "Enter a point name.");
      return;
    }
    setSaving(true);
    try {
      const pt = await api.createPoint({
        project_id: project.id,
        name: name.trim(),
        code: code.trim(),
        latitude: loc.coords.latitude,
        longitude: loc.coords.longitude,
        altitude: loc.coords.altitude ?? 0,
        accuracy_h: loc.coords.accuracy ?? 0,
        accuracy_v: (loc.coords as any).altitudeAccuracy ?? 0,
        antenna_height: parseFloat(antH) || 0,
        fix_type: status.fix,
        satellites: status.satellites,
        note: note.trim(),
      });
      const next = autoSeq + 1;
      setAutoSeq(next);
      setName(`PT${String(next).padStart(3, "0")}`);
      setCode("");
      setNote("");
      Alert.alert("Saved", `Point ${pt.name} stored.`);
    } catch (e: any) {
      Alert.alert("Error", e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <StatusBar status={status} />

      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        style={{ flex: 1 }}
      >
        <ScrollView contentContainerStyle={styles.scroll}>
          {!project && (
            <View style={styles.warnBox}>
              <Ionicons name="warning" size={18} color={colors.single} />
              <Text style={styles.warnText}>No active project</Text>
              <TouchableOpacity onPress={() => router.push("/project")}>
                <Text style={styles.warnLink}>OPEN</Text>
              </TouchableOpacity>
            </View>
          )}

          {permState === "denied" && (
            <View style={styles.warnBox}>
              <Ionicons name="alert-circle" size={18} color={colors.single} />
              <Text style={styles.warnText}>Location permission denied</Text>
            </View>
          )}

          <View style={styles.coordCard}>
            <Text style={styles.cardLabel}>CURRENT POSITION • WGS84</Text>
            <View style={styles.coordGrid}>
              <Coord label="LAT" value={loc ? loc.coords.latitude.toFixed(8) : "—"} />
              <Coord label="LON" value={loc ? loc.coords.longitude.toFixed(8) : "—"} />
              <Coord label="ALT (m)" value={loc?.coords.altitude != null ? loc.coords.altitude.toFixed(3) : "—"} />
              <Coord label="ACC (m)" value={loc?.coords.accuracy != null ? loc.coords.accuracy.toFixed(2) : "—"} />
              <Coord label="SPEED (m/s)" value={loc?.coords.speed != null ? loc.coords.speed.toFixed(2) : "—"} />
              <Coord label="HEAD (°)" value={loc?.coords.heading != null ? loc.coords.heading.toFixed(0) : "—"} />
            </View>
          </View>

          <View style={styles.form}>
            <View style={styles.row}>
              <View style={{ flex: 1 }}>
                <Text style={styles.flabel}>POINT NAME</Text>
                <TextInput
                  testID="survey-name-input"
                  value={name}
                  onChangeText={setName}
                  style={styles.input}
                />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.flabel}>CODE</Text>
                <TextInput
                  testID="survey-code-input"
                  value={code}
                  onChangeText={setCode}
                  placeholder="TREE, FENCE..."
                  placeholderTextColor={colors.textDisabled}
                  style={styles.input}
                />
              </View>
            </View>
            <View>
              <Text style={styles.flabel}>ANTENNA HEIGHT (m)</Text>
              <TextInput
                testID="survey-anth-input"
                value={antH}
                onChangeText={setAntH}
                keyboardType="decimal-pad"
                style={styles.input}
              />
            </View>
            <View>
              <Text style={styles.flabel}>NOTE</Text>
              <TextInput
                testID="survey-note-input"
                value={note}
                onChangeText={setNote}
                placeholder="optional"
                placeholderTextColor={colors.textDisabled}
                style={styles.input}
              />
            </View>
          </View>
        </ScrollView>

        <TouchableOpacity
          style={[styles.measure, (!project || !loc || saving) && { opacity: 0.55 }]}
          onPress={save}
          disabled={!project || !loc || saving}
          activeOpacity={0.85}
          testID="survey-measure-button"
        >
          <Ionicons name="radio-button-on" size={28} color="#FFF" />
          <Text style={styles.measureText}>{saving ? "SAVING..." : "MEASURE & STORE"}</Text>
        </TouchableOpacity>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function Coord({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.coordItem}>
      <Text style={styles.coordLabel}>{label}</Text>
      <Text style={styles.coordValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: 14, paddingBottom: 24 },
  warnBox: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    padding: 10,
    backgroundColor: "#FFF3F3",
    borderWidth: 1,
    borderColor: colors.single,
    borderRadius: 6,
    marginBottom: 10,
  },
  warnText: { color: colors.single, fontWeight: "800", flex: 1 },
  warnLink: { color: colors.single, fontWeight: "900", letterSpacing: 1, fontSize: 12 },
  coordCard: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    padding: 12,
    marginBottom: 14,
  },
  cardLabel: { fontSize: 10, fontWeight: "900", letterSpacing: 1.5, color: colors.textSecondary, marginBottom: 8 },
  coordGrid: { flexDirection: "row", flexWrap: "wrap" },
  coordItem: { width: "33.333%", paddingVertical: 6 },
  coordLabel: { fontSize: 9, color: colors.textSecondary, fontWeight: "700", letterSpacing: 1 },
  coordValue: { fontSize: 14, fontFamily: mono, fontWeight: "700", color: colors.text, marginTop: 2 },
  form: { gap: 10 },
  row: { flexDirection: "row", gap: 10 },
  flabel: { fontSize: 10, fontWeight: "900", letterSpacing: 1.5, color: colors.textSecondary, marginBottom: 5 },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: "#FFF",
    borderRadius: 6,
    padding: 12,
    fontSize: 15,
    color: colors.text,
    fontFamily: mono,
  },
  measure: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    backgroundColor: colors.primary,
    margin: 14,
    height: 64,
    borderRadius: 6,
  },
  measureText: { color: "#FFF", fontSize: 17, fontWeight: "900", letterSpacing: 1.5 },
});
