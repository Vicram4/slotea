# Google Cloud (API en Cloud Run)

Configuración única del proyecto de GCP, hecha con `gcloud`. Los despliegues posteriores los hace
GitHub Actions ([deploy-api.yml](../../.github/workflows/deploy-api.yml)) cuando la CI de `main` termina en verde.

| Recurso | Valor |
|---|---|
| Proyecto | `slotea-514518` (región `europe-west1`) |
| Servicio | Cloud Run `slotea-api` — escala a cero, máx. 2 instancias, 512 MiB |
| Imágenes | Artifact Registry `europe-west1-docker.pkg.dev/slotea-514518/slotea/api:<commit>` (se conservan las 3 últimas) |
| Secretos | Secret Manager `db-url`, `db-user`, `db-password`, `jwt-secret` (réplica solo en `europe-west1`) |
| Base de datos | Neon, Frankfurt (`aws-eu-central-1`), conexión directa sin pooler |
| Coste | Nivel gratuito; presupuesto mensual de 1 € con alertas al 50/90/100 % (real) y 100 % (previsto) |

## Identidades (mínimo privilegio)

| Cuenta de servicio | Para qué | Permisos |
|---|---|---|
| `slotea-api` | Identidad de la API en ejecución | `secretAccessor` solo sobre los 4 secretos |
| `github-deployer` | GitHub Actions despliega | `artifactregistry.writer` (repo `slotea`), `run.developer`, `serviceAccountUser` solo sobre `slotea-api` |

GitHub Actions entra **sin claves JSON** mediante Workload Identity Federation. El proveedor solo acepta
tokens OIDC del repositorio por su **ID numérico** (no por nombre, que se puede reutilizar) y de la rama `main`:

```
assertion.repository_id == '1454593295' && assertion.ref == 'refs/heads/main'
```

## Reproducir desde cero

```bash
P=slotea-514518; PN=908024172167; R=europe-west1; REPO_ID=1454593295
API_SA=slotea-api@$P.iam.gserviceaccount.com; DEP_SA=github-deployer@$P.iam.gserviceaccount.com

gcloud config set project $P && gcloud config set run/region $R
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com \
  iamcredentials.googleapis.com sts.googleapis.com iam.googleapis.com cloudresourcemanager.googleapis.com

# Registro de imágenes con limpieza automática
gcloud artifacts repositories create slotea --repository-format=docker --location=$R
gcloud artifacts repositories set-cleanup-policies slotea --location=$R \
  --policy=infra/gcp/artifact-cleanup-policy.json --no-dry-run

# Secretos (el valor se lee de la entrada estándar: nunca en el historial de la shell)
for s in db-url db-user db-password; do
  gcloud secrets create $s --replication-policy=user-managed --locations=$R --data-file=-   # pega el valor + Ctrl+D
done
# Clave de firma de los JWT: aleatoria, nadie la ve ni la teclea
openssl rand -base64 48 | tr -d '\n' | \
  gcloud secrets create jwt-secret --replication-policy=user-managed --locations=$R --data-file=-

# Cuentas de servicio y permisos
gcloud iam service-accounts create slotea-api --display-name="Slotea API (Cloud Run runtime)"
gcloud iam service-accounts create github-deployer --display-name="GitHub Actions deployer"
for s in db-url db-user db-password jwt-secret; do
  gcloud secrets add-iam-policy-binding $s --member=serviceAccount:$API_SA --role=roles/secretmanager.secretAccessor
done
gcloud artifacts repositories add-iam-policy-binding slotea --location=$R \
  --member=serviceAccount:$DEP_SA --role=roles/artifactregistry.writer
gcloud projects add-iam-policy-binding $P --member=serviceAccount:$DEP_SA --role=roles/run.developer --condition=None
gcloud iam service-accounts add-iam-policy-binding $API_SA \
  --member=serviceAccount:$DEP_SA --role=roles/iam.serviceAccountUser

# Workload Identity Federation para GitHub Actions
gcloud iam workload-identity-pools create github --location=global --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc slotea-repo --location=global \
  --workload-identity-pool=github --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_id=assertion.repository_id,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository_id == '$REPO_ID' && assertion.ref == 'refs/heads/main'"
gcloud iam service-accounts add-iam-policy-binding $DEP_SA --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PN/locations/global/workloadIdentityPools/github/attribute.repository_id/$REPO_ID"

# Primer despliegue: lo hace el workflow. Después, la API se hace pública (la protege su propia autenticación):
gcloud run services add-iam-policy-binding slotea-api --region=$R --member=allUsers --role=roles/run.invoker
```
