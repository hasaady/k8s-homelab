#!/usr/bin/env bash
set -euo pipefail

helm repo add jetstack https://charts.jetstack.io
helm repo update

# Install cert-manager with CRDs into its own namespace
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set installCRDs=true

kubectl -n cert-manager rollout status deploy/cert-manager -w
kubectl -n cert-manager rollout status deploy/cert-manager-webhook -w
kubectl -n cert-manager rollout status deploy/cert-manager-cainjector -w
