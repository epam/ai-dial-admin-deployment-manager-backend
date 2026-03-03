# AI DIAL Deployment Manager — Full Security Model Deployment Guide (with Cilium)

> **Note:** This guide covers the deployment of the AI DIAL Deployment Manager in a dual-cluster security model with optional Cilium network policy enforcement. It intentionally omits cloud-provider–specific configuration steps and is intended to serve as a **template** for your cloud-specific setup.

---

## Table of Contents

1. [Requirements](#1-requirements)
2. [Configure Cluster Connection](#2-configure-cluster-connection)
   - [2.1 Create a Service Account on the Untrusted Cluster](#21-create-a-service-account-on-the-untrusted-cluster)
   - [2.2 Retrieve the Token and CA Certificate](#22-retrieve-the-token-and-ca-certificate)
   - [2.3 Get the Cluster API Endpoint](#23-get-the-cluster-api-endpoint)
   - [2.4 Generate a kubeconfig for the Untrusted Cluster](#24-generate-a-kubeconfig-for-the-untrusted-cluster)
3. [RBAC Configuration on the Untrusted Cluster](#3-rbac-configuration-on-the-untrusted-cluster)
4. [Cilium Network Policy RBAC (optional)](#4-cilium-network-policy-rbac-optional)
5. [Deployment Manager Configuration](#5-deployment-manager-configuration)

---

## 1. Requirements

This deployment model requires **two Kubernetes clusters**.

### 1.1 Trusted Cluster

The **trusted cluster** is the cluster where the core AI DIAL platform is already deployed, including:

- `dial-admin` (AI DIAL Admin backend)
- `dial` (AI DIAL application)
- The **Deployment Manager** itself (this service)

No special additional tooling is required on this cluster beyond what is already present for the DIAL platform.

### 1.2 Untrusted Cluster

The **untrusted cluster** is dedicated to running model-serving and deployment workloads. It hosts:

| Workload | Required | Notes |
|----------|----------|-------|
| **Istio** | Yes | Service mesh for traffic management and security |
| **Knative Serving** | Yes | Serverless runtime for MCP deployments |
| **KServe** | Optional | Kubernetes-native model serving framework |
| **NIM (NVIDIA Inference Microservices)** | Optional | GPU-accelerated inference with NVIDIA NIM Operator |
| **Cilium** | Optional | eBPF-based network policies for fine-grained traffic control |

#### Installation Guides

Before proceeding, ensure the required components are installed on the untrusted cluster:

- **Istio**: [Official Istio Installation Guide](https://istio.io/latest/docs/setup/install/)
- **Knative Serving**: [Official Knative Serving Installation Guide](https://knative.dev/docs/install/)
- **KServe**: [Official KServe Installation Guide](https://kserve.github.io/website/docs/admin-guide/kubernetes-deployment) (see also [`kserve.md`](kserve.md) in this repository)
- **NVIDIA NIM Operator**: [Official NIM Operator Installation Guide](https://docs.nvidia.com/nim-operator/latest/install.html)
- **Cilium**: [Official Cilium Installation Guide](https://docs.cilium.io/en/stable/gettingstarted/k8s-install-default/)

---

## 2. Configure Cluster Connection

The Deployment Manager runs on the **trusted cluster** and connects to the **untrusted cluster** using a dedicated Kubernetes `ServiceAccount` and a generated kubeconfig file.

Set the following shell variables before running the commands in this section. Substitute the correct values for your environment:

```bash
# kubectl context name that points to the untrusted cluster (from your local kubeconfig)
UNTRUSTED_CTX=<your-untrusted-context-name>

# Namespace on the untrusted cluster used for build jobs and the service account 
UNTRUSTED_NS=<K8S_BUILD_NAMESPACE>
```
> **Note:** It is not obligatory to use build namespace as a Service account namespace, but as build images stage shared between all kind of model deployments it is better to use it.

### 2.1 Create a Service Account on the Untrusted Cluster

Apply the following manifests to create a `ServiceAccount` and a long-lived token `Secret` on the **untrusted cluster**:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: dial-deployment-manager
  namespace: <K8S_BUILD_NAMESPACE>
---
apiVersion: v1
kind: Secret
metadata:
  name: dial-deployment-manager-sa-secret
  namespace: <K8S_BUILD_NAMESPACE>
  annotations:
    kubernetes.io/service-account.name: "dial-deployment-manager"
type: kubernetes.io/service-account-token
data:
  extra: ZHVtbXk=
```

```bash
kubectl --context $UNTRUSTED_CTX apply -f service-account.yaml
```

After the Secret is created, Kubernetes will automatically populate it with a valid service-account token. This token will be used to authenticate the Deployment Manager against the untrusted cluster.

### 2.2 Retrieve the Token and CA Certificate

Read the token and cluster CA certificate from the Secret and store them in shell variables:

```bash
UNTRUSTED_TOKEN=$(kubectl --context $UNTRUSTED_CTX \
  get secret dial-deployment-manager-sa-secret \
  -n $UNTRUSTED_NS \
  -o jsonpath='{.data.token}' | base64 -d)

UNTRUSTED_CERT=$(kubectl --context $UNTRUSTED_CTX \
  get secret dial-deployment-manager-sa-secret \
  -n $UNTRUSTED_NS \
  -o jsonpath='{.data.ca\.crt}')
```

Verify the token was retrieved successfully:

```bash
echo $UNTRUSTED_TOKEN | head -c 20 && echo "..."
```

### 2.3 Get the Cluster API Endpoint

The cluster API server endpoint (`UNTRUSTED_CLUSTER`) must be set to the full HTTPS URL of the untrusted cluster's Kubernetes API server.

You can find this value in your local kubeconfig under the relevant cluster entry:

```bash
kubectl config view --minify --context $UNTRUSTED_CTX \
  -o jsonpath='{.clusters[0].cluster.server}'
```

Store the result:

```bash
UNTRUSTED_CLUSTER=<https://your-untrusted-cluster-api-endpoint>
```

> For cloud-specific instructions on locating the API server endpoint, refer to your cloud provider's Kubernetes documentation (e.g., AKS, GKE, EKS cluster details page or CLI commands).

### 2.4 Generate a kubeconfig for the Untrusted Cluster

Use the variables collected above to generate a kubeconfig file that authenticates the Deployment Manager to the untrusted cluster:

```bash
kubectl --kubeconfig untrusted-kubeconfig.yaml config set-cluster untrusted-cluster \
  --server=$UNTRUSTED_CLUSTER \
  --certificate-authority=<(echo $UNTRUSTED_CERT | base64 -d) \
  --embed-certs=true

kubectl --kubeconfig untrusted-kubeconfig.yaml config set-credentials dial-deployment-manager \
  --token=$UNTRUSTED_TOKEN

kubectl --kubeconfig untrusted-kubeconfig.yaml config set-context untrusted \
  --cluster=untrusted-cluster \
  --user=dial-deployment-manager \
  --namespace=$UNTRUSTED_NS

kubectl --kubeconfig untrusted-kubeconfig.yaml config use-context untrusted
```

The resulting `untrusted-kubeconfig.yaml` file should be mounted into the Deployment Manager pod or referenced via the `K8S_CONNECT_TYPE=CONFIG_FILE` configuration. See [configuration.md](configuration.md) for details.

---

## 3. RBAC Configuration on the Untrusted Cluster

The `dial-deployment-manager` ServiceAccount requires scoped permissions in each namespace it manages on the untrusted cluster.

Replace the namespace placeholders with your actual namespace names, then apply the manifests to the **untrusted cluster**.

```yaml
# Build namespace — permissions to run image build/copy/analyze Jobs
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: dial-deployment-manager-build-role
  namespace: <K8S_BUILD_NAMESPACE>
rules:
  - verbs:
      - get
      - list
      - watch
      - create
      - delete
    apiGroups:
      - batch
    resources:
      - jobs
  - verbs:
      - get
      - list
      - watch
    apiGroups:
      - ""
    resources:
      - pods
      - pods/log
  - verbs:
      - get
      - create
      - delete
    apiGroups:
      - ""
    resources:
      - secrets
      - configmaps
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: dial-deployment-manager-build-rb
  namespace: <K8S_BUILD_NAMESPACE>
subjects:
  - kind: ServiceAccount
    name: dial-deployment-manager
    namespace: <K8S_BUILD_NAMESPACE>
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: dial-deployment-manager-build-role
---
# Knative namespace — permissions to manage Knative Services
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: dial-deployment-manager-knative-role
  namespace: <K8S_KNATIVE_DEPLOYMENT_NAMESPACE>
rules:
  - verbs:
      - get
      - list
      - watch
      - create
      - update
      - delete
    apiGroups:
      - serving.knative.dev
    resources:
      - services
  - verbs:
      - get
      - list
      - watch
      - delete
    apiGroups:
      - ""
    resources:
      - pods
      - pods/log
      - events
  - verbs:
      - get
      - create
      - delete
    apiGroups:
      - ""
    resources:
      - secrets
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: dial-deployment-manager-knative-rb
  namespace: <K8S_KNATIVE_DEPLOYMENT_NAMESPACE>
subjects:
  - kind: ServiceAccount
    name: dial-deployment-manager
    namespace: <K8S_BUILD_NAMESPACE>
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: dial-deployment-manager-knative-role
---
# NIM namespace — permissions to manage NVIDIA NIMService resources (optional)
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: dial-deployment-manager-nim-role
  namespace: <K8S_NIM_DEPLOYMENT_NAMESPACE>
rules:
  - verbs:
      - get
      - list
      - watch
      - create
      - delete
    apiGroups:
      - apps.nvidia.com
    resources:
      - nimservices
  - verbs:
      - get
      - list
      - watch
    apiGroups:
      - ""
    resources:
      - pods
      - pods/log
      - events
  - verbs:
      - get
      - create
      - delete
    apiGroups:
      - ""
    resources:
      - secrets
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: dial-deployment-manager-nim-rb
  namespace: <K8S_NIM_DEPLOYMENT_NAMESPACE>
subjects:
  - kind: ServiceAccount
    name: dial-deployment-manager
    namespace: <K8S_BUILD_NAMESPACE>
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: dial-deployment-manager-nim-role
---
# KServe namespace — permissions to manage InferenceService resources (optional)
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: dial-deployment-manager-kserve-role
  namespace: <K8S_KSERVE_DEPLOYMENT_NAMESPACE>
rules:
  - verbs:
      - get
      - list
      - watch
      - create
      - delete
    apiGroups:
      - serving.kserve.io
    resources:
      - inferenceservices
  - verbs:
      - get
      - list
      - watch
    apiGroups:
      - ""
    resources:
      - pods
      - pods/log
      - events
  - verbs:
      - get
    apiGroups:
      - ""
    resources:
      - secrets
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: dial-deployment-manager-kserve-rb
  namespace: <K8S_KSERVE_DEPLOYMENT_NAMESPACE>
subjects:
  - kind: ServiceAccount
    name: dial-deployment-manager
    namespace: <K8S_BUILD_NAMESPACE>
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: dial-deployment-manager-kserve-role
```

---

## 4. Cilium Network Policy RBAC (optional)

If Cilium is installed on the untrusted cluster and you want the Deployment Manager to enforce Cilium network policies for build jobs and deployed services, set the following environment variable on the Deployment Manager:

```
CILIUM_NETWORK_POLICIES_ENABLED=true
```

In this mode, the `dial-deployment-manager` ServiceAccount also needs permissions to create and manage `CiliumNetworkPolicy` and `CiliumClusterwideNetworkPolicy` resources. Apply the following in **each target namespace** (`<K8S_BUILD_NAMESPACE>`, `<K8S_KNATIVE_DEPLOYMENT_NAMESPACE>`, `<K8S_NIM_DEPLOYMENT_NAMESPACE>`, `<K8S_KSERVE_DEPLOYMENT_NAMESPACE>`):

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: deployment-manager-cilium-role
  namespace: <K8S_KSERVE_DEPLOYMENT_NAMESPACE|K8S_NIM_DEPLOYMENT_NAMESPACE|K8S_KNATIVE_DEPLOYMENT_NAMESPACE|K8S_BUILD_NAMESPACE>
rules:
  - verbs:
      - create
      - update
      - deletecollection
      - patch
      - get
      - delete
      - list
      - watch
    apiGroups:
      - cilium.io
    resources:
      - ciliumnetworkpolicies
      - ciliumclusterwidenetworkpolicies
  - verbs:
      - patch
      - update
    apiGroups:
      - cilium.io
    resources:
      - ciliumnetworkpolicies/status
      - ciliumclusterwidenetworkpolicies/status
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: deployment-manager-cilium-role
  namespace: <K8S_KSERVE_DEPLOYMENT_NAMESPACE|K8S_NIM_DEPLOYMENT_NAMESPACE|K8S_KNATIVE_DEPLOYMENT_NAMESPACE|K8S_BUILD_NAMESPACE>
subjects:
  - kind: ServiceAccount
    name: dial-deployment-manager
    namespace: <K8S_BUILD_NAMESPACE>
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: deployment-manager-cilium-role
```

---

## 5. Deployment Manager Configuration

After the cluster connection is configured, update the Deployment Manager's configuration to point at the untrusted cluster kubeconfig and enable the relevant deployment backends.

Key settings for the dual-cluster security model (set as environment variables or in your `application.yml` override):

| Environment Variable | Example Value | Description |
|----------------------|---------------|-------------|
| `K8S_CONNECT_TYPE` | `CONFIG_FILE` | Use a kubeconfig file for cluster authentication |
| `K8S_DEPLOY_CONTEXT` | `untrusted` | Context name in the generated kubeconfig |
| `K8S_BUILD_NAMESPACE` | `dm-build` | Namespace on the untrusted cluster for build jobs |
| `K8S_KNATIVE_ENABLED` | `true` | Enable Knative service deployment support |
| `K8S_KNATIVE_DEPLOYMENT_NAMESPACE` | `dm-mcp` | Namespace for Knative (MCP) deployments |
| `K8S_NIM_ENABLED` | `false` | Enable NIM deployment support (optional) |
| `K8S_NIM_DEPLOYMENT_NAMESPACE` | `dm-nim` | Namespace for NIM deployments (optional) |
| `K8S_KSERVE_ENABLED` | `true` | Enable KServe deployment support (optional) |
| `K8S_KSERVE_DEPLOYMENT_NAMESPACE` | `dm-kserve` | Namespace for KServe deployments (optional) |
| `CILIUM_NETWORK_POLICIES_ENABLED` | `false` | Enable Cilium network policies (optional) |
| `SPRING_CONFIG_ADDITIONAL_LOCATION` | `file:/config/override.yml` | Path to external configuration override |

Mount the generated `untrusted-kubeconfig.yaml` into the Deployment Manager container (e.g., as a Kubernetes `Secret`) and set `SPRING_CONFIG_ADDITIONAL_LOCATION` to point to the override file that references it:

```yaml
app:
  kubernetes:
    connect-type: CONFIG_FILE
    config_file:
      kube-config: /config/untrusted-kubeconfig.yaml
      contexts:
        deploy-context: untrusted
```

Alternatively, if you are deploying with Helm, you can mount the kubeconfig from a Kubernetes `Secret` using the chart's `extraVolumes` and `extraVolumeMounts` extension points. First, create the Secret from the generated file:

```bash
kubectl create secret generic dial-deployment-manager-kubeconfig \
  --from-file=config=untrusted-kubeconfig.yaml \
  -n <trusted-cluster-namespace>
```

Then add the following to your Helm values:

```yaml
extraVolumes:
  - name: kubeconfig
    secret:
      secretName: dial-deployment-manager-kubeconfig

extraVolumeMounts:
  - name: kubeconfig
    mountPath: /home/appuser/.kube
```

With this setup, the kubeconfig is available at `/home/appuser/.kube/config` — the default path used when `app.kubernetes.config_file.kube-config` is not explicitly set.

For the full list of configuration options, see [configuration.md](configuration.md).
