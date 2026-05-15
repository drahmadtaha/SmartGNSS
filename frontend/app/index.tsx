import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Image } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter, useFocusEffect } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import StatusBar, { useMockedStatus } from "@/src/components/StatusBar";
import { colors, mono } from "@/src/theme";
import { getActiveProjectId } from "@/src/activeProject";
import { api, type Project } from "@/src/api";

type Tile = {
  key: string;
  label: string;
  sub: string;
  icon: keyof typeof Ionicons.glyphMap;
  route: string;
  color: string;
};

const TILES: Tile[] = [
  { key: "project", label: "Project", sub: "Files & data", icon: "folder-open", route: "/project", color: "#0056D2" },
  { key: "device", label: "Device", sub: "GNSS connect", icon: "bluetooth", route: "/device", color: "#0077C8" },
  { key: "survey", label: "Survey", sub: "Capture points", icon: "location", route: "/survey", color: "#00A651" },
  { key: "stakeout", label: "Stakeout", sub: "Navigate to point", icon: "locate", route: "/stakeout", color: "#F5A623" },
  { key: "points", label: "Points", sub: "Coordinate DB", icon: "list", route: "/points", color: "#0A0A0A" },
  { key: "map", label: "Map", sub: "View on map", icon: "map", route: "/map", color: "#0056D2" },
  { key: "cogo", label: "COGO", sub: "Calculations", icon: "calculator", route: "/cogo", color: "#4A4A4A" },
  { key: "ntrip", label: "NTRIP", sub: "RTCM stream", icon: "cloud-download", route: "/ntrip", color: "#0077C8" },
  { key: "cad", label: "CAD", sub: "DXF / SHP", icon: "layers", route: "/cad", color: "#0A0A0A" },
  { key: "settings", label: "Settings", sub: "Configure", icon: "settings", route: "/settings", color: "#4A4A4A" },
];

export default function Home() {
  const router = useRouter();
  const status = useMockedStatus();
  const [activeProject, setActiveProject] = useState<Project | null>(null);

  const refreshActive = React.useCallback(async () => {
    const id = await getActiveProjectId();
    if (!id) {
      setActiveProject(null);
      return;
    }
    try {
      const p = await api.getProject(id);
      setActiveProject(p);
    } catch {
      setActiveProject(null);
    }
  }, []);

  useEffect(() => {
    refreshActive();
  }, [refreshActive]);

  useFocusEffect(
    React.useCallback(() => {
      refreshActive();
    }, [refreshActive])
  );

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <StatusBar status={status} />
      <View style={styles.header} testID="home-header">
        <View>
          <Text style={styles.brand}>SmartGNSS</Text>
          <Text style={styles.tagline}>Professional Field Surveying</Text>
        </View>
        <View style={styles.versionPill}>
          <Text style={styles.versionPillText}>v1.0</Text>
        </View>
      </View>

      <ScrollView contentContainerStyle={styles.scroll}>
        <TouchableOpacity
          style={styles.activeCard}
          testID="home-active-project"
          onPress={() => router.push("/project")}
          activeOpacity={0.85}
        >
          <View style={{ flex: 1 }}>
            <Text style={styles.activeLabel}>ACTIVE PROJECT</Text>
            <Text style={styles.activeName}>
              {activeProject ? activeProject.name : "No project selected"}
            </Text>
            <Text style={styles.activeMeta}>
              {activeProject
                ? `${activeProject.coordinate_system}  •  ${activeProject.point_count} points`
                : "Tap to create or open a project"}
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={22} color={colors.text} />
        </TouchableOpacity>

        <View style={styles.grid}>
          {TILES.map((t) => (
            <TouchableOpacity
              key={t.key}
              testID={`home-tile-${t.key}`}
              style={styles.tile}
              activeOpacity={0.85}
              onPress={() => router.push(t.route as never)}
            >
              <View style={[styles.iconBox, { backgroundColor: t.color }]}>
                <Ionicons name={t.icon} size={26} color="#FFFFFF" />
              </View>
              <Text style={styles.tileLabel}>{t.label}</Text>
              <Text style={styles.tileSub}>{t.sub}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.banner}>
          <Image
            source={{
              uri: "https://images.unsplash.com/photo-1628158088936-68ccaaa400dc?crop=entropy&cs=srgb&fm=jpg&q=80&w=900",
            }}
            style={styles.bannerImg}
          />
          <View style={styles.bannerOverlay}>
            <Text style={styles.bannerTitle}>FIELD-READY</Text>
            <Text style={styles.bannerSub}>RTK • COGO • Stakeout • Export</Text>
          </View>
        </View>

        <View style={styles.footer}>
          <Text style={styles.footerText}>
            GNSS Survey • Stakeout • Road Design • CAD
          </Text>
          <Text style={styles.footerMono}>WGS84 / UTM • NMEA • NTRIP</Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: 2,
    borderBottomColor: colors.black,
  },
  brand: { fontSize: 26, fontWeight: "900", color: colors.text, letterSpacing: -0.5 },
  tagline: { fontSize: 11, color: colors.textSecondary, fontWeight: "600", letterSpacing: 1, marginTop: 2 },
  versionPill: { backgroundColor: colors.black, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 3 },
  versionPillText: { color: "#FFF", fontFamily: mono, fontSize: 11, fontWeight: "700" },
  scroll: { padding: 16, paddingBottom: 40 },
  activeCard: {
    flexDirection: "row",
    alignItems: "center",
    padding: 16,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderLeftWidth: 4,
    borderLeftColor: colors.primary,
    borderRadius: 6,
    marginBottom: 18,
  },
  activeLabel: { fontSize: 10, fontWeight: "900", letterSpacing: 2, color: colors.textSecondary },
  activeName: { fontSize: 18, fontWeight: "800", color: colors.text, marginTop: 4 },
  activeMeta: { fontSize: 12, color: colors.textSecondary, marginTop: 4, fontFamily: mono },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 12, justifyContent: "space-between" },
  tile: {
    width: "48%",
    aspectRatio: 1,
    backgroundColor: "#FFFFFF",
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    padding: 14,
    justifyContent: "space-between",
  },
  iconBox: {
    width: 48,
    height: 48,
    borderRadius: 6,
    alignItems: "center",
    justifyContent: "center",
  },
  tileLabel: { fontSize: 18, fontWeight: "900", color: colors.text, marginTop: 8 },
  tileSub: { fontSize: 11, color: colors.textSecondary, fontWeight: "600", letterSpacing: 0.5 },
  banner: {
    marginTop: 18,
    borderRadius: 6,
    overflow: "hidden",
    height: 110,
    borderWidth: 1,
    borderColor: colors.border,
  },
  bannerImg: { width: "100%", height: "100%" },
  bannerOverlay: {
    position: "absolute",
    inset: 0,
    backgroundColor: "rgba(10,10,10,0.55)",
    padding: 14,
    justifyContent: "center",
  },
  bannerTitle: { color: "#FFFFFF", fontWeight: "900", fontSize: 22, letterSpacing: 2 },
  bannerSub: { color: "#FFFFFF", fontFamily: mono, fontSize: 12, marginTop: 4, opacity: 0.9 },
  footer: { marginTop: 20, alignItems: "center" },
  footerText: { fontSize: 11, color: colors.textSecondary, fontWeight: "700", letterSpacing: 1 },
  footerMono: { fontSize: 10, color: colors.textDisabled, fontFamily: mono, marginTop: 4 },
});
