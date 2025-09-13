#!/bin/bash
set -e

# ==========================================
# Kubernetes Home Lab Bootstrap Script
# ==========================================

# Helper: wait for all deployments in a namespace
wait_for_namespace_deploys() {
  local namespace=$1
  echo "⏳ Waiting for all deployments in namespace/$namespace..."
  kubectl rollout status deployment -n "$namespace" --timeout=180s
}

# 1. Start Minikube
echo "🚀 Starting Minikube..."
minikube start --driver=docker

# 2. Enable Minikube addons
echo "⚙️  Enabling Minikube addons..."
minikube addons enable metrics-server

# 3. Create namespaces
echo "📂 Applying namespaces..."
kubectl apply -f namespaces/

# 4. Install Cert-Manager (Bitnami via Helm)
echo "🔒 Installing Cert-Manager..."
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install cert-manager bitnami/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  -f cert-manager/values.yaml

# 5. Wait for Cert-Manager before applying ClusterIssuer
wait_for_namespace_deploys cert-manager

echo "🔑 Applying ClusterIssuer and localhost Certificate..."
kubectl apply -f cert-manager/cluster-issuer.yaml

# 6. Install NGINX Ingress Controller (Bitnami via Helm)
echo "🌐 Installing NGINX Ingress Controller..."
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  -f nginx/values.yaml

# 7. Wait for ingress-nginx before applying ArgoCD ingress
wait_for_namespace_deploys ingress-nginx

# 8. Install Argo CD (Bitnami via Helm)
echo "📦 Installing Argo CD..."
helm repo add argo https://argoproj.github.io/argo-helm
helm repo update

helm upgrade --install argocd argo/argo-cd \
  --namespace argocd \
  --create-namespace \
  -f argocd/values.yaml

# 9. Wait for ArgoCD to be healthy
wait_for_namespace_deploys argocd

# 10. Apply ArgoCD ingress
echo "🌍 Applying ArgoCD Ingress..."
kubectl apply -f argocd/ingress.yaml

# 11. Apply App of Apps (root application for ArgoCD)
echo "📂 Applying ArgoCD App of Apps..."
kubectl apply -f argocd/app-of-apps.yaml

echo "✅ Bootstrap completed successfully!"
echo "👉 Run 'minikube tunnel' in a separate terminal"
echo "👉 Access ArgoCD at: https://localhost/argocd"
