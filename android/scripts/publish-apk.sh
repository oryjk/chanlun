#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$ANDROID_DIR/.." && pwd)"

PUBLISH_DIR="${PUBLISH_DIR:-/data7t/apk-downloads/chanlun-android}"
PUBLIC_URL="${PUBLIC_URL:-http://172.16.60.233/chanlun-android/}"
EXTERNAL_URL="${EXTERNAL_URL:-https://ai.oryjk.cn:82/chanlun-android/}"
RETAIN_RELEASES="${RETAIN_RELEASES:-10}"
APK_PATH="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
GRADLE_FILE="$ANDROID_DIR/app/build.gradle.kts"
DIST_INDEX="$ANDROID_DIR/distribution/index.html"

SKIP_BUILD=0
NOTES_FILE=""
CLI_NOTES=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --note)
      if [[ $# -lt 2 ]]; then
        echo "Error: --note requires an argument" >&2
        exit 1
      fi
      CLI_NOTES+=("$2")
      shift 2
      ;;
    --notes-file)
      if [[ $# -lt 2 ]]; then
        echo "Error: --notes-file requires an argument" >&2
        exit 1
      fi
      NOTES_FILE="$2"
      shift 2
      ;;
    -h|--help)
      cat <<'EOF'
Usage: publish-apk.sh [--skip-build] [--note "item"]... [--notes-file path]
Builds debug APK (unless --skip-build), publishes to 7T disk with releases.json retention,
and verifies both LAN (172.16.60.233) and external (ai.oryjk.cn:82) endpoints.
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

export ANDROID_HOME="${ANDROID_HOME:-/home/betalpha/Android/Sdk}"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  echo "==> Building Android debug APK..."
  (cd "$ANDROID_DIR" && ./gradlew assembleDebug)
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "Error: APK not found at $APK_PATH" >&2
  exit 1
fi

eval "$(
  python3 - "$GRADLE_FILE" <<'PY'
import re
import shlex
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding="utf-8")
app_id_m = re.search(r'applicationId\s*=\s*"([^"]+)"', text)
ver_code_m = re.search(r'versionCode\s*=\s*(\d+)', text)
ver_name_m = re.search(r'versionName\s*=\s*"([^"]+)"', text)
debug_suffix_m = re.search(r'debug\s*\{[^}]*?applicationIdSuffix\s*=\s*"([^"]+)"', text, re.S)

base_id = app_id_m.group(1) if app_id_m else "cn.oryjk.changuan"
suffix = debug_suffix_m.group(1) if debug_suffix_m else ""
app_id = base_id + suffix
ver_code = ver_code_m.group(1) if ver_code_m else "1"
ver_name = ver_name_m.group(1) if ver_name_m else "1.0.0"

print(f"APPLICATION_ID={shlex.quote(app_id)}")
print(f"VERSION_CODE={shlex.quote(ver_code)}")
print(f"VERSION_NAME={shlex.quote(ver_name)}")
PY
)"

# Double-check actual APK package name if aapt2 exists in Android SDK
AAPT2_BIN="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name aapt2 2>/dev/null | sort -V | tail -n 1 || true)"
if [[ -n "$AAPT2_BIN" && -x "$AAPT2_BIN" ]]; then
  APK_PKG="$("$AAPT2_BIN" dump packagename "$APK_PATH" 2>/dev/null || true)"
  if [[ -n "$APK_PKG" && "$APK_PKG" != "$APPLICATION_ID" ]]; then
    echo "Error: APK package ($APK_PKG) does not match expected ($APPLICATION_ID)" >&2
    exit 1
  fi
fi

PUBLISHED_AT="$(TZ=Asia/Shanghai date '+%Y-%m-%d %H:%M:%S %z')"
STAMP="$(TZ=Asia/Shanghai date '+%Y%m%d%H%M')"
RELEASE_REL="releases/changuan-v${VERSION_NAME}-b${VERSION_CODE}-${STAMP}.apk"
GIT_COMMIT="$(git -C "$REPO_DIR" rev-parse --short=7 HEAD 2>/dev/null || echo 'unknown')"
SHA256="$(sha256sum "$APK_PATH" | awk '{print $1}')"
FILE_SIZE_BYTES="$(stat -c '%s' "$APK_PATH")"

mkdir -p "$PUBLISH_DIR/releases"

TMP_RELEASE="$PUBLISH_DIR/${RELEASE_REL}.tmp.$$"
TMP_LATEST="$PUBLISH_DIR/latest.apk.tmp.$$"
cp "$APK_PATH" "$TMP_RELEASE"
chmod 0644 "$TMP_RELEASE"
mv -f "$TMP_RELEASE" "$PUBLISH_DIR/$RELEASE_REL"

cp "$APK_PATH" "$TMP_LATEST"
chmod 0644 "$TMP_LATEST"
mv -f "$TMP_LATEST" "$PUBLISH_DIR/latest.apk"

if [[ -f "$DIST_INDEX" ]]; then
  cp "$DIST_INDEX" "$PUBLISH_DIR/index.html.tmp.$$"
  chmod 0644 "$PUBLISH_DIR/index.html.tmp.$$"
  mv -f "$PUBLISH_DIR/index.html.tmp.$$" "$PUBLISH_DIR/index.html"
fi

NOTES_JSON="$(
  python3 - "$REPO_DIR" "$NOTES_FILE" "${CLI_NOTES[@]+"${CLI_NOTES[@]}"}" <<'PY'
import json
import subprocess
import sys
from pathlib import Path

repo_dir = Path(sys.argv[1])
notes_file = sys.argv[2]
cli_notes = [n.strip() for n in sys.argv[3:] if n.strip()]

notes = []
if cli_notes:
    notes.extend(cli_notes)
if notes_file:
    p = Path(notes_file)
    if p.is_file():
        for line in p.read_text(encoding="utf-8").splitlines():
            clean = line.strip().lstrip("-*• ").strip()
            if clean:
                notes.append(clean)
if not notes:
    try:
        out = subprocess.check_output(
            ["git", "-C", str(repo_dir), "log", "-n", "3", "--pretty=format:%s"],
            text=True,
        )
        notes = [line.strip() for line in out.splitlines() if line.strip()]
    except Exception:
        notes = []
if not notes:
    notes = ["常规体验优化与问题修复"]

print(json.dumps(notes, ensure_ascii=False))
PY
)"

python3 - "$PUBLISH_DIR" "$VERSION_NAME" "$VERSION_CODE" "$PUBLISHED_AT" "$GIT_COMMIT" \
  "$NOTES_JSON" "$RELEASE_REL" "$SHA256" "$FILE_SIZE_BYTES" "$APPLICATION_ID" "$RETAIN_RELEASES" <<'PY'
import json
import os
import sys
from pathlib import Path

publish_dir = Path(sys.argv[1])
version_name = sys.argv[2]
version_code = sys.argv[3]
published_at = sys.argv[4]
git_commit = sys.argv[5]
notes = json.loads(sys.argv[6])
release_rel = sys.argv[7]
sha256 = sys.argv[8]
file_size = int(sys.argv[9])
app_id = sys.argv[10]
retain = int(sys.argv[11])

entry = {
    "versionName": version_name,
    "versionCode": str(version_code),
    "publishedAt": published_at,
    "gitCommit": git_commit,
    "releaseNotes": notes,
    "releaseFile": release_rel,
    "sha256": sha256,
    "fileSizeBytes": file_size,
    "applicationId": app_id,
    "apkAvailable": True,
}

releases_path = publish_dir / "releases.json"
existing = []
if releases_path.is_file():
    try:
        data = json.loads(releases_path.read_text(encoding="utf-8"))
        if isinstance(data, list):
            existing = data
    except Exception:
        existing = []

filtered = [
    item for item in existing
    if isinstance(item, dict) and (
        str(item.get("versionCode")) != str(version_code)
        or str(item.get("sha256")) != sha256
    )
]
updated = [entry] + filtered

# Retain latest N release APKs on disk and mark older entries archived
kept_files = set()
active_count = 0
for item in updated:
    rel_file = item.get("releaseFile")
    if rel_file and (publish_dir / rel_file).is_file():
        if active_count < retain:
            item["apkAvailable"] = True
            kept_files.add(rel_file)
            active_count += 1
        else:
            item["apkAvailable"] = False
            item["releaseFile"] = None
    else:
        item["apkAvailable"] = False
        item["releaseFile"] = None

releases_dir = publish_dir / "releases"
if releases_dir.is_dir():
    for path in releases_dir.glob("*.apk"):
        rel = f"releases/{path.name}"
        if rel not in kept_files:
            path.unlink(missing_ok=True)

def write_atomic(target: Path, payload):
    tmp = target.with_suffix(target.suffix + f".tmp.{os.getpid()}")
    tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.chmod(tmp, 0o644)
    tmp.replace(target)

write_atomic(publish_dir / "metadata.json", entry)
write_atomic(releases_path, updated)
PY

echo "==> Published $RELEASE_REL (versionCode=$VERSION_CODE, sha256=$SHA256, size=$FILE_SIZE_BYTES)"

verify_endpoint() {
  local base_url="${1%/}/"
  local label="$2"
  echo "==> Verifying $label ($base_url)..."

  local meta_json
  meta_json="$(curl --noproxy '*' -fsSL --max-time 15 "${base_url}metadata.json?t=$(date +%s)")"
  python3 - "$meta_json" "$VERSION_CODE" "$SHA256" "$RELEASE_REL" <<'PY'
import json
import sys

meta = json.loads(sys.argv[1])
exp_code, exp_sha, exp_file = sys.argv[2], sys.argv[3], sys.argv[4]
assert str(meta.get("versionCode")) == exp_code, f"versionCode mismatch: {meta.get('versionCode')} != {exp_code}"
assert meta.get("sha256") == exp_sha, f"sha256 mismatch: {meta.get('sha256')} != {exp_sha}"
assert meta.get("releaseFile") == exp_file, f"releaseFile mismatch: {meta.get('releaseFile')} != {exp_file}"
PY

  for artifact in "latest.apk" "$RELEASE_REL"; do
    local url="${base_url}${artifact}"
    local headers clen range_code range_bytes
    headers="$(curl --noproxy '*' -fsSI --max-time 15 "$url" | tr -d '\r')"
    clen="$(printf '%s\n' "$headers" | awk -F': ' 'tolower($1)=="content-length"{print $2}' | tail -n 1)"
    if [[ "$clen" != "$FILE_SIZE_BYTES" ]]; then
      echo "Error: $url Content-Length '$clen' != expected '$FILE_SIZE_BYTES'" >&2
      exit 1
    fi
    local tmp_range
    tmp_range="$(mktemp)"
    range_code="$(curl --noproxy '*' -sS --max-time 15 -r 0-0 -o "$tmp_range" -w '%{http_code}' "$url")"
    range_bytes="$(wc -c < "$tmp_range" | tr -d ' ')"
    rm -f "$tmp_range"
    if [[ "$range_code" != "206" || "$range_bytes" != "1" ]]; then
      echo "Error: $url range probe returned status=$range_code bytes=$range_bytes (expected 206 / 1 byte)" >&2
      exit 1
    fi
  done
  echo "    OK: metadata & range checks passed for $label"
}

verify_endpoint "$PUBLIC_URL" "LAN 233"
verify_endpoint "$EXTERNAL_URL" "External ai.oryjk.cn:82"
echo "Done."
