#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NS_DIR="${ROOT_DIR}/namespaces"

NAMESPACES=(
  "ingress.yaml"
  "persistence.yaml"
  "messaging.yaml"
  "observability.yaml"
  "apps.yaml"
  "sandbox.yaml"
)

echo "==> Applying namespaces from: ${NS_DIR}"

# Sanity checks
if ! command -v kubectl >/dev/null 2>&1; then
  echo "ERROR: kubectl not found in PATH" >&2
  exit 1
fi

if [ ! -d "${NS_DIR}" ]; then
  echo "ERROR: namespaces directory not found at ${NS_DIR}" >&2
  exit 1
fi

# Apply each namespace manifest if present
missing=0
for f in "${NAMESPACES[@]}"; do
  path="${NS_DIR}/${f}"
  if [ ! -f "${path}" ]; then
    echo "WARN: missing file ${path}"
    missing=1
    continue
  fi
  echo "-> kubectl apply -f ${path}"
  kubectl apply -f "${path}"
done

if [ "${missing}" -eq 1 ]; then
  echo "NOTE: Some namespace files were missing. Create them and re-run this script if needed."
fi

echo "==> Verifying namespaces exist..."
kubectl get ns ingress persistence messaging observability apps sandbox --no-headers | awk '{print "   - " $1 " (" $2 ")"}' || true

echo "==> Done."
