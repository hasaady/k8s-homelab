# k8s-homelab

A local Kubernetes **dev environment** built with **Minikube** for experimenting, development, and reproducible demos.  
All manifests and Helm values are versioned here so the entire environment can be spun up on any machine with a single script.

```mermaid
flowchart TB
    subgraph ingress[Ingress]
        NGINX --> certManager[cert-manager]
    end

    subgraph persistence[Persistence]
        MSSQL
        Postgres
        MongoDB
        Redis
        MinIO
    end

    subgraph messaging[Messaging]
        Kafka
        SchemaRegistry[Schema Registry]
        KafkaConnect[Kafka Connect]
        RedpandaConsole[Redpanda Console]
        Kafka --> SchemaRegistry
        Kafka --> KafkaConnect
    end

    subgraph observability[Observability]
        Prometheus
        Grafana
        Seq
        OTel[OTel Collector]
    end

    subgraph apps[Apps]
        App1
        App2
    end

    subgraph sandbox[Sandbox]
        TestApp1
        TestApp2
    end

    %% Connections
    apps --> Kafka
    apps --> Redis
    apps --> OTel
    apps --> Seq
    messaging --> Prometheus
    messaging --> OTel
    persistence --> apps
````

## Prerequisites

Make sure you have the following installed locally:

* [Minikube](https://minikube.sigs.k8s.io/docs/start/) ≥ 1.33
* [kubectl](https://kubernetes.io/docs/tasks/tools/) ≥ 1.29
* [Helm](https://helm.sh/docs/intro/install/) ≥ 3.14
* [Git](https://git-scm.com/) ≥ 2.40
* [htpasswd](https://httpd.apache.org/docs/current/programs/htpasswd.html) (for Argo CD password hashing)

Optional (recommended):

* [Aptakube](https://aptakube.com/) — **preferred UI** for managing namespaces and resources
* [mkcert](https://github.com/FiloSottile/mkcert) — trusted local CA alternative


## Quick Start

1. **Clone the repo:**

   ```bash
   git clone https://github.com/hasaady/k8s-homelab.git
   cd k8s-homelab
   ```

1. **Run the bootstrap script:**

   ```bash
   chmod +x /bootstrap.sh
   ./bootstrap.sh
   ```

   This script will:

   * Start Minikube with resources
   * Enable addons (`ingress`, `storage-provisioner`)
   * Apply namespaces (`ingress`, `persistence`, `messaging`, `observability`, `apps`, `sandbox`)
   * Install cert-manager
   * Apply cluster issuers (`atelier-ca-issuer`)
   * Install Argo CD and patch it for `/argocd`
   * Apply ingress rules (`atelier.local`)
   * Apply Argo CD ApplicationSet (`argocd/atelier-appset.yaml`)
   * Add `atelier.local` to `/etc/hosts`
   * Set a custom Argo CD admin password from the script (`ARGOCD_PASS`)

4. **Open Argo CD:**

   * URL: [https://atelier.local/argocd](https://atelier.local/argocd)
   * Username: `admin`
   * Password: set in `ops/bootstrap.sh` (via `ARGOCD_PASS` variable)

5. **Sync services in Argo CD UI:**

   * You’ll see one app per service (`postgresql`, `mongodb`, `redis`, `minio`, `mssql`, `kafka`, `schema-registry`, `kafka-connect`, `redpanda-console`, `prometheus`, `grafana`, `seq`, `otel-collector`, etc.)
   * Click **Sync All** to deploy.


## Repository Structure

```
k8s-atelier/
├── argocd/
│   ├── atelier-appset.yaml      # ApplicationSet for all services
├── cert-manager/
│   └── cluster-issuers.yaml     # self-signed CA + atelier issuer
├── ingress/
│   └── atelier.yaml             # unified ingress (https://atelier.local/*)
├── namespaces/                  # Namespace manifests
├── services/                    # Services grouped by namespace
│   ├── persistence/ (Postgres, MongoDB, Redis, MinIO, MSSQL)
│   ├── messaging/ (Kafka, Schema Registry, Kafka Connect, Redpanda Console)
│   ├── observability/ (Prometheus, Grafana, Seq, OTel Collector)
│   ├── apps/ (your microservices)
│   └── sandbox/ (experiments)
└── ops/
    ├── bootstrap.sh             # full cluster bootstrap
    ├── install-cert-manager.sh  # helper for cert-manager
    └── apply-namespaces.sh      # helper for namespaces
```


## Namespaces & Roles

| Namespace         | Purpose                              | Services / Components                                           |
| ----------------- | ------------------------------------ | --------------------------------------------------------------- |
| **ingress**       | Entry, TLS, traffic routing          | NGINX Ingress Controller, cert-manager, Ingress resources       |
| **persistence**   | Databases & storage                  | MSSQL, PostgreSQL, MongoDB, Redis, MinIO                        |
| **messaging**     | Event streaming & integration layer  | Kafka (KRaft), Schema Registry, Kafka Connect, Redpanda Console |
| **observability** | Metrics, tracing, logging            | Prometheus, Grafana, Seq, OpenTelemetry Collector               |
| **apps**          | Application workloads (stable demos) | Your microservices, APIs, test applications                     |
| **sandbox**       | Experiments / junkyard               | Throwaway workloads, PoCs, test data                            |


## Hostname Convention

* Single root hostname: **`atelier.local`**
* All services exposed via paths:

  * `https://atelier.local/argocd`
  * `https://atelier.local/grafana`
  * `https://atelier.local/prometheus`
  * `https://atelier.local/seq`
  * `https://atelier.local/redpanda`
  * `https://atelier.local/minio`
  * … etc.

TLS is handled by **cert-manager** with the single `atelier-ca-issuer`.


## Argo CD Password

* The bootstrap script sets your admin password automatically via `ARGOCD_PASS`.
* You can edit `ops/bootstrap.sh` to change it.
* No need to grab the initial secret anymore.