import React, { useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TextInput,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { api, type NtripMount, type NtripSession } from "@/src/api";
import { storage } from "@/src/utils/storage";
import { colors, mono } from "@/src/theme";

const DEFAULTS = { host: "rtk2go.com", port: "2101", user: "", pass: "" };

export default function NtripScreen() {
  const [host, setHost] = useState(DEFAULTS.host);
  const [port, setPort] = useState(DEFAULTS.port);
  const [user, setUser] = useState(DEFAULTS.user);
  const [pass, setPass] = useState(DEFAULTS.pass);
  const [mounts, setMounts] = useState<NtripMount[]>([]);
  const [filter, setFilter] = useState("");
  const [loading, setLoading] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [session, setSession] = useState<NtripSession | null>(null);
  const pollRef = useRef<any>(null);

  useEffect(() => {
    (async () => {
      const saved = await storage.getItem<string>("ntrip.config", "");
      if (saved) {
        try {
          const cfg = JSON.parse(saved);
          if (cfg.host) setHost(cfg.host);
          if (cfg.port) setPort(String(cfg.port));
          if (cfg.user) setUser(cfg.user);
        } catch {}
      }
    })();
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  const saveConfig = () =>
    storage.setItem("ntrip.config", JSON.stringify({ host, port, user }));

  const fetchTable = async () => {
    setLoading(true);
    setMounts([]);
    saveConfig();
    try {
      const res = await api.ntripSourcetable({
        host,
        port: parseInt(port, 10) || 2101,
        username: user,
        password: pass,
      });
      setMounts(res.mountpoints);
    } catch (e: any) {
      Alert.alert("Sourcetable failed", e.message);
    } finally {
      setLoading(false);
    }
  };

  const startStream = async (mount: NtripMount) => {
    setConnecting(true);
    try {
      const res = await api.ntripConnect({
        host,
        port: parseInt(port, 10) || 2101,
        mountpoint: mount.mountpoint,
        username: user,
        password: pass,
      });
      // Begin polling
      pollRef.current && clearInterval(pollRef.current);
      pollRef.current = setInterval(async () => {
        try {
          const s = await api.ntripStatus(res.session_id);
          setSession(s);
          if (s.status === "disconnected" || s.status === "error") {
            clearInterval(pollRef.current);
            pollRef.current = null;
          }
        } catch {
          clearInterval(pollRef.current);
          pollRef.current = null;
        }
      }, 1500);
    } catch (e: any) {
      Alert.alert("Connect failed", e.message);
    } finally {
      setConnecting(false);
    }
  };

  const stopStream = async () => {
    if (!session) return;
    try {
      await api.ntripDisconnect(session.id);
    } catch {}
    if (pollRef.current) clearInterval(pollRef.current);
    pollRef.current = null;
    setSession(null);
  };

  const filtered = mounts.filter((m) => {
    const q = filter.trim().toLowerCase();
    if (!q) return true;
    return (
      m.mountpoint.toLowerCase().includes(q) ||
      (m.country || "").toLowerCase().includes(q) ||
      (m.identifier || "").toLowerCase().includes(q)
    );
  });

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={{ flex: 1 }}
      >
        <ScrollView contentContainerStyle={styles.scroll}>
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>NTRIP CASTER</Text>
            <View style={styles.row}>
              <View style={{ flex: 2 }}>
                <Text style={styles.flabel}>HOST</Text>
                <TextInput
                  style={styles.input}
                  value={host}
                  onChangeText={setHost}
                  autoCapitalize="none"
                  testID="ntrip-host"
                />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.flabel}>PORT</Text>
                <TextInput
                  style={styles.input}
                  value={port}
                  onChangeText={setPort}
                  keyboardType="number-pad"
                  testID="ntrip-port"
                />
              </View>
            </View>
            <View style={styles.row}>
              <View style={{ flex: 1 }}>
                <Text style={styles.flabel}>USER</Text>
                <TextInput
                  style={styles.input}
                  value={user}
                  onChangeText={setUser}
                  autoCapitalize="none"
                  placeholder="(optional)"
                  placeholderTextColor={colors.textDisabled}
                  testID="ntrip-user"
                />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.flabel}>PASS</Text>
                <TextInput
                  style={styles.input}
                  value={pass}
                  onChangeText={setPass}
                  secureTextEntry
                  placeholder="(optional)"
                  placeholderTextColor={colors.textDisabled}
                  testID="ntrip-pass"
                />
              </View>
            </View>
            <TouchableOpacity
              style={styles.primaryBtn}
              onPress={fetchTable}
              disabled={loading}
              testID="ntrip-fetch-btn"
            >
              {loading ? (
                <ActivityIndicator color="#FFF" />
              ) : (
                <Ionicons name="cloud-download-outline" size={18} color="#FFF" />
              )}
              <Text style={styles.primaryBtnText}>
                {loading ? "LOADING…" : "FETCH SOURCETABLE"}
              </Text>
            </TouchableOpacity>
          </View>

          {session && (
            <View style={styles.sessCard}>
              <View style={{ flex: 1 }}>
                <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                  <Text style={styles.sessMount}>{session.mountpoint}</Text>
                  <View
                    style={[
                      styles.sessPill,
                      {
                        backgroundColor:
                          session.status === "streaming"
                            ? colors.fixed
                            : session.status === "error"
                            ? colors.single
                            : colors.float,
                      },
                    ]}
                  >
                    <Text style={styles.sessPillText}>{session.status.toUpperCase()}</Text>
                  </View>
                </View>
                <Text style={styles.sessMeta}>
                  {session.host}:{session.port} • {(session.bytes / 1024).toFixed(2)} KB
                </Text>
                <Text style={styles.sessMeta}>
                  RTCM:{" "}
                  {Object.keys(session.msg_types).length === 0
                    ? "—"
                    : Object.entries(session.msg_types)
                        .map(([k, v]) => `${k}(${v})`)
                        .join(" ")}
                </Text>
                {session.error && (
                  <Text style={styles.sessErr} numberOfLines={2}>
                    {session.error}
                  </Text>
                )}
              </View>
              <TouchableOpacity
                style={styles.stopBtn}
                onPress={stopStream}
                testID="ntrip-stop-btn"
              >
                <Ionicons name="stop" size={18} color="#FFF" />
                <Text style={styles.stopBtnText}>STOP</Text>
              </TouchableOpacity>
            </View>
          )}

          {mounts.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>
                {filtered.length} / {mounts.length} MOUNTPOINTS
              </Text>
              <TextInput
                style={styles.input}
                value={filter}
                onChangeText={setFilter}
                placeholder="Filter by name / country / id"
                placeholderTextColor={colors.textDisabled}
                autoCapitalize="none"
                testID="ntrip-filter"
              />
              <View style={{ marginTop: 8 }}>
                {filtered.slice(0, 200).map((m) => (
                  <View key={m.mountpoint} style={styles.mountRow}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.mountName}>{m.mountpoint}</Text>
                      <Text style={styles.mountMeta}>
                        {m.format} • {m.country || "—"} • {m.identifier || ""}
                      </Text>
                    </View>
                    <TouchableOpacity
                      style={styles.useBtn}
                      onPress={() => startStream(m)}
                      disabled={connecting}
                      testID={`ntrip-use-${m.mountpoint}`}
                    >
                      <Text style={styles.useBtnText}>USE</Text>
                    </TouchableOpacity>
                  </View>
                ))}
                {filtered.length > 200 && (
                  <Text style={styles.emptyLine}>
                    Showing first 200. Use the filter to narrow down.
                  </Text>
                )}
              </View>
            </View>
          )}

          {mounts.length === 0 && !loading && (
            <View style={styles.notice}>
              <Ionicons name="information-circle" size={16} color={colors.primary} />
              <Text style={styles.noticeText}>
                Defaults to the free public caster rtk2go.com:2101. Username/password are
                optional. Tap FETCH SOURCETABLE to list mountpoints, then USE to start
                streaming RTCM corrections.
              </Text>
            </View>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: 12, paddingBottom: 40 },
  section: { marginBottom: 16 },
  sectionTitle: { fontSize: 10, fontWeight: "900", letterSpacing: 2, color: colors.textSecondary, marginBottom: 8 },
  row: { flexDirection: "row", gap: 10 },
  flabel: { fontSize: 10, fontWeight: "900", letterSpacing: 1.5, color: colors.textSecondary, marginBottom: 5 },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: "#FFF",
    borderRadius: 6,
    padding: 12,
    fontFamily: mono,
    color: colors.text,
  },
  primaryBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    backgroundColor: colors.primary,
    padding: 14,
    borderRadius: 6,
    marginTop: 12,
  },
  primaryBtnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1.5 },
  notice: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    padding: 12,
    backgroundColor: "#EAF2FE",
    borderWidth: 1,
    borderColor: "#C7DBFA",
    borderRadius: 6,
  },
  noticeText: { color: colors.text, fontSize: 11, flex: 1, lineHeight: 16 },
  sessCard: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.black,
    padding: 12,
    borderRadius: 6,
    marginBottom: 16,
    gap: 10,
  },
  sessMount: { color: "#FFF", fontWeight: "900", fontSize: 16 },
  sessPill: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 3 },
  sessPillText: { color: "#FFF", fontSize: 10, fontWeight: "900", letterSpacing: 1 },
  sessMeta: { color: "#BBB", fontFamily: mono, fontSize: 11, marginTop: 3 },
  sessErr: { color: colors.float, fontSize: 11, marginTop: 4 },
  stopBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.single,
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderRadius: 4,
  },
  stopBtnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1, fontSize: 11 },
  mountRow: {
    flexDirection: "row",
    alignItems: "center",
    padding: 10,
    backgroundColor: "#FFF",
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 4,
    marginBottom: 6,
  },
  mountName: { fontWeight: "800", fontSize: 14, color: colors.text },
  mountMeta: { fontSize: 11, color: colors.textSecondary, fontFamily: mono, marginTop: 2 },
  useBtn: {
    backgroundColor: colors.primary,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 4,
  },
  useBtnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1, fontSize: 11 },
  emptyLine: { color: colors.textSecondary, fontSize: 11, textAlign: "center", padding: 12 },
});
