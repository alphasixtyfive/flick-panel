"""Pack public OpenStreetMap geometry exported by Overpass into the offline map assets."""

import argparse
import json
import struct
from collections import defaultdict
from pathlib import Path

from asset_output import open_asset

SCALE = 100_000
CELL = 4_000
PARK, WATER, RIVER, LOCAL_ROAD, MAIN_ROAD = range(5)

REGIONS = {
    "london": (51.47, -0.35, 51.71, 0.10),
    "varna": (43.10, 27.72, 43.34, 28.09),
}


def coordinate(latitude, longitude):
    return round((longitude + 180) * SCALE), round((90 - latitude) * SCALE)


def layer_for(tags):
    highway = tags.get("highway")
    if highway:
        return MAIN_ROAD if highway in {"motorway", "trunk", "primary", "secondary", "tertiary"} else LOCAL_ROAD
    if tags.get("waterway") in {"river", "canal"}:
        return RIVER
    if tags.get("natural") == "water" or tags.get("waterway") == "riverbank":
        return WATER
    if tags.get("leisure") or tags.get("landuse") or tags.get("natural") == "wood":
        return PARK
    return None


def feature_points(geometry, polygon):
    raw = [coordinate(node["lat"], node["lon"]) for node in geometry]
    if len(raw) < 2 or polygon and raw[0] != raw[-1]:
        return []
    points = [raw[0]]
    for x, y in raw[1:-1]:
        last_x, last_y = points[-1]
        if (x - last_x)**2 + (y - last_y)**2 >= 9:
            points.append((x, y))
    if raw[-1] != points[-1] and not polygon:
        points.append(raw[-1])
    return points if len(points) >= (3 if polygon else 2) else []


def bounds(points):
    return min(x for x, _ in points), min(y for _, y in points), max(x for x, _ in points), max(y for _, y in points)


def pack_region(source, output, coverage):
    data = json.loads(source.read_text(encoding="utf-8"))
    if not data.get("elements") or data.get("remark"):
        raise ValueError(f"Incomplete map source {source}")
    areas = defaultdict(lambda: [[] for _ in range(5)])
    labels = {}
    for element in data["elements"]:
        tags = element.get("tags", {})
        layer = layer_for(tags)
        if layer is None or not element.get("geometry"):
            continue
        points = feature_points(element["geometry"], polygon=layer in (PARK, WATER))
        if not points:
            continue
        x, y = points[len(points) // 2]
        cell = x // CELL, y // CELL
        areas[cell][layer].append(points)
        name = tags.get("name")
        if layer == MAIN_ROAD and name and len(name.encode("utf-8")) <= 160:
            labels.setdefault((cell, name), (x, y))

    south, west, north, east = coverage
    first, last = coordinate(north, west), coordinate(south, east)
    with open_asset(output) as target:
        target.write(struct.pack(">4s4iI", b"FPD1", *first, *last, len(areas)))
        for layers in areas.values():
            target.write(struct.pack(">4i", *bounds([point for layer in layers for line in layer for point in line])))
            for layer in layers:
                target.write(struct.pack(">I", len(layer)))
                for path in layer:
                    target.write(struct.pack(">I", len(path)))
                    for point in path:
                        target.write(struct.pack(">2i", *point))
        target.write(struct.pack(">I", len(labels)))
        for (_, name), point in labels.items():
            raw = name.encode("utf-8")
            target.write(struct.pack(">2iH", *point, len(raw)))
            target.write(raw)
    print(output.name, len(data["elements"]), len(areas), output.stat().st_size,
          data.get("osm3s", {}).get("timestamp_osm_base", "unknown date"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sources", type=Path, help="Directory containing london.json and varna.json Overpass exports")
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/offline_map"))
    args = parser.parse_args()
    for region, coverage in REGIONS.items():
        pack_region(args.sources / f"{region}.json", args.output / f"{region}.fpd", coverage)


if __name__ == "__main__":
    main()
