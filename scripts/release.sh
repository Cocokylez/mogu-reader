#!/usr/bin/env bash
# Ship a new Mogu Reader version.
#
#   scripts/release.sh 2.10.1 "Fixed the Toonily covers"
#
# Bumps the version, builds a signed release APK, pushes the commit and tag, and
# publishes a GitHub Release with the APK attached. Every installed copy picks
# that release up on its next launch, because the app checks this repository's
# latest release and offers to install it.
#
# Requires: JAVA_HOME pointing at a JDK 17, the gh CLI logged in, and
# keystore.properties present (the signing key -- an update will not install
# over an existing copy if the signature changes).
set -euo pipefail

VERSION="${1:-}"
NOTES="${2:-}"
if [[ -z "$VERSION" ]]; then
  echo "usage: scripts/release.sh <version> [release notes]" >&2
  echo "   eg: scripts/release.sh 2.10.1 \"Fixed the Toonily covers\"" >&2
  exit 1
fi

cd "$(dirname "$0")/.."
GRADLE_FILE="app/build.gradle.kts"

if [[ ! -f keystore.properties ]]; then
  echo "keystore.properties is missing: the build would fall back to the debug" >&2
  echo "key and the APK could not install over an existing copy. Restore it from" >&2
  echo "your keystore backup before releasing." >&2
  exit 1
fi

CURRENT_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE_FILE")
NEXT_CODE=$((CURRENT_CODE + 1))

# versionCode is what Android compares when deciding whether an APK is an
# upgrade, so it has to climb on every single release.
sed -i "s/versionCode = ${CURRENT_CODE}/versionCode = ${NEXT_CODE}/" "$GRADLE_FILE"
sed -i "s/versionName = \"[^\"]*\"/versionName = \"${VERSION}\"/" "$GRADLE_FILE"
echo "==> version ${VERSION} (code ${NEXT_CODE})"

echo "==> building signed release"
./gradlew assembleRelease --console=plain -q

APK="Mogu-Reader-${VERSION}.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"

echo "==> committing and tagging"
git add -A
git commit -q -m "Mogu Reader ${VERSION}${NOTES:+

${NOTES}}"
git tag -a "v${VERSION}" -m "Mogu Reader ${VERSION}"
git push -q origin main
git push -q origin "v${VERSION}"

echo "==> publishing release"
gh release create "v${VERSION}" "$APK" \
  --title "Mogu Reader ${VERSION}" \
  --notes "${NOTES:-Maintenance release.}"

rm -f "$APK"
echo "==> done. Installed copies will offer ${VERSION} on their next launch."
