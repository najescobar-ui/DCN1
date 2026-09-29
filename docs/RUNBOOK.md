# Runbook · Pedidos360

Cómo operar y desplegar Pedidos360 en AWS con los scripts de `infra/`, y qué cambiar para montarlo en otro Learner Lab u otra cuenta.

Todos los scripts se ejecutan **a mano, desde la raíz del repositorio o desde `infra/`**. No hay despliegue automático: se publica solo cuando alguien corre el script.

---

## 1. Requisitos en tu computador

| Herramienta | Versión | Para qué |
|---|---|---|
| AWS CLI | v2 | Todos los scripts |
| JDK | 21 o superior | Compilar el backend (`05`) |
| Node.js y npm | 22 o superior | Compilar el frontend (`06`) |
| bash, curl, zip, ssh, scp, openssl, python3 | las del sistema | Scripts, llaves y pruebas |

Si `java -version` no muestra 21 o superior, indica dónde está el JDK antes de desplegar el backend:

```bash
export JAVA_HOME=/ruta/al/jdk-21      # en macOS con Homebrew: /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
```

---

## 2. Credenciales del Learner Lab

Los scripts usan un **perfil propio** (`dce1`) en archivos separados, para no tocar `~/.aws/config` ni `~/.aws/credentials`.

**Una sola vez**, crea estos tres archivos:

`~/.aws/config_aws_dce1`
```ini
[profile dce1]
region = us-east-1
output = json
```

`~/.aws/dce1-env.sh`
```bash
export AWS_CONFIG_FILE="$HOME/.aws/config_aws_dce1"
export AWS_SHARED_CREDENTIALS_FILE="$HOME/.aws/credentials_aws_dce1"
export AWS_PROFILE=dce1
export AWS_REGION=us-east-1
```

`~/.aws/credentials_aws_dce1`: se llena en cada sesión (siguiente paso).

**En cada sesión del lab** (las credenciales duran unas 4 horas):

1. AWS Academy → Learner Lab → **Start Lab**. Espera a que el círculo junto a "AWS" esté verde.
2. **AWS Details → Show** (AWS CLI). Copia el bloque.
3. Pégalo en `~/.aws/credentials_aws_dce1` y cambia la primera línea `[default]` por **`[dce1]`**.
4. Comprueba:
   ```bash
   source ~/.aws/dce1-env.sh && aws sts get-caller-identity
   ```

> Si un comando responde `explicit deny ... voc-cancel-cred`, la sesión del lab venció: repite los pasos 1 a 3.

---

## 3. El estado: `infra/state.env`

Cada script guarda lo que crea (IDs, URLs, claves) en `infra/state.env`, y los siguientes scripts lo leen. **No se sube a git** porque incluye la clave de la base de datos y las de los usuarios de prueba.

| Clave | Qué es | La crea |
|---|---|---|
| `VPC_ID`, `EC2_SG_ID`, `RDS_SG_ID` | Red y security groups | `01` |
| `DB_USER`, `DB_PASSWORD`, `RDS_HOST` | Base de datos | `01` y `05` |
| `AMPLIFY_APP_ID`, `FRONTEND_URL` | Hosting del frontend | `02` |
| `USER_POOL_ID`, `COGNITO_CLIENT_ID`, `COGNITO_DOMAIN`, `COGNITO_ISSUER_URI` | Cognito | `02` |
| `ADMIN_PASSWORD`, `CLIENTE_PASSWORD` | Claves de los usuarios de prueba | `02` |
| `KEY_FILE`, `EC2_INSTANCE_ID`, `EC2_EIP_ALLOC`, `EC2_IP` | EC2, su llave SSH y su IP elástica | `03` |
| `API_ID`, `API_URL`, `AUTHORIZER_ID`, `INTEG_*` | API Gateway | `04` |
| `POST_CONFIRMATION_LAMBDA` | Lambda del registro | `09` |

Los scripts son **idempotentes**: si la clave ya existe, no vuelven a crear el recurso, solo lo actualizan. Por eso se pueden correr de nuevo sin miedo.

> **Respáldalo.** Si pierdes `state.env`, los scripts creen que no hay nada y duplican recursos. Guárdalo fuera del repositorio (por ejemplo, en un gestor de contraseñas).

---

## 4. Operación diaria

### 4.1 Encender todo después de iniciar el lab

Al terminar cada sesión, el lab apaga la EC2 y RDS. Cognito, API Gateway y Amplify siguen funcionando, pero la API responde 503 hasta que se encienda el backend.

```bash
infra/encender.sh
```

Enciende RDS y la EC2 si están apagadas y espera a que los tres servicios respondan `UP`. Los servicios arrancan solos con systemd; la IP elástica no cambia.

### 4.2 Verificar que todo funciona

```bash
infra/smoke-test.sh
```

Debe terminar con **`Resultado: 29 OK, 0 con diferencias`**. Obtiene tokens reales con el flujo PKCE y prueba cada ruta con y sin token. También cierra cualquier pedido en curso de `cliente1` que haya quedado de antes.

### 4.3 Si cambiaste de red (casa, Duoc, celular)

La EC2 solo acepta SSH desde las IPs autorizadas. Si `05-deploy-backend.sh` se queda pegado o dice "Operation timed out":

```bash
infra/ssh-mi-ip.sh
```

---

## 5. Publicar cambios

Flujo: rama `feature/*` → PR a `dev` → PR a `main` → tag → desplegar **desde `main`**.

```bash
git checkout main && git pull
```

### 5.1 Backend (EC2)

```bash
infra/05-deploy-backend.sh
```

Compila los tres servicios con Maven, copia los `.jar` a `/opt/pedidos360/` en la EC2, reescribe `/opt/pedidos360/pedidos360.env` con las variables (Cognito, base de datos, Webpay) y reinicia los servicios. Termina mostrando el estado `UP` de cada uno. Tarda unos 3 minutos.

### 5.2 Frontend (Amplify)

```bash
BRANCH=main infra/06-deploy-frontend.sh       # producción: https://main.<AMPLIFY_APP_ID>.amplifyapp.com
BRANCH=preview infra/06-deploy-frontend.sh    # revisión:   https://preview.<AMPLIFY_APP_ID>.amplifyapp.com
```

Compila React con Vite, arma un zip y lo publica con la API de Amplify (despliegue manual, sin conexión a GitHub). Termina con `Job N: SUCCEED`.

### 5.3 Probar en el preview o en local

El CORS de producción solo acepta el sitio principal. Para probar en el preview o en `localhost:4200`, ábrelo mientras pruebas y ciérralo al terminar:

```bash
CORS_DEV=1 infra/04-apigw.sh    # agrega preview y localhost
infra/04-apigw.sh               # vuelve a dejar solo el sitio principal
```

---

## 6. Montar todo desde cero (otro lab u otra cuenta)

Tiempo total: unos 30 minutos, casi todo esperando a RDS.

### 6.1 Antes de empezar

1. Configura las credenciales (sección 2).
2. Deja el estado del lab anterior fuera del camino:
   ```bash
   mv infra/state.env infra/state.env.lab-anterior    # si existe
   ```
3. Si ya tienes una llave `~/.ssh/pedidos360-dce1.pem` de otro lab, renómbrala: `03` crea una nueva con ese nombre.

### 6.2 Crear la infraestructura, en este orden

| Paso | Comando | Qué crea | Notas |
|---|---|---|---|
| 1 | `infra/01-network-rds.sh` | Security groups y RDS PostgreSQL privado | RDS tarda 5 a 10 minutos en estar disponible; puedes seguir con el paso 2 |
| 2 | `infra/02-amplify-cognito.sh` | App de Amplify, user pool, grupos, scopes, app client, dominio del Hosted UI y usuarios `admin` y `cliente1` | Muestra las URLs nuevas; las claves quedan en `state.env` |
| 3 | `infra/03-ec2.sh` | EC2 con Java 21, llave SSH e IP elástica | Espera 2 minutos después, para que termine de instalar Java |
| 4 | `infra/04-apigw.sh` | HTTP API, CORS, authorizer JWT, integraciones y 16 rutas | Necesita la IP de la EC2 y los datos de Cognito |
| 5 | `infra/05-deploy-backend.sh` | Base `pedidos`, variables en la EC2 y los tres servicios | Espera solo a que RDS esté disponible |
| 6 | `infra/07-frontend-env.sh` | Escribe `frontend/.env.production` y `.env.development` con los valores nuevos | Ver 6.3 |
| 7 | `BRANCH=main infra/06-deploy-frontend.sh` | Publica el sitio | Crea la rama de Amplify si no existe |
| 8 | `infra/08-cognito-ui.sh` | Logo y colores del Hosted UI | Opcional |
| 9 | `infra/09-cognito-registro.sh` | Lambda que agrega a los usuarios registrados al grupo CLIENTE | |
| 10 | `infra/smoke-test.sh` | Verificación completa | Debe dar 29 OK |

### 6.3 Actualizar el repositorio con los valores nuevos

Un lab nuevo tiene otro user pool, otra API y otra URL de Amplify. Después del paso 6:

1. **Frontend:** `07-frontend-env.sh` ya reescribió `frontend/.env.production` y `frontend/.env.development`. Súbelos con un PR (son valores públicos, no secretos).
2. **README:** actualiza las URLs de la tabla del inicio (sitio y API).
3. **Mapbox (opcional):** crea `frontend/.env.production.local` y `frontend/.env.development.local` con `VITE_MAPBOX_TOKEN=pk....` (no se suben a git) y, en tu cuenta de Mapbox, agrega la URL nueva del sitio a las restricciones del token. Sin token, el buscador usa OpenStreetMap.
4. **Credenciales para el docente:** están en `infra/state.env` (`ADMIN_PASSWORD` y `CLIENTE_PASSWORD`).

---

## 7. Qué cambiar en los scripts

La mayoría de los valores salen de `state.env` o de la cuenta, así que en otro Learner Lab los scripts funcionan sin editarlos. Esto es lo que está fijo, por si hace falta:

| Qué | Dónde | Valor actual | Cuándo cambiarlo |
|---|---|---|---|
| Archivo del perfil de AWS | `infra/lib.sh` | `AWS_ENV_FILE`, por defecto `~/.aws/dce1-env.sh` | Otra cuenta: `export AWS_ENV_FILE=~/.aws/otro-env.sh` antes de correr los scripts, sin editar nada |
| Región | `~/.aws/dce1-env.sh` (`AWS_REGION`) | `us-east-1` | Otra región. El Learner Lab normalmente solo permite `us-east-1` y `us-west-2` |
| Prefijo de los recursos | `infra/lib.sh` (`PROJECT`) | `pedidos360` | Dos copias en la misma cuenta. Solo cambia los nombres de los recursos; el identificador de los scopes (`pedidos360/...`) vive dentro de cada user pool y no hay que tocarlo |
| Rol de la Lambda | `infra/09-cognito-registro.sh` | `LabRole` | Cuenta que no es Learner Lab: crear un rol con permiso `cognito-idp:AdminAddUserToGroup` y poner su nombre |
| Perfil de la EC2 | `infra/03-ec2.sh` | `LabInstanceProfile` | Cuenta que no es Learner Lab: usar un instance profile propio (o quitar la opción) |
| Tamaño de la EC2 | `infra/03-ec2.sh` | `t3.small` | Más carga: `t3.medium` |
| Tamaño de RDS | `infra/01-network-rds.sh` | `db.t3.micro`, 20 GB | Más carga |
| Llave SSH | `infra/03-ec2.sh` | `~/.ssh/pedidos360-dce1.pem` | Si ya existe una de otro lab |
| JDK para compilar | `infra/05-deploy-backend.sh` | `JAVA_HOME` o la ruta de Homebrew en macOS | Linux o Windows (WSL): `export JAVA_HOME=...` antes de correrlo |
| Credenciales de Webpay | `ms-pedidos/src/main/resources/application.yml` | Sandbox público de Transbank | Producción: `WEBPAY_BASE_URL`, `WEBPAY_COMMERCE_CODE` y `WEBPAY_API_KEY` en `/opt/pedidos360/pedidos360.env` |

---

## 8. Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| `explicit deny ... voc-cancel-cred` | Venció la sesión del lab | Sección 2: nuevas credenciales |
| La API responde 503, el catálogo no carga | EC2 o RDS apagadas | `infra/encender.sh` |
| `05-deploy-backend.sh` se queda pegado en SSH | Cambió tu IP pública | `infra/ssh-mi-ip.sh` |
| El sitio dice "No se pudo contactar la API (red o CORS)" en el preview o en local | CORS solo acepta producción | `CORS_DEV=1 infra/04-apigw.sh` |
| Todas las llamadas dan 401 después de recrear Cognito | El frontend o el authorizer tienen el user pool anterior | `infra/04-apigw.sh`, `infra/05-deploy-backend.sh`, `infra/07-frontend-env.sh` y `06` |
| `smoke-test.sh` falla al crear el pedido con 409 | `cliente1` tiene un pedido en curso que el script no pudo cerrar | Entregarlo o cancelarlo desde el panel de admin |
| Amplify: `Job N: FAILED` | Error al compilar o subir | Correr `npm run build` en `frontend/` y revisar el error |
| RDS: `create-db-instance` falla por el nombre | Ya existe `pedidos360-db` de un intento anterior | Borrarla o cambiar `PROJECT` |

Registros útiles:

```bash
# Logs de un servicio en la EC2 (los últimos 100)
ssh -i ~/.ssh/pedidos360-dce1.pem ec2-user@<EC2_IP> "sudo journalctl -u pedidos360-ms-pedidos -n 100 --no-pager"
# Estado de los tres servicios
ssh -i ~/.ssh/pedidos360-dce1.pem ec2-user@<EC2_IP> "systemctl is-active pedidos360-bff pedidos360-ms-productos pedidos360-ms-pedidos"
```

---

## 9. Borrar todo (liberar presupuesto)

Solo si ya no se va a usar: **no se puede deshacer** y se pierden los datos. Primero carga el perfil y las variables de `state.env` (sin `lib.sh`, que en una terminal interactiva la cerraría ante cualquier error):

```bash
source ~/.aws/dce1-env.sh
set -a; source infra/state.env; set +a
aws apigatewayv2 delete-api --api-id "$API_ID"
aws ec2 terminate-instances --instance-ids "$EC2_INSTANCE_ID"
aws ec2 wait instance-terminated --instance-ids "$EC2_INSTANCE_ID"
aws ec2 release-address --allocation-id "$EC2_EIP_ALLOC"                 # la IP elástica cobra aunque no se use
aws rds delete-db-instance --db-instance-identifier pedidos360-db --skip-final-snapshot
aws lambda delete-function --function-name pedidos360-post-confirmation
aws cognito-idp delete-user-pool-domain --user-pool-id "$USER_POOL_ID" --domain "$(echo "$COGNITO_DOMAIN" | sed -E 's#https://([^.]+)\..*#\1#')"
aws cognito-idp delete-user-pool --user-pool-id "$USER_POOL_ID"
aws amplify delete-app --app-id "$AMPLIFY_APP_ID"
# Cuando RDS termine de borrarse:
aws ec2 delete-security-group --group-id "$RDS_SG_ID"
aws ec2 delete-security-group --group-id "$EC2_SG_ID"
aws ec2 delete-key-pair --key-name pedidos360-key
```

Al final, mueve `infra/state.env` a un respaldo para que un nuevo despliegue parta de cero.
