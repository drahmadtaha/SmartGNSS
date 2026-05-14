import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Switch } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { storage } from "@/src/utils/storage";
import { colors, mono } from "@/src/theme";

const COORD_FORMATS = ["DD (decimal degrees)", "DMS (deg-min-sec)", "DDM (deg-decimal min)"];
const UNITS = ["Metric (m)", "US Survey Feet", "International Feet"];

export default function Settings() {
  const [coordFormat, setCoordFormat] = useState(0);
  const [unit, setUnit] = useState(0);
  const [voice, setVoice] = useState(true);
  const [autoSeq, setAutoSeq] = useState(true);

  useEffect(() => {
    (async () => {
      setCoordFormat((await storage.getItem<number>("settings.coordFormat", 0)) ?? 0);
      setUnit((await storage.getItem<number>("settings.unit", 0)) ?? 0);
      setVoice((await storage.getItem<boolean>("settings.voice", true)) ?? true);
      setAutoSeq((await storage.getItem<boolean>("settings.autoSeq", true)) ?? true);
    })();
  }, []);

  const persist = (k: string, v: any) => storage.setItem(k, v);

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <ScrollView>
        <Section title="COORDINATE FORMAT">
          {COORD_FORMATS.map((f, idx) => (
            <Row
              key={f}
              title={f}
              right={
                <Ionicons
                  name={coordFormat === idx ? "radio-button-on" : "radio-button-off"}
                  size={20}
                  color={coordFormat === idx ? colors.primary : colors.textDisabled}
                />
              }
              onPress={() => {
                setCoordFormat(idx);
                persist("settings.coordFormat", idx);
              }}
            />
          ))}
        </Section>

        <Section title="UNITS">
          {UNITS.map((u, idx) => (
            <Row
              key={u}
              title={u}
              right={
                <Ionicons
                  name={unit === idx ? "radio-button-on" : "radio-button-off"}
                  size={20}
                  color={unit === idx ? colors.primary : colors.textDisabled}
                />
              }
              onPress={() => {
                setUnit(idx);
                persist("settings.unit", idx);
              }}
            />
          ))}
        </Section>

        <Section title="PREFERENCES">
          <Row
            title="Voice alerts"
            sub="Notify on fix change & save"
            right={
              <Switch
                value={voice}
                onValueChange={(v) => {
                  setVoice(v);
                  persist("settings.voice", v);
                }}
                trackColor={{ true: colors.primary, false: colors.surfaceAlt }}
                thumbColor="#FFF"
              />
            }
          />
          <Row
            title="Auto-sequence point names"
            sub="PT001, PT002…"
            right={
              <Switch
                value={autoSeq}
                onValueChange={(v) => {
                  setAutoSeq(v);
                  persist("settings.autoSeq", v);
                }}
                trackColor={{ true: colors.primary, false: colors.surfaceAlt }}
                thumbColor="#FFF"
              />
            }
          />
        </Section>

        <Section title="ABOUT">
          <Row title="App" right={<Text style={styles.right}>SmartGNSS</Text>} />
          <Row title="Version" right={<Text style={styles.right}>1.0.0</Text>} />
          <Row title="Coordinate engine" right={<Text style={styles.right}>WGS84 / UTM</Text>} />
          <Row title="Supported brands" right={<Text style={styles.right}>CHC • South • Stonex • Hi-Target • Leica</Text>} />
        </Section>

        <View style={{ height: 32 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <View style={styles.sectionBody}>{children}</View>
    </View>
  );
}

function Row({
  title,
  sub,
  right,
  onPress,
}: {
  title: string;
  sub?: string;
  right?: React.ReactNode;
  onPress?: () => void;
}) {
  const Wrap = (onPress ? TouchableOpacity : View) as any;
  return (
    <Wrap style={styles.row} onPress={onPress} activeOpacity={0.7}>
      <View style={{ flex: 1 }}>
        <Text style={styles.rowTitle}>{title}</Text>
        {!!sub && <Text style={styles.rowSub}>{sub}</Text>}
      </View>
      {right}
    </Wrap>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  section: { marginTop: 16, paddingHorizontal: 12 },
  sectionTitle: { fontSize: 11, fontWeight: "900", letterSpacing: 2, color: colors.textSecondary, marginBottom: 8 },
  sectionBody: { borderWidth: 1, borderColor: colors.border, borderRadius: 6, backgroundColor: "#FFF", overflow: "hidden" },
  row: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    borderBottomWidth: 1,
    borderColor: colors.border,
  },
  rowTitle: { fontSize: 14, fontWeight: "700", color: colors.text },
  rowSub: { fontSize: 11, color: colors.textSecondary, marginTop: 2 },
  right: { fontFamily: mono, color: colors.primary, fontSize: 12, fontWeight: "700", maxWidth: 200, textAlign: "right" },
});
