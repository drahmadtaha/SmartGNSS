import { Stack } from "expo-router";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <StatusBar style="light" backgroundColor="#0A0A0A" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: "#0A0A0A" },
          headerTintColor: "#FFFFFF",
          headerTitleStyle: { fontWeight: "900", letterSpacing: 1 },
          contentStyle: { backgroundColor: "#FFFFFF" },
        }}
      >
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="project" options={{ title: "PROJECTS" }} />
        <Stack.Screen name="survey" options={{ title: "POINT SURVEY" }} />
        <Stack.Screen name="points" options={{ title: "POINTS" }} />
        <Stack.Screen name="stakeout" options={{ title: "STAKEOUT" }} />
        <Stack.Screen name="map" options={{ title: "MAP VIEW" }} />
        <Stack.Screen name="cogo" options={{ title: "COGO TOOLS" }} />
        <Stack.Screen name="device" options={{ title: "DEVICE" }} />
        <Stack.Screen name="ntrip" options={{ title: "NTRIP / RTCM" }} />
        <Stack.Screen name="cad" options={{ title: "CAD IMPORT" }} />
        <Stack.Screen name="settings" options={{ title: "SETTINGS" }} />
      </Stack>
    </SafeAreaProvider>
  );
}
