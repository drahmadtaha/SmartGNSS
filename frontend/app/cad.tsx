import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import * as DocumentPicker from "expo-document-picker";
import { useRouter } from "expo-router";
import { api, type CadOverlay } from "@/src/api";
import { colors, mono } from "@/src/theme";
import { getActiveProjectId } from "@/src/activeProject";

export default function CadScreen() {
  const router = useRouter();
  const [projectId, setProjectId] = useState<string | null>(null);
  const [overlays, setOverlays] = useState<CadOverlay[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

  const load = async () => {
    const id = await getActiveProjectId();
    setProjectId(id);
    if (!id) {
      setOverlays([]);
      return;
    }
    setLoading(true);
    try {
      const list = await api.listCad(id);
      setOverlays(list);
    } catch (e: any) {
      Alert.alert("Error", e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const pickAndUpload = async (kind: "dxf" | "shp") => {
    if (!projectId) {
      Alert.alert("No active project", "Open a project first.");
      return;
    }
    try {
      const accept =
        kind === "dxf"
          ? ["application/dxf", "*/*"]
          : ["application/octet-stream", "*/*"];
      const result = await DocumentPicker.getDocumentAsync({
        multiple: false,
        type: accept,
        copyToCacheDirectory: true,
      });
      if (result.canceled || !result.assets?.[0]) return;
      const f = result.assets[0];
      const lower = f.name.toLowerCase();
      if (kind === "dxf" && !lower.endsWith(".dxf")) {
        Alert.alert("Wrong file", "Please choose a .dxf file.");
        return;
      }
      if (kind === "shp" && !lower.endsWith(".shp")) {
        Alert.alert("Wrong file", "Please choose a .shp file (.dbf / .shx are optional companions).");
        return;
      }
      setUploading(true);
      let extras: any = undefined;
      if (kind === "shp") {
        const dbf = await DocumentPicker.getDocumentAsync({
          multiple: false,
          type: "*/*",
          copyToCacheDirectory: true,
        });
        if (!dbf.canceled && dbf.assets?.[0]) {
          extras = {
            dbf: { uri: dbf.assets[0].uri, name: dbf.assets[0].name },
          };
        }
      }
      const overlay = await api.uploadCad(
        projectId,
        { uri: f.uri, name: f.name, mimeType: f.mimeType },
        extras
      );
      Alert.alert("Uploaded", `${overlay.filename} • ${overlay.feature_count} features`);
      load();
    } catch (e: any) {
      Alert.alert("Upload failed", e.message);
    } finally {
      setUploading(false);
    }
  };

  const remove = (o: CadOverlay) => {
    Alert.alert("Delete overlay?", o.filename, [
      { text: "Cancel", style: "cancel" },
      {
        text: "Delete",
        style: "destructive",
        onPress: async () => {
          try {
            await api.deleteCad(o.id);
            load();
          } catch (e: any) {
            Alert.alert("Error", e.message);
          }
        },
      },
    ]);
  };

  if (!projectId) {
    return (
      <SafeAreaView style={styles.safe} edges={["bottom"]}>
        <View style={styles.empty}>
          <Ionicons name="layers-outline" size={48} color={colors.textDisabled} />
          <Text style={styles.emptyText}>No active project</Text>
          <TouchableOpacity style={styles.openBtn} onPress={() => router.push("/project")}>
            <Text style={styles.openBtnText}>OPEN A PROJECT</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <View style={styles.actions}>
        <TouchableOpacity
          style={[styles.actionBtn, { backgroundColor: colors.primary }]}
          onPress={() => pickAndUpload("dxf")}
          disabled={uploading}
          testID="cad-import-dxf"
        >
          {uploading ? <ActivityIndicator color="#FFF" /> : <Ionicons name="cube-outline" size={18} color="#FFF" />}
          <Text style={styles.actionText}>IMPORT DXF</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.actionBtn, { backgroundColor: colors.black }]}
          onPress={() => pickAndUpload("shp")}
          disabled={uploading}
          testID="cad-import-shp"
        >
          {uploading ? <ActivityIndicator color="#FFF" /> : <Ionicons name="map-outline" size={18} color="#FFF" />}
          <Text style={styles.actionText}>IMPORT SHP</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.notice}>
        <Ionicons name="information-circle" size={16} color={colors.primary} />
        <Text style={styles.noticeText}>
          DXF/SHP are parsed to GeoJSON and overlaid on the Map. Files must already be in
          WGS84 lon/lat to align with collected points. Max 10 MB per upload.
        </Text>
      </View>

      <ScrollView
        contentContainerStyle={{ padding: 12 }}
        refreshControl={undefined}
      >
        <Text style={styles.section}>{overlays.length} OVERLAY{overlays.length === 1 ? "" : "S"}</Text>
        {loading && <ActivityIndicator color={colors.primary} />}
        {!loading && overlays.length === 0 && (
          <Text style={styles.emptyLine}>No CAD overlays. Tap IMPORT DXF or IMPORT SHP.</Text>
        )}
        {overlays.map((o) => (
          <View key={o.id} style={styles.card}>
            <View
              style={[
                styles.cardIcon,
                { backgroundColor: o.file_type === "dxf" ? colors.primary : colors.black },
              ]}
            >
              <Text style={styles.cardIconText}>{o.file_type.toUpperCase()}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.cardName}>{o.filename}</Text>
              <Text style={styles.cardMeta}>
                {o.feature_count} features • {o.created_at.slice(0, 19).replace("T", " ")}
              </Text>
              {o.bounds && (
                <Text style={styles.cardBounds}>
                  Bounds {o.bounds[0].toFixed(4)},{o.bounds[1].toFixed(4)} → {o.bounds[2].toFixed(4)},{o.bounds[3].toFixed(4)}
                </Text>
              )}
            </View>
            <TouchableOpacity onPress={() => remove(o)} hitSlop={8} testID={`cad-delete-${o.id}`}>
              <Ionicons name="trash-outline" size={20} color={colors.single} />
            </TouchableOpacity>
          </View>
        ))}

        {overlays.length > 0 && (
          <TouchableOpacity
            style={styles.viewMap}
            onPress={() => router.push("/map")}
            testID="cad-view-map"
          >
            <Ionicons name="map" size={18} color="#FFF" />
            <Text style={styles.viewMapText}>VIEW ON MAP</Text>
          </TouchableOpacity>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  actions: { flexDirection: "row", gap: 10, padding: 12 },
  actionBtn: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    paddingVertical: 14,
    borderRadius: 6,
  },
  actionText: { color: "#FFF", fontWeight: "900", letterSpacing: 1.5 },
  notice: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    padding: 10,
    marginHorizontal: 12,
    backgroundColor: "#EAF2FE",
    borderWidth: 1,
    borderColor: "#C7DBFA",
    borderRadius: 6,
  },
  noticeText: { color: colors.text, fontSize: 11, flex: 1, lineHeight: 16 },
  section: { fontSize: 10, fontWeight: "900", letterSpacing: 2, color: colors.textSecondary, marginBottom: 8 },
  emptyLine: { color: colors.textSecondary, fontSize: 12, textAlign: "center", padding: 20 },
  card: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 12,
    backgroundColor: "#FFF",
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    marginBottom: 8,
  },
  cardIcon: {
    width: 48,
    height: 48,
    borderRadius: 6,
    alignItems: "center",
    justifyContent: "center",
  },
  cardIconText: { color: "#FFF", fontWeight: "900", fontSize: 12 },
  cardName: { fontSize: 15, fontWeight: "800", color: colors.text },
  cardMeta: { fontSize: 11, color: colors.textSecondary, fontFamily: mono, marginTop: 2 },
  cardBounds: { fontSize: 10, color: colors.textSecondary, fontFamily: mono, marginTop: 2 },
  viewMap: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    padding: 14,
    backgroundColor: colors.primary,
    borderRadius: 6,
    marginTop: 12,
  },
  viewMapText: { color: "#FFF", fontWeight: "900", letterSpacing: 1.5 },
  empty: { flex: 1, alignItems: "center", justifyContent: "center" },
  emptyText: { fontSize: 16, fontWeight: "700", color: colors.text, marginTop: 12 },
  openBtn: {
    marginTop: 16,
    paddingHorizontal: 18,
    paddingVertical: 12,
    backgroundColor: colors.primary,
    borderRadius: 6,
  },
  openBtnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1 },
});
