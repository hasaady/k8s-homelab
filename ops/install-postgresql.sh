#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NS_DIR="${ROOT_DIR}/apps"

NS="persistence"
release="postgresql"

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install "${release}" bitnami/postgresql \
  --namespace "${NS}" \
  --create-namespace \
  -f ${NS_DIR}/postgresql/values.yaml

kubectl -n "${NS}" rollout status statefulset/${release}
kubectl -n "${NS}" get svc,po,pvc -l app.kubernetes.io/instance=${release}
