#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_SCRIPT="$SCRIPT_DIR/../build-apk.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "fakeapk" > "$TMP/app.apk"

source "$BUILD_SCRIPT"

run_check() {
  local name="$1"
  local aapt_exit="$2"
  local aapt_output="$3"
  local expect_pass="$4"

  mkdir -p "$TMP/bin"
  cat > "$TMP/bin/aapt" <<EOF
#!/usr/bin/env bash
cat <<'AAPT'
$aapt_output
AAPT
exit $aapt_exit
EOF
  chmod +x "$TMP/bin/aapt"

  local result="pass"
  if ! ( PATH="$TMP/bin:$PATH" verify_package_name "$TMP/app.apk" "com.porth.demo" ) >/dev/null 2>&1; then
    result="fail"
  fi

  if [[ "$result" == "$expect_pass" ]]; then
    echo "PASS: $name"
  else
    echo "FAIL: $name (expected $expect_pass, got $result)" >&2
    exit 1
  fi
}

run_check "aapt exits 0 with matching package"  0 "package: name='com.porth.demo' versionCode='1'" pass
run_check "aapt exits 1 with matching package"  1 "package: name='com.porth.demo' versionCode='1'" fail
run_check "aapt exits 0 with different package" 0 "package: name='com.other.app' versionCode='1'" fail
run_check "aapt exits 1 with no output"         1 "" fail
run_check "aapt exits 0 with no output"         0 "" fail

echo "All verify_package_name tests passed."
