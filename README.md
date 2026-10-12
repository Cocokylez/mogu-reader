# Mogu Reader — Manga & Manhwa Reader for Android

Mogu Reader is a focused, ad-free Android reader for manga, manhwa, manhua, and other
page-based comics. It reads local comic files and can browse supported online
sources. It has no accounts, analytics, ads, or trackers.

Current app version: **2.2.0** (`versionCode 12`). Android 8.0 (API 26) or newer
is required.

Version 2.0 implements the imported Tome Glass design canvas with an ambient glass
backdrop, floating navigation, continue-reading hero, comic import sheet,
featured sources, grouped history and settings cards, glass chapter rows, and
floating reader controls. The original Claude Design sources are preserved under
`design/claude` for traceability.

## What it reads

- CBZ, CBR, ZIP, and RAR comic archives
- Image files and image folders, including chapter subfolders
- PDF files rendered as comic pages
- Downloaded and streamed chapters from supported online sources

New EPUB and TXT novel imports are intentionally unsupported. Existing legacy
novel rows are left untouched in the database and hidden from the manga library,
so updating the app does not delete user data.

## Reader

- Vertical webtoon scrolling, left-to-right pages, and right-to-left manga pages
- Auto-scroll with adjustable speed
- Fit-width comic rendering without page cropping
- Pinch and double-tap zoom in paged mode
- Page seek, chapter navigation, volume-key paging, focus mode, and screen-on option
- Exact chapter and scroll-position resume for local and online reading
- Bounded image decoding and a small streaming cache for long chapters

## Sources

The Discover screen gives AquaReader and MangaDex dedicated featured cards. Its
integrated feed uses the same main catalogue sources as the desktop app:

- MangaDex, through its public API
- WeebCentral
- Asura Scans
- Flame Comics

Additional manga/manhwa sources and official website shortcuts are available from
**Discover → Sources**. AquaReader is included as an in-app website source because
the site currently rejects direct catalogue requests; Mogu Reader does not pretend that
search, imports, or downloads work when the source blocks them.

Source websites can change or become temporarily unavailable. Mogu Reader shows source
errors, limits concurrent requests, times out stalled calls, and lets the user open
the website when an integrated chapter is unavailable. It does not bypass logins,
paywalls, DRM, or other access controls.

## Library and downloads

### Library layouts

The layout row above the library switches between four presentations, and the
choice is remembered across restarts:

- **Grid** — the adaptive cover grid (default)
- **Shelves** — horizontal rails, series first and then grouped by comic format
- **List** — a dense single-column view with progress on each row
- **Cards** — a browsable deck for picking up something to read

- Import individual comic files or a complete folder through Android's file picker
- Group chapter files and image subfolders into a series
- Search, sort, filter, tag, follow, and track reading history
- Stream online chapters or download unread chapters for offline reading
- Check followed series for new chapters in the background
- Export and restore app data with ID-safe merging

Removing a local library entry never deletes the user's original file. Mogu Reader only
deletes downloaded files inside its own private download directory.

## Build and install

The project targets Android SDK 34 and Java 17.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The build output is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install or update it on a connected Android device with:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The convenient copy at the project root is `mogu.apk`.

## Architecture

Mogu Reader is a single-module Kotlin app using Jetpack Compose, Room, DataStore, Coil,
WorkManager, OkHttp, and Jsoup. Local format readers live under `data/source`,
online integrations under `scraper`, and the Compose UI under `ui`.

The Room schema retains legacy enum values for safe upgrades and backup restores,
while all visible queries and new imports enforce the manga/manhwa-only product
scope.

## Privacy and permissions

Online browsing and chapter downloads require `INTERNET` and network-state access.
Notifications are optional and are used for downloads and followed-series updates.
WorkManager supplies the wake-lock and boot permissions needed to reschedule those
checks. Local reading works offline after content has been imported or downloaded.
