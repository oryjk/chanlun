#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ANDROID_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(git -C "$ANDROID_DIR" rev-parse --show-toplevel)
PUBLIC_DIR=${CHANLUN_ANDROID_PUBLIC_DIR:-"/data7t/apk-downloads/chanlun-android"}
PUBLIC_URL=${CHANLUN_ANDROID_PUBLIC_URL:-http://172.16.60.233/chanlun-android/}
RETAIN_RELEASES=${RETAIN_RELEASES:-10}
SKIP_BUILD=false
NOTES=()

usage() {
    cat <<'EOF'
Usage: publish-apk.sh [--skip-build] --note TEXT [--note TEXT ...]

Environment:
  CHANLUN_ANDROID_PUBLIC_DIR   7T disk directory mounted by the local233 internal gateway
                               (default: /data7t/apk-downloads/chanlun-android).
  CHANLUN_ANDROID_PUBLIC_URL   Internal download URL (default: http://172.16.60.233/chanlun-android/).
  ANDROID_HOME                 Android SDK path.
  RETAIN_RELEASES              Versioned APK files to retain. Defaults to 10.
EOF
}

while (($#)); do
    case "$1" in
        --skip-build) SKIP_BUILD=true; shift ;;
        --note) NOTES+=("${2:?Missing release note}"); shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

if ((${#NOTES[@]} == 0)); then
    echo "At least one release note is required." >&2
    exit 2
fi
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=normal)" ]]; then
    echo "Release blocked: commit all changes before publishing." >&2
    exit 1
fi

BRANCH=$(git -C "$REPO_ROOT" symbolic-ref --quiet --short HEAD || true)
UPSTREAM=$(git -C "$REPO_ROOT" rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null || true)
if [[ -z "$BRANCH" || -z "$UPSTREAM" ]]; then
    echo "Release blocked: branch and upstream are required. Push to origin/main first." >&2
    exit 1
fi
REMOTE=${UPSTREAM%%/*}
REMOTE_BRANCH=${UPSTREAM#*/}
git -C "$REPO_ROOT" fetch --quiet "$REMOTE" "$REMOTE_BRANCH"
GIT_COMMIT=$(git -C "$REPO_ROOT" rev-parse HEAD)
REMOTE_COMMIT=$(git -C "$REPO_ROOT" rev-parse "$REMOTE/$REMOTE_BRANCH")
if [[ "$GIT_COMMIT" != "$REMOTE_COMMIT" ]]; then
    echo "Release blocked: local HEAD is not synchronized with $UPSTREAM. Commit and push first." >&2
    exit 1
fi

if [[ ! "$RETAIN_RELEASES" =~ ^[0-9]+$ ]] || ((RETAIN_RELEASES < 1)); then
    echo "RETAIN_RELEASES must be a positive integer." >&2
    exit 2
fi

if [[ "$SKIP_BUILD" == false ]]; then
    (cd "$ANDROID_DIR" && ./gradlew testDebugUnitTest assembleDebug)
fi

APK_PATH="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK_PATH" ]] || { echo "APK not found: $APK_PATH" >&2; exit 1; }

SDK_ROOT=${ANDROID_HOME:-$HOME/Android/Sdk}
APK_ANALYZER=${APK_ANALYZER:-$SDK_ROOT/cmdline-tools/latest/bin/apkanalyzer}
APKSIGNER=${APKSIGNER:-$(find "$SDK_ROOT/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner -perm -u+x | sort -V | tail -n 1)}
[[ -x "$APK_ANALYZER" ]] || { echo "apkanalyzer not found: $APK_ANALYZER" >&2; exit 1; }
[[ -x "$APKSIGNER" ]] || { echo "apksigner not found" >&2; exit 1; }
"$APKSIGNER" verify "$APK_PATH"

VERSION_NAME=$($APK_ANALYZER manifest version-name "$APK_PATH")
VERSION_CODE=$($APK_ANALYZER manifest version-code "$APK_PATH")
SAFE_VERSION=${VERSION_NAME//[^a-zA-Z0-9._-]/-}
PUBLISHED_AT=$(TZ=Asia/Shanghai date --iso-8601=seconds)
TIMESTAMP=$(TZ=Asia/Shanghai date +%Y%m%d-%H%M%S)
RELEASE_FILE="changuan-v${SAFE_VERSION}-${TIMESTAMP}.apk"
FILE_SIZE=$(stat -c '%s' "$APK_PATH")
SHA256=$(sha256sum "$APK_PATH" | awk '{print $1}')

STAGING_DIR=$(mktemp -d)
trap 'rm -rf -- "$STAGING_DIR"' EXIT
NOTES_JSON=$(jq -n --args '$ARGS.positional' "${NOTES[@]}")
jq -n \
    --arg versionName "$VERSION_NAME" \
    --arg versionCode "$VERSION_CODE" \
    --arg publishedAt "$PUBLISHED_AT" \
    --arg sha256 "$SHA256" \
    --arg gitCommit "$GIT_COMMIT" \
    --arg releaseFile "releases/$RELEASE_FILE" \
    --argjson releaseNotes "$NOTES_JSON" \
    --argjson fileSizeBytes "$FILE_SIZE" \
    '{versionName:$versionName,versionCode:$versionCode,buildType:"debug",publishedAt:$publishedAt,fileSizeBytes:$fileSizeBytes,sha256:$sha256,gitCommit:$gitCommit,releaseFile:$releaseFile,releaseNotes:$releaseNotes}' \
    > "$STAGING_DIR/metadata.json"

mkdir -p "$PUBLIC_DIR/releases"
if [[ -f "$PUBLIC_DIR/releases.json" ]] && jq -e 'type == "array"' "$PUBLIC_DIR/releases.json" >/dev/null 2>&1; then
    install -m 0644 "$PUBLIC_DIR/releases.json" "$STAGING_DIR/existing.json"
else
    echo '[]' > "$STAGING_DIR/existing.json"
fi
jq -n \
    --slurpfile current "$STAGING_DIR/metadata.json" \
    --slurpfile existing "$STAGING_DIR/existing.json" \
    --argjson keep "$RETAIN_RELEASES" \
    '[$current[0]] + [$existing[0][] | select(.sha256 != $current[0].sha256 and .releaseFile != $current[0].releaseFile)] | .[:$keep]' \
    > "$STAGING_DIR/releases.json"

install -m 0644 "$APK_PATH" "$PUBLIC_DIR/releases/.${RELEASE_FILE}.tmp"
mv -f "$PUBLIC_DIR/releases/.${RELEASE_FILE}.tmp" "$PUBLIC_DIR/releases/$RELEASE_FILE"
install -m 0644 "$APK_PATH" "$PUBLIC_DIR/.latest.apk.tmp"
install -m 0644 "$ANDROID_DIR/distribution/index.html" "$PUBLIC_DIR/.index.html.tmp"
install -m 0644 "$STAGING_DIR/metadata.json" "$PUBLIC_DIR/.metadata.json.tmp"
install -m 0644 "$STAGING_DIR/releases.json" "$PUBLIC_DIR/.releases.json.tmp"
mv -f "$PUBLIC_DIR/.latest.apk.tmp" "$PUBLIC_DIR/latest.apk"
mv -f "$PUBLIC_DIR/.index.html.tmp" "$PUBLIC_DIR/index.html"
mv -f "$PUBLIC_DIR/.metadata.json.tmp" "$PUBLIC_DIR/metadata.json"
mv -f "$PUBLIC_DIR/.releases.json.tmp" "$PUBLIC_DIR/releases.json"

PUBLISHED_SHA=$(sha256sum "$PUBLIC_DIR/latest.apk" | cut -d ' ' -f 1)
[[ "$PUBLISHED_SHA" == "$SHA256" ]] || { echo "Published SHA256 mismatch" >&2; exit 1; }
curl --fail --silent --show-error --max-time 15 "${PUBLIC_URL%/}/metadata.json" | jq -e --arg sha "$SHA256" '.sha256 == $sha' >/dev/null

mapfile -t OLD_RELEASES < <(
    find "$PUBLIC_DIR/releases" -maxdepth 1 -type f -name '*.apk' -printf '%T@ %p\n' \
        | sort -nr \
        | awk -v keep="$RETAIN_RELEASES" 'NR > keep { sub(/^[^ ]+ /, ""); print }'
)
for old_release in "${OLD_RELEASES[@]}"; do
    rm -f -- "$old_release"
done

echo "Published: $PUBLIC_URL"
echo "Storage:   $PUBLIC_DIR (7T disk)"
echo "Version:   $VERSION_NAME ($VERSION_CODE)"
echo "SHA256:    $SHA256"
