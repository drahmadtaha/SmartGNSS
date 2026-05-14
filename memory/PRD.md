# SmartGNSS - Product Requirements Document

## Overview
SmartGNSS is a professional mobile field surveying app inspired by SurPad 4.2. Built with Expo + FastAPI + MongoDB, it provides field data collection, point survey, stakeout, COGO calculations, and export tools using the phone GPS (real GNSS RTK receivers connect via Bluetooth NMEA on installed builds — mocked in preview).

## Tech Stack
- Frontend: Expo Router 6 (React Native 0.81)
- Backend: FastAPI + Motor (async MongoDB)
- Map: Leaflet via WebView (no native map library required)
- GPS: expo-location

## Screens
- `/` Home dashboard with permanent RTK status bar + bento tile menu
- `/project` Project list, create / open / delete, set active project
- `/survey` Live GPS coordinate display + point capture form (name, code, antenna height, note)
- `/points` Coordinate database table with CSV / KML export
- `/stakeout` Pick target point → live distance, bearing, bullseye compass arrow, N/S & E/W offsets
- `/map` Leaflet map with all project points
- `/cogo` Inverse (distance/bearing) and Polygon Area calculators
- `/device` Bluetooth GNSS device scan (mocked) — CHC, South, Stonex, Hi-Target, Sokkia, Leica
- `/settings` Coordinate format, units, voice alerts, auto-sequence

## API
- `POST/GET/DELETE /api/projects` — Project CRUD
- `GET/POST/DELETE /api/points` — Point CRUD per project
- `GET /api/export/csv/{id}` and `GET /api/export/kml/{id}`
- `POST /api/cogo/inverse` and `POST /api/cogo/area`
- `GET /api/devices/scan` — Mocked device list

## Mocked / Not Real
- Bluetooth GNSS device connections (preview cannot access real hardware)
- RTK fix-state cycle (Single → DGPS → Float → Fixed) every 4s
- Satellite count and accuracy values

## Future Enhancements
- Real Bluetooth NMEA parser (development build only)
- NTRIP client for RTCM corrections
- DXF / SHP / DWG import & overlay
- Road design module
- License activation
