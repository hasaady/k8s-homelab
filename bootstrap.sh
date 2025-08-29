#!/usr/bin/env bash
set -euo pipefail

echo "🚀 Starting Atelier bootstrap..."

# 1. Start Minikube
minikube start --cpus=6 --memory=12288 --disk-size=40g --driver=docker

# 2. Enable addons
minikube addons enable ingress
minikube addons enable storage-provisioner

# 3. Apply namespaces
echo "📦 Applying namespaces..."
kubectl apply -f namespaces/

# 4. Install cert-manager (Helm with CRDs)
echo "🔐 Installing cert-manager..."
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

# 5. Apply ClusterIssuers
echo "🔐 Applying cluster issuers..."
kubectl apply -f cert-manager/cluster-issuers.yaml

# 6. Deploy Argo CD (manifests)
echo "🎛️ Deploying Argo CD..."
kubectl create namespace argocd || true
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# 7. Patch Argo CD server to run under /argocd
kubectl -n argocd patch deployment argocd-server \
  --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--rootpath=/argocd"}]'

# 8. Apply Ingress for Argo CD + unified atelier ingress
echo "🌐 Applying ingress resources..."
kubectl apply -f ingress/

# 9. Apply ApplicationSet for atelier
echo "📂 Applying Argo CD ApplicationSet..."
kubectl apply -f argocd/atelier-appset.yaml -n argocd

# 10. Update /etc/hosts
MINIKUBE_IP=$(minikube ip)
HOST_ENTRY="$MINIKUBE_IP atelier.local"

echo "📝 Ensuring /etc/hosts contains: $HOST_ENTRY"
if ! grep -q "atelier.local" /etc/hosts; then
  echo "$HOST_ENTRY" | sudo tee -a /etc/hosts
else
  echo "ℹ️ /etc/hosts already contains atelier.local"
fi

# 11. Set Argo CD admin password
ARGOCD_PASS="Aa@12341234"
HASH=$(htpasswd -nbBC 10 "" "$ARGOCD_PASS" | tr -d ':\n')

kubectl -n argocd patch secret argocd-secret \
  -p "{\"stringData\": {\"admin.password\": \"$HASH\", \"admin.passwordMtime\": \"$(date +%FT%T%Z)\"}}"

kubectl -n argocd rollout restart deploy argocd-server

echo "🔑 Argo CD admin password set to: $ARGOCD_PASS"

echo "✅ Atelier bootstrap finished!"

echo "👉 Open https://atelier.local/argocd in your browser."
