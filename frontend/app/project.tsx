import React, { useEffect, useState, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  FlatList,
  Alert,
  Modal,
  KeyboardAvoidingView,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { api, type Project } from "@/src/api";
import { colors, mono } from "@/src/theme";
import { getActiveProjectId, setActiveProjectId } from "@/src/activeProject";

export default function ProjectScreen() {
  const router = useRouter();
  const [projects, setProjects] = useState<Project[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [coordSystem, setCoordSystem] = useState("WGS84");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await api.listProjects();
      setProjects(list);
      const a = await getActiveProjectId();
      setActiveId(a);
    } catch (e: any) {
      Alert.alert("Error", e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const create = async () => {
    if (!name.trim()) {
      Alert.alert("Name required", "Please enter a project name.");
      return;
    }
    setSaving(true);
    try {
      const p = await api.createProject({
        name: name.trim(),
        description: description.trim(),
        coordinate_system: coordSystem,
      });
      await setActiveProjectId(p.id);
      setActiveId(p.id);
      setShowCreate(false);
      setName("");
      setDescription("");
      await load();
    } catch (e: any) {
      Alert.alert("Error", e.message);
    } finally {
      setSaving(false);
    }
  };

  const open = async (p: Project) => {
    await setActiveProjectId(p.id);
    setActiveId(p.id);
    router.push("/points");
  };

  const remove = (p: Project) => {
    Alert.alert("Delete project?", `"${p.name}" and all its points will be deleted.`, [
      { text: "Cancel", style: "cancel" },
      {
        text: "Delete",
        style: "destructive",
        onPress: async () => {
          try {
            await api.deleteProject(p.id);
            if (activeId === p.id) {
              await setActiveProjectId(null);
              setActiveId(null);
            }
            load();
          } catch (e: any) {
            Alert.alert("Error", e.message);
          }
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <View style={styles.toolbar}>
        <Text style={styles.toolbarText}>
          {projects.length} PROJECT{projects.length === 1 ? "" : "S"}
        </Text>
        <TouchableOpacity
          style={styles.newBtn}
          onPress={() => setShowCreate(true)}
          testID="project-create-btn"
        >
          <Ionicons name="add" size={18} color="#FFF" />
          <Text style={styles.newBtnText}>NEW PROJECT</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={projects}
        keyExtractor={(i) => i.id}
        onRefresh={load}
        refreshing={loading}
        contentContainerStyle={{ padding: 12 }}
        ListEmptyComponent={
          !loading ? (
            <View style={styles.empty}>
              <Ionicons name="folder-open-outline" size={48} color={colors.textDisabled} />
              <Text style={styles.emptyText}>No projects yet</Text>
              <Text style={styles.emptySub}>Tap "NEW PROJECT" to begin surveying</Text>
            </View>
          ) : null
        }
        renderItem={({ item }) => {
          const active = item.id === activeId;
          return (
            <TouchableOpacity
              testID={`project-item-${item.name}`}
              style={[styles.card, active && styles.cardActive]}
              activeOpacity={0.85}
              onPress={() => open(item)}
              onLongPress={() => remove(item)}
            >
              <View style={[styles.cardIcon, { backgroundColor: active ? colors.primary : colors.black }]}>
                <Ionicons name="folder" size={20} color="#FFF" />
              </View>
              <View style={{ flex: 1 }}>
                <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
                  <Text style={styles.cardName}>{item.name}</Text>
                  {active && (
                    <View style={styles.activeTag}>
                      <Text style={styles.activeTagText}>ACTIVE</Text>
                    </View>
                  )}
                </View>
                <Text style={styles.cardMeta}>
                  {item.coordinate_system} • {item.point_count} pts • {item.created_at.slice(0, 10)}
                </Text>
                {!!item.description && <Text style={styles.cardDesc}>{item.description}</Text>}
              </View>
              <TouchableOpacity onPress={() => remove(item)} hitSlop={8} testID={`project-delete-${item.name}`}>
                <Ionicons name="trash-outline" size={20} color={colors.single} />
              </TouchableOpacity>
            </TouchableOpacity>
          );
        }}
      />

      <Modal visible={showCreate} animationType="slide" transparent>
        <KeyboardAvoidingView
          behavior={Platform.OS === "ios" ? "padding" : undefined}
          style={styles.modalWrap}
        >
          <View style={styles.modal}>
            <Text style={styles.modalTitle}>NEW PROJECT</Text>
            <Text style={styles.label}>Project name</Text>
            <TextInput
              testID="project-name-input"
              style={styles.input}
              value={name}
              onChangeText={setName}
              placeholder="e.g. Riverbank Topo 2026"
              placeholderTextColor={colors.textDisabled}
            />
            <Text style={styles.label}>Description (optional)</Text>
            <TextInput
              testID="project-desc-input"
              style={[styles.input, { height: 70 }]}
              value={description}
              onChangeText={setDescription}
              placeholder="Notes about this project"
              placeholderTextColor={colors.textDisabled}
              multiline
            />
            <Text style={styles.label}>Coordinate system</Text>
            <View style={styles.csRow}>
              {["WGS84", "UTM", "Local"].map((cs) => (
                <TouchableOpacity
                  key={cs}
                  onPress={() => setCoordSystem(cs)}
                  style={[styles.csPill, coordSystem === cs && styles.csPillActive]}
                >
                  <Text style={[styles.csPillText, coordSystem === cs && { color: "#FFF" }]}>
                    {cs}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
            <View style={styles.modalActions}>
              <TouchableOpacity
                style={[styles.modalBtn, { backgroundColor: colors.surface }]}
                onPress={() => setShowCreate(false)}
              >
                <Text style={[styles.modalBtnText, { color: colors.text }]}>CANCEL</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalBtn, { backgroundColor: colors.primary }]}
                onPress={create}
                disabled={saving}
                testID="project-save-btn"
              >
                <Text style={styles.modalBtnText}>{saving ? "SAVING..." : "CREATE"}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  toolbar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  toolbarText: { fontSize: 11, fontWeight: "900", letterSpacing: 2, color: colors.textSecondary },
  newBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: colors.primary,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 4,
  },
  newBtnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1, fontSize: 12 },
  empty: { alignItems: "center", paddingVertical: 60 },
  emptyText: { fontSize: 16, fontWeight: "700", color: colors.text, marginTop: 12 },
  emptySub: { fontSize: 13, color: colors.textSecondary, marginTop: 4 },
  card: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#FFF",
    padding: 14,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    marginBottom: 10,
    gap: 12,
  },
  cardActive: { borderColor: colors.primary, borderLeftWidth: 4 },
  cardIcon: {
    width: 40,
    height: 40,
    borderRadius: 6,
    alignItems: "center",
    justifyContent: "center",
  },
  cardName: { fontSize: 16, fontWeight: "800", color: colors.text },
  cardMeta: { fontSize: 12, color: colors.textSecondary, fontFamily: mono, marginTop: 2 },
  cardDesc: { fontSize: 12, color: colors.text, marginTop: 4 },
  activeTag: { backgroundColor: colors.fixed, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 3 },
  activeTagText: { fontSize: 9, color: "#FFF", fontWeight: "900", letterSpacing: 1 },
  modalWrap: { flex: 1, backgroundColor: "rgba(0,0,0,0.5)", justifyContent: "flex-end" },
  modal: { backgroundColor: "#FFF", padding: 20, borderTopLeftRadius: 14, borderTopRightRadius: 14 },
  modalTitle: { fontSize: 18, fontWeight: "900", letterSpacing: 1, color: colors.text, marginBottom: 14 },
  label: { fontSize: 11, fontWeight: "900", letterSpacing: 1.5, color: colors.textSecondary, marginTop: 10, marginBottom: 6 },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    padding: 12,
    fontSize: 15,
    color: colors.text,
  },
  csRow: { flexDirection: "row", gap: 8, marginTop: 4 },
  csPill: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: colors.border,
  },
  csPillActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  csPillText: { fontSize: 13, fontWeight: "700", color: colors.text },
  modalActions: { flexDirection: "row", gap: 10, marginTop: 20 },
  modalBtn: { flex: 1, padding: 14, borderRadius: 6, alignItems: "center" },
  modalBtnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1 },
});
