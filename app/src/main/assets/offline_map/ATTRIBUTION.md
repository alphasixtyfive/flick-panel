# Offline basemap

Made with Natural Earth. The bundled data is public domain. [Terms of use](https://www.naturalearthdata.com/about/terms-of-use/)

`world.fpm` contains countries, lakes, and selected populated places from the 1:50 million GeoJSON layers in Natural Earth's v5.1.2 release. It provides a geographic overview at country and city scale. Android compresses this file as part of the APK. [Pinned source](https://github.com/nvkelso/natural-earth-vector/tree/v5.1.2/geojson)

To rebuild from the repository root, download `ne_50m_admin_0_countries.geojson`, `ne_50m_lakes.geojson`, and `ne_50m_populated_places_simple.geojson` from the pinned source. Run `python tools/build_offline_basemap.py <geojson-directory>`.
