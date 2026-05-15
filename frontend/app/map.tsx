import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { WebView } from "react-native-webview";
import { Ionicons } from "@expo/vector-icons";
import { TouchableOpacity } from "react-native";
import { useRouter } from "expo-router";
import { api, type Point, type CadOverlay } from "@/src/api";
import { colors, mono } from "@/src/theme";
import { getActiveProjectId } from "@/src/activeProject";

export default function MapScreen() {
  const router = useRouter();
  const [points, setPoints] = useState<Point[]>([]);
  const [overlays, setOverlays] = useState<CadOverlay[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    (async () => {
      const id = await getActiveProjectId();
      if (!id) {
        setLoaded(true);
        return;
      }
      try {
        const [pts, cads] = await Promise.all([api.listPoints(id), api.listCad(id)]);
        setPoints(pts);
        setOverlays(cads);
      } finally {
        setLoaded(true);
      }
    })();
  }, []);

  if (!loaded) return null;

  const markersJs = points
    .map(
      (p) =>
        `L.marker([${p.latitude},${p.longitude}]).addTo(map).bindPopup('<b>${escapeJs(p.name)}</b><br>${p.latitude.toFixed(6)}, ${p.longitude.toFixed(6)}<br>Alt: ${p.altitude.toFixed(2)} m');`
    )
    .join("\n");

  // Inject all CAD overlays as GeoJSON layers
  const overlaysJs = overlays
    .map(
      (o, i) =>
        `var overlay_${i} = L.geoJSON(${JSON.stringify(o.geojson)}, {
          style: { color: '${i % 2 === 0 ? "#0056D2" : "#0A0A0A"}', weight: 2, fillOpacity: 0.15 },
          pointToLayer: function(f, latlng) { return L.circleMarker(latlng, { radius: 4, color: '#0056D2', fillOpacity: 0.8 }); },
          onEachFeature: function(f, layer) {
            var props = f.properties || {};
            var html = '<b>${escapeJs(o.filename)}</b>';
            for (var k in props) { html += '<br>' + k + ': ' + props[k]; }
            layer.bindPopup(html);
          }
        }).addTo(map);`
    )
    .join("\n");

  const center =
    points.length > 0
      ? [points[0].latitude, points[0].longitude]
      : overlays[0]?.bounds
      ? [
          (overlays[0].bounds[1] + overlays[0].bounds[3]) / 2,
          (overlays[0].bounds[0] + overlays[0].bounds[2]) / 2,
        ]
      : [40.7128, -74.006];

  const html = `<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no" />
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<style>html,body,#map{height:100%;margin:0;padding:0;background:#0a0a0a;font-family:sans-serif;color:#fff}</style>
</head><body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
var map = L.map('map', { zoomControl: true }).setView([${center[0]}, ${center[1]}], ${points.length || overlays.length ? 17 : 4});
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  attribution: '© OpenStreetMap', maxZoom: 20
}).addTo(map);
${markersJs}
${overlaysJs}
var allBounds = null;
${points.length > 1 ? `var group = L.featureGroup([${points.map((p) => `L.marker([${p.latitude},${p.longitude}])`).join(",")}]); allBounds = group.getBounds();` : ""}
${overlays.map((o, i) => `try { var b = overlay_${i}.getBounds(); allBounds = allBounds ? allBounds.extend(b) : b; } catch(e) {}`).join("\n")}
if (allBounds && allBounds.isValid()) map.fitBounds(allBounds.pad(0.2));
</script></body></html>`;

  return (
    <SafeAreaView style={styles.safe} edges={["bottom"]}>
      <View style={styles.toolbar}>
        <Text style={styles.toolbarText}>
          {points.length} PTS • {overlays.length} CAD
        </Text>
        <TouchableOpacity onPress={() => router.push("/cad")} style={[styles.btn, { backgroundColor: colors.black }]} testID="map-open-cad">
          <Ionicons name="layers" size={16} color="#FFF" />
          <Text style={styles.btnText}>CAD</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={() => router.push("/survey")} style={styles.btn}>
          <Ionicons name="add" size={16} color="#FFF" />
          <Text style={styles.btnText}>SURVEY</Text>
        </TouchableOpacity>
      </View>
      {points.length === 0 && overlays.length === 0 ? (
        <View style={styles.empty}>
          <Ionicons name="map-outline" size={48} color={colors.textDisabled} />
          <Text style={styles.emptyText}>Nothing to show</Text>
          <Text style={styles.emptySub}>Capture points or import a DXF/SHP overlay</Text>
        </View>
      ) : (
        <WebView
          source={{ html }}
          style={{ flex: 1, backgroundColor: "#0A0A0A" }}
          originWhitelist={["*"]}
          javaScriptEnabled
          domStorageEnabled
        />
      )}
    </SafeAreaView>
  );
}

function escapeJs(s: string): string {
  return s.replace(/['\\]/g, "\\$&").replace(/\n/g, "\\n");
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  toolbar: {
    flexDirection: "row",
    alignItems: "center",
    padding: 12,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderColor: colors.border,
    gap: 8,
  },
  toolbarText: { flex: 1, fontSize: 12, fontWeight: "900", letterSpacing: 1.5, color: colors.textSecondary, fontFamily: mono },
  btn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.primary,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 4,
  },
  btnText: { color: "#FFF", fontWeight: "900", letterSpacing: 1, fontSize: 12 },
  empty: { flex: 1, alignItems: "center", justifyContent: "center" },
  emptyText: { fontSize: 16, fontWeight: "700", color: colors.text, marginTop: 12 },
  emptySub: { fontSize: 13, color: colors.textSecondary, marginTop: 4 },
});
