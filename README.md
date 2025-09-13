# k8s-homelab

A local Kubernetes **dev environment** built with **Minikube** for experimenting, development, and reproducible demos.  
All manifests and Helm values are versioned here so the entire environment can be spun up on any machine with a single script.

```mermaid
flowchart TB

    subgraph Minikube["Cluster"]
        subgraph ingress["Ingress"]
            NGINX["NGINX Ingress Controller"]
        end

        subgraph cert["Cert-Manager"]
            CM["cert-manager"]
            CI["ClusterIssuer: selfsigned-root"]
            CERT["Certificate: localhost-cert"]
        end

        subgraph gitops["ArgoCD"]
            ACD["ArgoCD Server"]
            APP["App-of-Apps Root Application"]
            WF["workloads/ (GitOps folder)"]
        end

        subgraph persistence["Persistence"]
            DB["Postgres / MSSQL / MongoDB / Redis / MinIO"]
        end

        subgraph kafka["Apache Kafka"]
            KAFKA["Kafka (KRaft)"]
            CONNECT["Kafka Connect + Plugins"]
            SR["Schema Registry"]
            UI["Kafka UI"]
        end

        subgraph obs["Observability"]
            PROM["Prometheus"]
            GRAF["Grafana"]
            SEQ["Seq"]
            OTEL["OpenTelemetry Collector"]
        end

        subgraph apps["Apps"]
            APP1["app1"]
        end
    end

    %% Relationships
    NGINX -->|Ingress Routes| ACD
    NGINX -->|Ingress Routes| GRAF
    NGINX -->|Ingress Routes| UI
    NGINX -->|Ingress Routes| APP1

    CI --> CERT
    CERT -.-> NGINX

    ACD --> APP
    APP --> WF
    WF --> persistence
    WF --> kafka
    WF --> obs
    WF --> apps
````

## Prerequisites

Make sure you have the following installed locally:

* [Minikube](https://minikube.sigs.k8s.io/docs/start/) ≥ 1.33
* [kubectl](https://kubernetes.io/docs/tasks/tools/) ≥ 1.29
* [Helm](https://helm.sh/docs/intro/install/) ≥ 3.14
* [Git](https://git-scm.com/) ≥ 2.40
* [htpasswd](https://httpd.apache.org/docs/current/programs/htpasswd.html) (for Argo CD password hashing)

Optional (recommended):

* [Headlamps](https://headlamp.dev/) — **preferred UI** for managing namespaces and resources


## Quick Start

1. **Clone the repo:**

   ```bash
   git clone https://github.com/hasaady/k8s-homelab.git
   cd k8s-homelab
   ```

2. **Run the bootstrap script:**

   ```bash
   ./bootstrap.sh
   ```

   This script will:
    1. Starts **Minikube** with Docker driver.
    2. Enables **metrics-server** addon.
    3. Applies **namespaces**.
    4. Installs **cert-manager** (Bitnami Helm).
        - Waits until ready, then applies **ClusterIssuer + localhost certificate**.
    5. Installs **NGINX Ingress Controller** (Bitnami Helm).
        - Waits until ready.
    6. Installs **ArgoCD** (Bitnami Helm) with a custom bcrypt admin password.
        - Waits until ready.
    7. Applies **ArgoCD ingress** (`/argocd`).
    8. Applies **App-of-Apps** root application, pointing ArgoCD to `workloads/`.

3. **Access**
    1. Run tunnel in a separate terminal:
    ```bash
        minikube tunnel
    ```
    2. Open ArgoCD:
        - URL: [https://localhost/argocd](https://localhost/argocd)
        - User: `admin`
        - Password: whatever you configured via bcrypt in `argocd/values.yaml` 
    3. Future apps:
        - Grafana → `https://localhost/grafana`
        - Seq → `https://localhost/seq`
        - etc.

    TLS is handled by **cert-manager** with the single `localhost-ca-issuer`.

## Key Design Choices:
- **Path-based ingress** → everything is accessible under `https://localhost/<app>` (e.g. `/argocd`, `/grafana`).
- **Cert-manager self-signed issuer** → single `localhost-cert` shared by all ingresses.
- **ArgoCD App-of-Apps pattern** → GitOps deploys workloads from the `workloads/` folder.
- **Namespaces** split infra vs. workloads for clean isolation.


##  Repository Structure
```
bootstrap.sh # one-click cluster bootstrap  
namespaces/ # declarative namespace manifests  
    persistence.yaml  
    apache-kafka.yaml  
    observability.yaml  
    apps.yaml  
cert-manager/ # TLS management  
    values.yaml  
    cluster-issuer.yaml # ClusterIssuer + localhost cert  
argocd/ # GitOps setup  
    values.yaml # Helm values (bcrypt admin password, configs)  
    app-of-apps.yaml # Root ArgoCD Application  
    ingress.yaml # ArgoCD ingress (/argocd)  
nginx/ # Helm values for ingress-nginx  
    values.yaml  
workloads/ # GitOps workloads (managed by ArgoCD)  
    persistence/  
    apache-kafka/  
    observability/  
    apps/
````
