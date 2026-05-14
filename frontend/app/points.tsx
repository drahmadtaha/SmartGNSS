import React, { useEffect, useState, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Alert,
  TouchableOpacity,
  Share,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { api, type Point, type Project } from "@/src/api";
import { colors, mono } from "@/src/theme";
import { getActiveProjectId } from "@/src/activeProject";

export default function Points() {
  const router = useRouter();
  const [project, setProject] = useState<Project | null>(null);
  const [points, setPoints] = useState<Point[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    const id = await getActiveProjectId();
    if (!id) {
      setProject(null);
      setPoints([]);
      return;
    }
    setLoading(true);
    try {
      const [p, list] = await Promise.all([api.getProject(id), api.listPoints(id)]);
      setProject(p);
      setPoints(list);
    } catch (e: any) {
      Alert.alert("Error", e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const remove = (pt: Point) => {
    Alert.alert("Delete point?", pt.name, [
      { text: "Cancel", style: "cancel" },
      {
        text: "Delete",
        style: "destructive",
        onPress: async () => {
          try {
            await api.deletePoint(pt.id);
            load();
          } catch (e: any) {
            Alert.alert("Error", e.message);
          }
        },
      },
    ]);
  };

  const exportCsv = async () => {
    if (!project) return;
    try {
      const csv = await api.exportCsv(project.id);
      await Share.share({ message: csv, title: `${project.name}.csv` });
    } catch (e: any) {
      Alert.alert("Export failed", e.message);
    }
  };

  const exportKml = async () => {
    if (!project) return;
    try {
      const kml = await api.exportKml(project.id);
      await Share.share({ message: kml, title: `${project.name}.kml` });
    } catch (e: any) {
      Alert.alert("Export failed", e.message);
    }
  };

  if (!project) {
    return (
      <SafeAreaView style={styles.safe} edges={["bottom"]}>
        <View style={styles.empty}>
          <Ionicons name="folder-open-outline" size={48} color={colors.textDisabled} />
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
      <View style={styles.toolbar}>
        <View style={{ flex: 1 }}>
          <Text style={styles.title}>{project.name}</Text>
          <Text style={styles.meta}>
            {points.length} points • {project.coordinate_system}
          </Text>
        </View>
        <TouchableOpacity style={styles.iconBtn} onPress={exportCsv} testID="points-export-csv">
          <Ionicons name="document-text-outline" size={18} color="#FFF" />
          <Text style={styles.iconBtnText}>CSV</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.iconBtn, { marginLeft: 6, backgroundColor: colors.fixed }]} onPress={exportKml} testID="points-export-kml">
          <Ionicons name="earth-outline" size={18} color="#FFF" />
          <Text style={styles.iconBtnText}>KML</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.tableHeader}>
        <Text style={[styles.th, { flex: 1.4 }]}>NAME</Text>
        <Text style={[styles.th, { flex: 1 }]}>CODE</Text>
        <Text style={[styles.th, { flex: 2 }]}>LAT / LON</Text>
        <Text style={[styles.th, { flex: 0.8, textAlign: "right" }]}>ALT</Text>
        <Text style={[styles.th, { width: 32 }]}> </Text>
      </View>

      <FlatList
        data={points}
        keyExtractor={(p) => p.id}
        onRefresh={load}
        refreshing={loading}
        ListEmptyComponent={
          !loading ? (
            <View style={styles.empty}>
              <Ionicons name="locate-outline" size={48} color={colors.textDisabled} />
              <Text style={styles.emptyText}>No points yet</Text>
              <Text style={styles.emptySub}>Go to Survey to capture points</Text>
            </View>
          ) : null
        }
        renderItem={({ item }) => (
          <TouchableOpacity
            style={styles.row}
            onLongPress={() => remove(item)}
            activeOpacity={0.7}
            testID={`point-row-${item.name}`}
          >
            <Text style={[styles.td, { flex: 1.4, fontWeight: "800" }]}>{item.name}</Text>
            <Text style={[styles.td, { flex: 1 }]} numberOfLines={1}>
              {item.code || "—"}
            </Text>
            <View style={{ flex: 2 }}>
              <Text style={styles.td}>{item.latitude.toFixed(6)}</Text>
              <Text style={styles.td}>{item.longitude.toFixed(6)}</Text>
            </View>
            <Text style={[styles.td, { flex: 0.8, textAlign: "right" }]}>
              {item.altitude.toFixed(2)}
            </Text>
            <TouchableOpacity
              onPress={() => router.push({ pathname: "/stakeout", params: { id: item.id } })}
              style={styles.stakeBtn}
              hitSlop={6}
            >
              <Ionicons name="locate" size={16} color={colors.primary} />
            </TouchableOpacity>
          </TouchableOpacity>
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  toolbar: {
    flexDirection: "row",
    alignItems: "center",
    padding: 12,
    borderBottomWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  title: { fontSize: 16, fontWeight: "900", color: colors.text },
  meta: { fontSize: 11, color: colors.textSecondary, fontFamily: mono, marginTop: 2 },
  iconBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.primary,
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: 4,
  },
  iconBtnText: { color: "#FFF", fontWeight: "900", fontSize: 11, letterSpacing: 1 },
  tableHeader: {
    flexDirection: "row",
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: colors.surfaceAlt,
    borderBottomWidth: 2,
    borderColor: colors.black,
  },
  th: { fontSize: 10, fontWeight: "900", letterSpacing: 1, color: colors.text },
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderColor: colors.border,
    backgroundColor: "#FFF",
  },
  td: { fontSize: 12, color: colors.text, fontFamily: mono },
  stakeBtn: { width: 32, height: 32, alignItems: "center", justifyContent: "center" },
  empty: { alignItems: "center", paddingVertical: 60 },
  emptyText: { fontSize: 16, fontWeight: "700", color: colors.text, marginTop: 12 },
  emptySub: { fontSize: 13, color: colors.textSecondary, marginTop: 4 },
  openBtn: { marginTop: 16, paddingHorizontal: 18, paddingVertical: 12, backgroundColor: colors.primary, borderRadius: 6 },
  openBtnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1 },
});
