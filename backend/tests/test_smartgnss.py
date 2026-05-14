"""SmartGNSS backend API tests.

Covers: health, projects CRUD, points CRUD, CSV/KML export, COGO endpoints,
and mocked devices scan.
"""
import math
import os
import re
import uuid

import pytest
import requests

BASE_URL = os.environ["EXPO_PUBLIC_BACKEND_URL"].rstrip("/")
API = f"{BASE_URL}/api"

TEST_PREFIX = "TEST_"


@pytest.fixture(scope="session")
def session():
    s = requests.Session()
    s.headers.update({"Content-Type": "application/json"})
    return s


# -------------------- Health --------------------
def test_health_root(session):
    r = session.get(f"{API}/")
    assert r.status_code == 200
    data = r.json()
    assert data.get("app") == "SmartGNSS"
    assert data.get("status") == "ok"


# -------------------- Projects CRUD --------------------
class TestProjects:
    created_ids = []

    def test_create_project(self, session):
        payload = {
            "name": f"{TEST_PREFIX}Project_{uuid.uuid4().hex[:6]}",
            "description": "test desc",
            "coordinate_system": "WGS84",
        }
        r = session.post(f"{API}/projects", json=payload)
        assert r.status_code == 200, r.text
        data = r.json()
        assert data["name"] == payload["name"]
        assert data["coordinate_system"] == "WGS84"
        assert data["point_count"] == 0
        assert "id" in data and len(data["id"]) > 0
        TestProjects.created_ids.append(data["id"])

    def test_list_projects_contains_created(self, session):
        r = session.get(f"{API}/projects")
        assert r.status_code == 200
        items = r.json()
        assert isinstance(items, list)
        ids = {p["id"] for p in items}
        assert TestProjects.created_ids[0] in ids

    def test_get_project_by_id(self, session):
        pid = TestProjects.created_ids[0]
        r = session.get(f"{API}/projects/{pid}")
        assert r.status_code == 200
        data = r.json()
        assert data["id"] == pid
        assert "point_count" in data

    def test_get_project_404(self, session):
        r = session.get(f"{API}/projects/{uuid.uuid4().hex}")
        assert r.status_code == 404

    def test_zz_cleanup_delete_projects(self, session):
        # Run last (zz prefix) - keeps an extra project alive for subsequent class via create
        # Delete only the ones we created here at the end of this class
        pass  # cleanup performed at module end


# -------------------- Points CRUD --------------------
@pytest.fixture(scope="module")
def project_id(session):
    payload = {"name": f"{TEST_PREFIX}PointProj_{uuid.uuid4().hex[:6]}"}
    r = session.post(f"{API}/projects", json=payload)
    assert r.status_code == 200
    pid = r.json()["id"]
    yield pid
    # cleanup
    session.delete(f"{API}/projects/{pid}")


class TestPoints:
    point_ids = []

    def test_create_point_requires_project(self, session):
        r = session.post(
            f"{API}/points",
            json={
                "project_id": uuid.uuid4().hex,
                "name": "P1",
                "latitude": 12.0,
                "longitude": 77.0,
            },
        )
        assert r.status_code == 404

    def test_create_point_ok(self, session, project_id):
        body = {
            "project_id": project_id,
            "name": "P1",
            "code": "BLD",
            "latitude": 12.9716,
            "longitude": 77.5946,
            "altitude": 920.5,
            "accuracy_h": 0.012,
            "accuracy_v": 0.022,
            "antenna_height": 1.8,
            "fix_type": "Fixed",
            "satellites": 18,
            "note": "corner1",
        }
        r = session.post(f"{API}/points", json=body)
        assert r.status_code == 200, r.text
        data = r.json()
        assert data["project_id"] == project_id
        assert data["name"] == "P1"
        assert math.isclose(data["latitude"], 12.9716)
        assert data["fix_type"] == "Fixed"
        TestPoints.point_ids.append(data["id"])

    def test_create_more_points(self, session, project_id):
        for i, (lat, lon) in enumerate([(12.9720, 77.5950), (12.9722, 77.5944)]):
            r = session.post(
                f"{API}/points",
                json={
                    "project_id": project_id,
                    "name": f"P{i+2}",
                    "latitude": lat,
                    "longitude": lon,
                    "altitude": 920.0,
                },
            )
            assert r.status_code == 200
            TestPoints.point_ids.append(r.json()["id"])

    def test_list_points(self, session, project_id):
        r = session.get(f"{API}/points", params={"project_id": project_id})
        assert r.status_code == 200
        items = r.json()
        assert len(items) >= 3
        ids = {p["id"] for p in items}
        for pid in TestPoints.point_ids:
            assert pid in ids

    def test_project_point_count_updated(self, session, project_id):
        r = session.get(f"{API}/projects/{project_id}")
        assert r.status_code == 200
        assert r.json()["point_count"] >= 3

    def test_delete_point(self, session, project_id):
        pid = TestPoints.point_ids[-1]
        r = session.delete(f"{API}/points/{pid}")
        assert r.status_code == 200
        assert r.json().get("deleted") is True

        # verify gone via list
        r2 = session.get(f"{API}/points", params={"project_id": project_id})
        ids = {p["id"] for p in r2.json()}
        assert pid not in ids
        TestPoints.point_ids.pop()

    def test_delete_point_404(self, session):
        r = session.delete(f"{API}/points/{uuid.uuid4().hex}")
        assert r.status_code == 404


# -------------------- Export --------------------
class TestExport:
    def test_csv_export(self, session, project_id):
        r = session.get(f"{API}/export/csv/{project_id}")
        assert r.status_code == 200
        text = r.text
        first_line = text.splitlines()[0]
        assert "Name" in first_line and "Latitude" in first_line and "Longitude" in first_line
        # At least header + 2 rows remaining after delete
        assert len(text.splitlines()) >= 3

    def test_csv_export_404(self, session):
        r = session.get(f"{API}/export/csv/{uuid.uuid4().hex}")
        assert r.status_code == 404

    def test_kml_export(self, session, project_id):
        r = session.get(f"{API}/export/kml/{project_id}")
        assert r.status_code == 200
        body = r.text
        assert body.startswith("<?xml")
        assert "<kml" in body and "</kml>" in body
        # should contain at least one Placemark
        assert "<Placemark>" in body
        # coordinates lon,lat,alt
        assert re.search(r"<coordinates>[-\d.]+,[-\d.]+,[-\d.]+</coordinates>", body)

    def test_kml_export_404(self, session):
        r = session.get(f"{API}/export/kml/{uuid.uuid4().hex}")
        assert r.status_code == 404


# -------------------- COGO --------------------
class TestCogo:
    def test_cogo_inverse(self, session):
        # Two points ~ 111 km apart (1 degree latitude)
        r = session.post(
            f"{API}/cogo/inverse",
            json={"lat1": 0.0, "lon1": 0.0, "lat2": 1.0, "lon2": 0.0},
        )
        assert r.status_code == 200
        d = r.json()
        assert isinstance(d["distance_m"], (int, float))
        assert isinstance(d["bearing_deg"], (int, float))
        # ~111195m for 1 deg of lat (great-circle on R=6371000)
        assert 110000 < d["distance_m"] < 112000
        assert abs(d["bearing_deg"] - 0.0) < 0.5 or abs(d["bearing_deg"] - 360.0) < 0.5

    def test_cogo_inverse_bearing_east(self, session):
        r = session.post(
            f"{API}/cogo/inverse",
            json={"lat1": 0.0, "lon1": 0.0, "lat2": 0.0, "lon2": 1.0},
        )
        assert r.status_code == 200
        d = r.json()
        assert abs(d["bearing_deg"] - 90.0) < 1.0

    def test_cogo_area_less_than_3(self, session):
        r = session.post(
            f"{API}/cogo/area",
            json={"points": [{"lat": 0, "lon": 0}, {"lat": 0, "lon": 1}]},
        )
        assert r.status_code == 200
        assert r.json() == {"area_m2": 0.0, "perimeter_m": 0.0}

    def test_cogo_area_triangle(self, session):
        # Small triangle ~100m sides near equator
        # 0.001 deg ≈ 111m
        pts = [
            {"lat": 0.0, "lon": 0.0},
            {"lat": 0.0, "lon": 0.001},
            {"lat": 0.001, "lon": 0.0},
        ]
        r = session.post(f"{API}/cogo/area", json={"points": pts})
        assert r.status_code == 200
        d = r.json()
        # Area of right triangle: 0.5 * 111.32 * 111.32 ≈ 6196 m^2
        assert 5000 < d["area_m2"] < 7500
        assert 350 < d["perimeter_m"] < 450


# -------------------- Devices (mocked) --------------------
def test_devices_scan(session):
    r = session.get(f"{API}/devices/scan")
    assert r.status_code == 200
    data = r.json()
    assert "devices" in data
    assert len(data["devices"]) == 6
    sample = data["devices"][0]
    for k in ("id", "name", "type", "rssi"):
        assert k in sample


# -------------------- Project delete cascades points --------------------
def test_delete_project_removes_points(session):
    # create new project + point, delete project, ensure points gone
    pj = session.post(f"{API}/projects", json={"name": f"{TEST_PREFIX}Cascade"}).json()
    pid = pj["id"]
    pt = session.post(
        f"{API}/points",
        json={"project_id": pid, "name": "p", "latitude": 1, "longitude": 2},
    )
    assert pt.status_code == 200
    r = session.delete(f"{API}/projects/{pid}")
    assert r.status_code == 200

    # Confirm 404 on get
    r2 = session.get(f"{API}/projects/{pid}")
    assert r2.status_code == 404

    # Listing points for deleted project returns empty
    r3 = session.get(f"{API}/points", params={"project_id": pid})
    assert r3.status_code == 200
    assert r3.json() == []


def test_delete_project_404(session):
    r = requests.delete(f"{API}/projects/{uuid.uuid4().hex}")
    assert r.status_code == 404


# -------------------- Cleanup TEST_ projects --------------------
def test_zzzz_cleanup(session):
    r = session.get(f"{API}/projects")
    for p in r.json():
        if p["name"].startswith(TEST_PREFIX):
            session.delete(f"{API}/projects/{p['id']}")
