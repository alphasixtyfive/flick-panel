"""Pack the Natural Earth GeoJSON layers listed in the asset's attribution."""

import argparse
import json
import struct
from pathlib import Path


def features(root, layer):
    return json.loads((root / f"{layer}.geojson").read_text(encoding="utf-8"))["features"]


def project(coordinates):
    longitude, latitude = coordinates[:2]
    return round((longitude + 180) * 1000), round((90 - latitude) * 1000)


def rings(layer):
    for feature in layer:
        shape = feature["geometry"]
        if shape is None:
            continue
        polygons = [shape["coordinates"]] if shape["type"] == "Polygon" else shape["coordinates"]
        for polygon in polygons:
            for ring in polygon:
                points = []
                for point in map(project, ring):
                    if not points or points[-1] != point:
                        points.append(point)
                if len(points) > 1 and points[0] == points[-1]:
                    points.pop()
                if len(set(points)) >= 3:
                    yield points


def write_layer(output, layer):
    paths = list(rings(layer))
    output.write(struct.pack(">I", len(paths)))
    for points in paths:
        output.write(struct.pack(">I", len(points)))
        for point in points:
            output.write(struct.pack(">II", *point))


def write_places(output, layer):
    places = []
    for feature in layer:
        properties = feature["properties"]
        name = properties["name"].encode("utf-8")
        point = project(feature["geometry"]["coordinates"])
        places.append((properties["scalerank"], -properties["pop_max"], point, name))
    places.sort()
    output.write(struct.pack(">I", len(places)))
    for rank, _, point, name in places:
        output.write(struct.pack(">IIBH", *point, rank, len(name)))
        output.write(name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("geojson", type=Path, help="Directory containing the pinned Natural Earth layers")
    parser.add_argument("--output", type=Path,
                        default=Path("app/src/main/assets/offline_map/world.fpm"))
    arguments = parser.parse_args()

    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    with arguments.output.open("wb") as output:
        output.write(b"FPM1")
        write_layer(output, features(arguments.geojson, "ne_50m_admin_0_countries"))
        write_layer(output, features(arguments.geojson, "ne_50m_lakes"))
        write_places(output, features(arguments.geojson, "ne_50m_populated_places_simple"))


if __name__ == "__main__":
    main()
