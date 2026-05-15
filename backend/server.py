from fastapi import FastAPI, APIRouter, HTTPException, UploadFile, File, Form
from fastapi.responses import PlainTextResponse
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import io
import socket
import base64
import asyncio
import logging
import math
import struct
import tempfile
import threading
from pathlib import Path
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
import uuid
from datetime import datetime, timezone

import ezdxf
import shapefile  # pyshp


ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / ".env")

# MongoDB connection
mongo_url = os.environ["MONGO_URL"]
client = AsyncIOMotorClient(mongo_url)
db = client[os.environ["DB_NAME"]]

app = FastAPI(title="SmartGNSS API")
api_router = APIRouter(prefix="/api")


# ---------- Models ----------
def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class Project(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    name: str
    description: Optional[str] = ""
    coordinate_system: str = "WGS84"
    created_at: str = Field(default_factory=now_iso)
    point_count: int = 0


class ProjectCreate(BaseModel):
    name: str
    description: Optional[str] = ""
    coordinate_system: Optional[str] = "WGS84"


class Point(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    project_id: str
    name: str
    code: Optional[str] = ""
    latitude: float
    longitude: float
    altitude: float = 0.0
    accuracy_h: float = 0.0
    accuracy_v: float = 0.0
    antenna_height: float = 0.0
    fix_type: str = "Single"  # Single, DGPS, Float, Fixed
    satellites: int = 0
    note: Optional[str] = ""
    created_at: str = Field(default_factory=now_iso)


class PointCreate(BaseModel):
    project_id: str
    name: str
    code: Optional[str] = ""
    latitude: float
    longitude: float
    altitude: Optional[float] = 0.0
    accuracy_h: Optional[float] = 0.0
    accuracy_v: Optional[float] = 0.0
    antenna_height: Optional[float] = 0.0
    fix_type: Optional[str] = "Single"
    satellites: Optional[int] = 0
    note: Optional[str] = ""


# ---------- Helpers ----------
PROJECTION = {"_id": 0}


# ---------- Routes ----------
@api_router.get("/")
async def root():
    return {"app": "SmartGNSS", "version": "1.0.0", "status": "ok"}


# Projects
@api_router.post("/projects", response_model=Project)
async def create_project(payload: ProjectCreate):
    project = Project(
        name=payload.name,
        description=payload.description or "",
        coordinate_system=payload.coordinate_system or "WGS84",
    )
    await db.projects.insert_one(project.model_dump())
    return project


@api_router.get("/projects", response_model=List[Project])
async def list_projects():
    docs = await db.projects.find({}, PROJECTION).sort("created_at", -1).to_list(1000)
    # attach point counts
    for d in docs:
        d["point_count"] = await db.points.count_documents({"project_id": d["id"]})
    return [Project(**d) for d in docs]


@api_router.get("/projects/{project_id}", response_model=Project)
async def get_project(project_id: str):
    doc = await db.projects.find_one({"id": project_id}, PROJECTION)
    if not doc:
        raise HTTPException(status_code=404, detail="Project not found")
    doc["point_count"] = await db.points.count_documents({"project_id": project_id})
    return Project(**doc)


@api_router.delete("/projects/{project_id}")
async def delete_project(project_id: str):
    await db.points.delete_many({"project_id": project_id})
    result = await db.projects.delete_one({"id": project_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Project not found")
    return {"deleted": True}


# Points
@api_router.post("/points", response_model=Point)
async def create_point(payload: PointCreate):
    proj = await db.projects.find_one({"id": payload.project_id}, PROJECTION)
    if not proj:
        raise HTTPException(status_code=404, detail="Project not found")
    point = Point(**payload.model_dump())
    await db.points.insert_one(point.model_dump())
    return point


@api_router.get("/points", response_model=List[Point])
async def list_points(project_id: str):
    docs = (
        await db.points.find({"project_id": project_id}, PROJECTION)
        .sort("created_at", -1)
        .to_list(5000)
    )
    return [Point(**d) for d in docs]


@api_router.delete("/points/{point_id}")
async def delete_point(point_id: str):
    result = await db.points.delete_one({"id": point_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Point not found")
    return {"deleted": True}


# Export
@api_router.get("/export/csv/{project_id}", response_class=PlainTextResponse)
async def export_csv(project_id: str):
    proj = await db.projects.find_one({"id": project_id}, PROJECTION)
    if not proj:
        raise HTTPException(status_code=404, detail="Project not found")
    docs = (
        await db.points.find({"project_id": project_id}, PROJECTION)
        .sort("created_at", 1)
        .to_list(5000)
    )
    header = "Name,Code,Latitude,Longitude,Altitude,H.Acc(m),V.Acc(m),AntennaH(m),Fix,Sats,Note,Timestamp"
    rows = [header]
    for d in docs:
        rows.append(
            f"{d['name']},{d.get('code','')},{d['latitude']:.8f},{d['longitude']:.8f},"
            f"{d.get('altitude',0):.3f},{d.get('accuracy_h',0):.3f},{d.get('accuracy_v',0):.3f},"
            f"{d.get('antenna_height',0):.3f},{d.get('fix_type','Single')},{d.get('satellites',0)},"
            f"\"{d.get('note','')}\",{d.get('created_at','')}"
        )
    return "\n".join(rows)


@api_router.get("/export/kml/{project_id}", response_class=PlainTextResponse)
async def export_kml(project_id: str):
    proj = await db.projects.find_one({"id": project_id}, PROJECTION)
    if not proj:
        raise HTTPException(status_code=404, detail="Project not found")
    docs = (
        await db.points.find({"project_id": project_id}, PROJECTION)
        .sort("created_at", 1)
        .to_list(5000)
    )
    placemarks = []
    for d in docs:
        placemarks.append(
            f"""    <Placemark>
      <name>{d['name']}</name>
      <description>Code: {d.get('code','')} | Fix: {d.get('fix_type','')} | Sats: {d.get('satellites',0)}</description>
      <Point><coordinates>{d['longitude']},{d['latitude']},{d.get('altitude',0)}</coordinates></Point>
    </Placemark>"""
        )
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>{proj['name']}</name>
{chr(10).join(placemarks)}
  </Document>
</kml>"""


# COGO
class CogoTwoPoints(BaseModel):
    lat1: float
    lon1: float
    lat2: float
    lon2: float


@api_router.post("/cogo/inverse")
async def cogo_inverse(p: CogoTwoPoints):
    """Compute distance (meters) and bearing (degrees) between two WGS84 points."""
    R = 6371000.0
    phi1 = math.radians(p.lat1)
    phi2 = math.radians(p.lat2)
    dphi = math.radians(p.lat2 - p.lat1)
    dlmb = math.radians(p.lon2 - p.lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlmb / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    distance = R * c
    y = math.sin(dlmb) * math.cos(phi2)
    x = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(dlmb)
    bearing = (math.degrees(math.atan2(y, x)) + 360) % 360
    return {"distance_m": round(distance, 3), "bearing_deg": round(bearing, 4)}


class CogoPolygon(BaseModel):
    points: List[dict]  # [{lat, lon}, ...]


@api_router.post("/cogo/area")
async def cogo_area(p: CogoPolygon):
    """Approximate polygon area (m²) using equirectangular projection."""
    if len(p.points) < 3:
        return {"area_m2": 0.0, "perimeter_m": 0.0}
    R = 6371000.0
    lat0 = sum(pt["lat"] for pt in p.points) / len(p.points)
    coords = []
    for pt in p.points:
        x = math.radians(pt["lon"]) * R * math.cos(math.radians(lat0))
        y = math.radians(pt["lat"]) * R
        coords.append((x, y))
    area = 0.0
    perim = 0.0
    n = len(coords)
    for i in range(n):
        x1, y1 = coords[i]
        x2, y2 = coords[(i + 1) % n]
        area += x1 * y2 - x2 * y1
        perim += math.hypot(x2 - x1, y2 - y1)
    return {"area_m2": round(abs(area) / 2.0, 3), "perimeter_m": round(perim, 3)}


# Mocked devices for Bluetooth catalog
MOCK_DEVICES = [
    {"id": "dev-001", "name": "CHC i73+", "type": "GNSS RTK", "rssi": -54},
    {"id": "dev-002", "name": "South Galaxy G1", "type": "GNSS RTK", "rssi": -61},
    {"id": "dev-003", "name": "Stonex S850A", "type": "GNSS RTK", "rssi": -68},
    {"id": "dev-004", "name": "Hi-Target V200", "type": "GNSS RTK", "rssi": -72},
    {"id": "dev-005", "name": "Sokkia GRX3", "type": "GNSS RTK", "rssi": -77},
    {"id": "dev-006", "name": "Leica TS16 (Total Station)", "type": "Total Station", "rssi": -65},
]


@api_router.get("/devices/scan")
async def devices_scan():
    return {"devices": MOCK_DEVICES}


# ==========================================================================
# NTRIP CLIENT  -  RTCM correction stream from a NTRIP caster
# ==========================================================================
class NtripQuery(BaseModel):
    host: str = "rtk2go.com"
    port: int = 2101
    username: Optional[str] = ""
    password: Optional[str] = ""


class NtripConnect(NtripQuery):
    mountpoint: str


# In-memory session store: {session_id: {bytes, msg_types, last_rx, status, thread, stop_event}}
NTRIP_SESSIONS: Dict[str, Dict[str, Any]] = {}
NTRIP_LOCK = threading.Lock()


def _ntrip_get_sourcetable(host: str, port: int, username: str = "", password: str = "", timeout: float = 8.0) -> str:
    """Fetch the NTRIP source table (mountpoints listing) from a caster."""
    auth_header = ""
    if username:
        token = base64.b64encode(f"{username}:{password}".encode()).decode()
        auth_header = f"Authorization: Basic {token}\r\n"
    req = (
        "GET / HTTP/1.0\r\n"
        f"User-Agent: NTRIP SmartGNSS/1.0\r\n"
        f"Host: {host}:{port}\r\n"
        "Accept: */*\r\n"
        f"{auth_header}"
        "\r\n"
    )
    with socket.create_connection((host, port), timeout=timeout) as s:
        s.sendall(req.encode())
        chunks = []
        s.settimeout(timeout)
        while True:
            try:
                data = s.recv(4096)
            except socket.timeout:
                break
            if not data:
                break
            chunks.append(data)
            if b"ENDSOURCETABLE" in b"".join(chunks):
                break
    return b"".join(chunks).decode(errors="replace")


def _parse_sourcetable(text: str) -> List[Dict[str, Any]]:
    mounts = []
    for line in text.splitlines():
        if line.startswith("STR;"):
            parts = line.split(";")
            if len(parts) >= 9:
                try:
                    lat = float(parts[9]) if len(parts) > 9 and parts[9] else None
                    lon = float(parts[10]) if len(parts) > 10 and parts[10] else None
                except ValueError:
                    lat = lon = None
                mounts.append({
                    "mountpoint": parts[1],
                    "identifier": parts[2],
                    "format": parts[3],
                    "format_details": parts[4],
                    "carrier": parts[5],
                    "nav_system": parts[6],
                    "network": parts[7],
                    "country": parts[8],
                    "latitude": lat,
                    "longitude": lon,
                })
    return mounts


@api_router.post("/ntrip/sourcetable")
async def ntrip_sourcetable(q: NtripQuery):
    try:
        loop = asyncio.get_event_loop()
        text = await loop.run_in_executor(
            None, _ntrip_get_sourcetable, q.host, q.port, q.username or "", q.password or ""
        )
        mounts = _parse_sourcetable(text)
        return {"caster": f"{q.host}:{q.port}", "mountpoints": mounts, "raw_lines": len(text.splitlines())}
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"NTRIP caster error: {e}")


def _rtcm_parse_message_type(buffer: bytes) -> Optional[int]:
    """If buffer starts with RTCM v3 preamble (0xD3), return the message type (12-bit)."""
    if len(buffer) < 6 or buffer[0] != 0xD3:
        return None
    # bytes 3-4 contain the 12-bit message number (top 12 bits)
    msg = (buffer[3] << 4) | (buffer[4] >> 4)
    return msg


def _ntrip_worker(session_id: str, host: str, port: int, mount: str, user: str, pw: str):
    """Background thread that streams RTCM from caster and updates session stats."""
    sess = NTRIP_SESSIONS[session_id]
    stop_event: threading.Event = sess["stop_event"]
    try:
        token = base64.b64encode(f"{user}:{pw}".encode()).decode() if user else ""
        auth = f"Authorization: Basic {token}\r\n" if token else ""
        req = (
            f"GET /{mount} HTTP/1.0\r\n"
            "User-Agent: NTRIP SmartGNSS/1.0\r\n"
            f"Host: {host}:{port}\r\n"
            "Accept: */*\r\n"
            "Ntrip-Version: Ntrip/2.0\r\n"
            f"{auth}"
            "\r\n"
        )
        s = socket.create_connection((host, port), timeout=10.0)
        s.sendall(req.encode())
        s.settimeout(2.0)
        header_buf = b""
        # Read response header
        while b"\r\n\r\n" not in header_buf:
            try:
                chunk = s.recv(512)
            except socket.timeout:
                continue
            if not chunk:
                break
            header_buf += chunk
            if stop_event.is_set():
                s.close()
                return
        if b"ICY 200" not in header_buf and b"HTTP/1.1 200" not in header_buf and b"HTTP/1.0 200" not in header_buf:
            sess["status"] = "error"
            sess["error"] = header_buf.decode(errors="replace")[:300]
            s.close()
            return
        sess["status"] = "streaming"
        # Stream RTCM payload
        rtcm_buf = b""
        while not stop_event.is_set():
            try:
                data = s.recv(2048)
            except socket.timeout:
                continue
            if not data:
                sess["status"] = "disconnected"
                break
            sess["bytes"] += len(data)
            sess["last_rx"] = datetime.now(timezone.utc).isoformat()
            rtcm_buf += data
            # Try to parse RTCM3 message types from the buffer
            while len(rtcm_buf) >= 6:
                if rtcm_buf[0] != 0xD3:
                    rtcm_buf = rtcm_buf[1:]
                    continue
                length = ((rtcm_buf[1] & 0x03) << 8) | rtcm_buf[2]
                total = 3 + length + 3  # header + payload + CRC24
                if len(rtcm_buf) < total:
                    break
                mtype = _rtcm_parse_message_type(rtcm_buf[:total])
                if mtype is not None:
                    sess["msg_types"][str(mtype)] = sess["msg_types"].get(str(mtype), 0) + 1
                rtcm_buf = rtcm_buf[total:]
        s.close()
    except Exception as e:
        sess["status"] = "error"
        sess["error"] = str(e)


@api_router.post("/ntrip/connect")
async def ntrip_connect(c: NtripConnect):
    session_id = str(uuid.uuid4())
    stop_event = threading.Event()
    with NTRIP_LOCK:
        NTRIP_SESSIONS[session_id] = {
            "id": session_id,
            "host": c.host,
            "port": c.port,
            "mountpoint": c.mountpoint,
            "username": c.username or "",
            "started_at": datetime.now(timezone.utc).isoformat(),
            "bytes": 0,
            "msg_types": {},
            "status": "connecting",
            "last_rx": None,
            "error": None,
            "stop_event": stop_event,
            "thread": None,
        }
    t = threading.Thread(
        target=_ntrip_worker,
        args=(session_id, c.host, c.port, c.mountpoint, c.username or "", c.password or ""),
        daemon=True,
    )
    NTRIP_SESSIONS[session_id]["thread"] = t
    t.start()
    return {"session_id": session_id, "status": "connecting"}


def _session_public(sess: Dict[str, Any]) -> Dict[str, Any]:
    return {k: v for k, v in sess.items() if k not in ("stop_event", "thread")}


@api_router.get("/ntrip/status/{session_id}")
async def ntrip_status(session_id: str):
    sess = NTRIP_SESSIONS.get(session_id)
    if not sess:
        raise HTTPException(status_code=404, detail="Session not found")
    return _session_public(sess)


@api_router.get("/ntrip/sessions")
async def ntrip_sessions():
    return {"sessions": [_session_public(s) for s in NTRIP_SESSIONS.values()]}


@api_router.delete("/ntrip/session/{session_id}")
async def ntrip_disconnect(session_id: str):
    sess = NTRIP_SESSIONS.get(session_id)
    if not sess:
        raise HTTPException(status_code=404, detail="Session not found")
    sess["stop_event"].set()
    sess["status"] = "disconnected"
    return {"disconnected": True}


# ==========================================================================
# CAD IMPORT  -  DXF and SHP -> GeoJSON overlays
# ==========================================================================
class CadOverlay(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    project_id: str
    filename: str
    file_type: str  # "dxf" | "shp"
    feature_count: int
    geojson: Dict[str, Any]
    bounds: Optional[List[float]] = None  # [minLon, minLat, maxLon, maxLat]
    created_at: str = Field(default_factory=now_iso)


def _bounds_from_features(features: List[Dict[str, Any]]) -> Optional[List[float]]:
    minLon = minLat = float("inf")
    maxLon = maxLat = float("-inf")
    def visit(coords):
        nonlocal minLon, minLat, maxLon, maxLat
        if not coords:
            return
        if isinstance(coords[0], (int, float)):
            lon, lat = coords[0], coords[1]
            if lon < minLon: minLon = lon
            if lon > maxLon: maxLon = lon
            if lat < minLat: minLat = lat
            if lat > maxLat: maxLat = lat
        else:
            for c in coords:
                visit(c)
    for f in features:
        g = f.get("geometry") or {}
        coords = g.get("coordinates")
        if coords:
            visit(coords)
    if minLon == float("inf"):
        return None
    return [minLon, minLat, maxLon, maxLat]


def _dxf_to_geojson(data: bytes) -> List[Dict[str, Any]]:
    """Convert DXF to GeoJSON features. Coordinates are treated as already in lon/lat WGS84."""
    with tempfile.NamedTemporaryFile(suffix=".dxf", delete=False) as f:
        f.write(data)
        path = f.name
    try:
        doc = ezdxf.readfile(path)
    finally:
        try:
            os.unlink(path)
        except OSError:
            pass
    msp = doc.modelspace()
    features: List[Dict[str, Any]] = []
    for e in msp:
        t = e.dxftype()
        try:
            if t == "LINE":
                features.append({
                    "type": "Feature",
                    "properties": {"layer": e.dxf.layer, "type": t},
                    "geometry": {
                        "type": "LineString",
                        "coordinates": [
                            [float(e.dxf.start.x), float(e.dxf.start.y)],
                            [float(e.dxf.end.x), float(e.dxf.end.y)],
                        ],
                    },
                })
            elif t in ("LWPOLYLINE", "POLYLINE"):
                pts = []
                if t == "LWPOLYLINE":
                    for x, y, *_ in e.get_points("xy"):
                        pts.append([float(x), float(y)])
                else:
                    for v in e.vertices:
                        pts.append([float(v.dxf.location.x), float(v.dxf.location.y)])
                if len(pts) >= 2:
                    is_closed = getattr(e, "closed", False) or (
                        len(pts) >= 3 and pts[0] == pts[-1]
                    )
                    if is_closed and len(pts) >= 3:
                        if pts[0] != pts[-1]:
                            pts.append(pts[0])
                        features.append({
                            "type": "Feature",
                            "properties": {"layer": e.dxf.layer, "type": t},
                            "geometry": {"type": "Polygon", "coordinates": [pts]},
                        })
                    else:
                        features.append({
                            "type": "Feature",
                            "properties": {"layer": e.dxf.layer, "type": t},
                            "geometry": {"type": "LineString", "coordinates": pts},
                        })
            elif t == "POINT":
                features.append({
                    "type": "Feature",
                    "properties": {"layer": e.dxf.layer, "type": t},
                    "geometry": {
                        "type": "Point",
                        "coordinates": [float(e.dxf.location.x), float(e.dxf.location.y)],
                    },
                })
            elif t == "CIRCLE":
                cx, cy = float(e.dxf.center.x), float(e.dxf.center.y)
                r = float(e.dxf.radius)
                pts = []
                for i in range(0, 49):
                    ang = 2 * math.pi * i / 48
                    pts.append([cx + r * math.cos(ang), cy + r * math.sin(ang)])
                pts.append(pts[0])
                features.append({
                    "type": "Feature",
                    "properties": {"layer": e.dxf.layer, "type": t, "radius": r},
                    "geometry": {"type": "Polygon", "coordinates": [pts]},
                })
            elif t == "ARC":
                cx, cy = float(e.dxf.center.x), float(e.dxf.center.y)
                r = float(e.dxf.radius)
                a0 = math.radians(float(e.dxf.start_angle))
                a1 = math.radians(float(e.dxf.end_angle))
                if a1 < a0:
                    a1 += 2 * math.pi
                steps = max(8, int((a1 - a0) * 24 / math.pi))
                pts = []
                for i in range(steps + 1):
                    ang = a0 + (a1 - a0) * i / steps
                    pts.append([cx + r * math.cos(ang), cy + r * math.sin(ang)])
                features.append({
                    "type": "Feature",
                    "properties": {"layer": e.dxf.layer, "type": t},
                    "geometry": {"type": "LineString", "coordinates": pts},
                })
            elif t == "TEXT" or t == "MTEXT":
                pos = e.dxf.insert if t == "MTEXT" else e.dxf.insert
                features.append({
                    "type": "Feature",
                    "properties": {
                        "layer": e.dxf.layer,
                        "type": t,
                        "text": e.dxf.text if t == "TEXT" else e.text,
                    },
                    "geometry": {"type": "Point", "coordinates": [float(pos.x), float(pos.y)]},
                })
        except Exception:
            continue
    return features


def _shp_to_geojson(shp_bytes: bytes, dbf_bytes: Optional[bytes] = None, shx_bytes: Optional[bytes] = None) -> List[Dict[str, Any]]:
    shp_io = io.BytesIO(shp_bytes)
    dbf_io = io.BytesIO(dbf_bytes) if dbf_bytes else None
    shx_io = io.BytesIO(shx_bytes) if shx_bytes else None
    reader = shapefile.Reader(shp=shp_io, dbf=dbf_io, shx=shx_io)
    features: List[Dict[str, Any]] = []
    fields = [f[0] for f in reader.fields[1:]]
    for sr in reader.iterShapeRecords():
        try:
            geo = sr.shape.__geo_interface__
            props: Dict[str, Any] = {}
            if dbf_io and sr.record:
                for i, name in enumerate(fields):
                    if i < len(sr.record):
                        v = sr.record[i]
                        props[name] = v if isinstance(v, (str, int, float, bool)) or v is None else str(v)
            features.append({"type": "Feature", "properties": props, "geometry": geo})
        except Exception:
            continue
    return features


MAX_CAD_BYTES = 10 * 1024 * 1024  # 10 MB


@api_router.post("/cad/upload", response_model=CadOverlay)
async def cad_upload(
    project_id: str = Form(...),
    file: UploadFile = File(...),
    dbf: Optional[UploadFile] = File(None),
    shx: Optional[UploadFile] = File(None),
):
    proj = await db.projects.find_one({"id": project_id}, PROJECTION)
    if not proj:
        raise HTTPException(status_code=404, detail="Project not found")
    blob = await file.read()
    if len(blob) > MAX_CAD_BYTES:
        raise HTTPException(status_code=413, detail=f"File exceeds {MAX_CAD_BYTES // (1024*1024)} MB cap")
    fn = (file.filename or "").lower()
    try:
        if fn.endswith(".dxf"):
            features = _dxf_to_geojson(blob)
            file_type = "dxf"
        elif fn.endswith(".shp"):
            dbf_bytes = (await dbf.read()) if dbf else None
            shx_bytes = (await shx.read()) if shx else None
            features = _shp_to_geojson(blob, dbf_bytes, shx_bytes)
            file_type = "shp"
        else:
            raise HTTPException(status_code=400, detail="Only .dxf and .shp accepted")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Parse error: {e}")
    geojson = {"type": "FeatureCollection", "features": features}
    overlay = CadOverlay(
        project_id=project_id,
        filename=file.filename or "upload",
        file_type=file_type,
        feature_count=len(features),
        geojson=geojson,
        bounds=_bounds_from_features(features),
    )
    await db.cad_overlays.insert_one(overlay.model_dump())
    return overlay


@api_router.get("/cad", response_model=List[CadOverlay])
async def list_cad(project_id: str):
    docs = await db.cad_overlays.find({"project_id": project_id}, PROJECTION).sort("created_at", -1).to_list(100)
    return [CadOverlay(**d) for d in docs]


@api_router.get("/cad/{overlay_id}", response_model=CadOverlay)
async def get_cad(overlay_id: str):
    doc = await db.cad_overlays.find_one({"id": overlay_id}, PROJECTION)
    if not doc:
        raise HTTPException(status_code=404, detail="Overlay not found")
    return CadOverlay(**doc)


@api_router.delete("/cad/{overlay_id}")
async def delete_cad(overlay_id: str):
    result = await db.cad_overlays.delete_one({"id": overlay_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Overlay not found")
    return {"deleted": True}


app.include_router(api_router)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


@app.on_event("shutdown")
async def shutdown_db_client():
    client.close()
