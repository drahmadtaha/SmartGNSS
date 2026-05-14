from fastapi import FastAPI, APIRouter, HTTPException
from fastapi.responses import PlainTextResponse
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import logging
import math
from pathlib import Path
from pydantic import BaseModel, Field
from typing import List, Optional
import uuid
from datetime import datetime, timezone


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
