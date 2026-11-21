# Offline basemap

- `world.fpm` is made with Natural Earth. The public-domain 1:50 million layers
  come from the [v5.1.2 release](https://github.com/nvkelso/natural-earth-vector/tree/v5.1.2/geojson).
  See the [Natural Earth terms](https://www.naturalearthdata.com/about/terms-of-use/).
- `london.fpd` covers north and central London from 51.47°N to 51.71°N and
  0.35°W to 0.10°E. The data snapshot is 2026-11-21 13:06 UTC.
- `varna.fpd` covers Varna and nearby areas from 43.10°N to 43.34°N and
  27.72°E to 28.09°E. The data snapshot is 2026-11-21 13:00 UTC.

Both `.fpd` files contain data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright)
under the [Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/).
The files are distributed under that licence. The viewer draws the data locally and
links to the OpenStreetMap credits. The offline map contains public map geometry.
Person positions come from the connected Home Assistant instance at runtime.

## Build the map assets

For the worldwide overview, download `ne_50m_admin_0_countries.geojson`,
`ne_50m_lakes.geojson`, and `ne_50m_populated_places_simple.geojson` from the
pinned Natural Earth release. Run `python tools/build_offline_basemap.py <geojson-directory>`.

For regional detail, export OpenStreetMap ways with geometry to `london.json` and
`varna.json`. Use the bounds above in this Overpass query.

```text
[out:json][timeout:90][bbox:SOUTH,WEST,NORTH,EAST];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|residential|unclassified|living_street)$"];
  way["landuse"~"^(forest|recreation_ground)$"];
  way["leisure"~"^(park|garden|nature_reserve)$"];
  way["natural"~"^(water|wood)$"];
  way["waterway"~"^(river|canal|riverbank)$"];
);
out geom;
```

Run `python tools/build_offline_detail.py <export-directory>` from the repository
root. The packer writes the coverage, cell bounds, five geometry layers and road
labels with big-endian lengths. Positions use a grid of 100,000 units per degree,
with longitude offset by 180° and latitude measured southward from 90°N. The APK
contains the packed assets. The map makes zero tile requests.
