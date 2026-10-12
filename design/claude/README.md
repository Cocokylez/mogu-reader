# Tome Glass design import

These files were extracted verbatim from `Tome Glass (standalone).html`, the
standalone Claude Design export supplied for this project:

- `Tome Glass.dc.html` — source canvas and interaction state
- `ios-frame.jsx` — preview-only device frame
- `support.js` — generated Claude Design runtime

The Android app implements the canvas in Jetpack Compose. It does not execute or
package `support.js`, and it does not reproduce the fake iOS frame. The product
adaptation keeps the Library, Discover, History, Settings, series, and image
reader glass language while excluding the canvas's novel, EPUB/TXT, Project
Gutenberg, text-reader, and typography flows. AquaReader and MangaDex are exposed
as featured sources backed by the app's real integrations.

## Source checksums (SHA-256)

- `Tome Glass.dc.html`: `CEDBBDB6097774B835B3CC125908D4D514FCB38899921DE1F73C62F28EC99792`
- `ios-frame.jsx`: `24642B887BE3D26AB5D1FF445AD98C363CFFD33746C539A117DA570AC7B6EB54`
- `support.js`: `8FE7DF74405F3C55F49B7249C74EA1397E65D07DEA2B1BD3B4A489BEC2E28CBE`
