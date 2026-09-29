# Pedidos360

Proyecto de Desarrollo Cloud Native I (DSY1107). Sistema de pedidos con frontend React, microservicios Spring Boot en EC2, Amazon Cognito como IDaaS y AWS API Gateway como API Manager.

> Por autorización del docente se usa **Amazon Cognito** en lugar de Azure AD (MSAL) y **React** en lugar de Angular. En el frontend, `react-oidc-context` (sobre `oidc-client-ts`) cumple el rol de MSAL: flujo Authorization Code + PKCE, guard de rutas (`ProtectedRoute`), interceptor de axios que adjunta el token y lectura de roles y scopes desde los claims.

## Arquitectura

```
Navegador (React en Amplify Hosting, HTTPS)
   │  1. login OIDC (Auth Code + PKCE) ──► Cognito (managed login)
   │  2. Bearer access token
   ▼
API Gateway (HTTP API) ── JWT authorizer: firma, exp, issuer, client_id, scopes ── CORS
   │
   ├─ /api/bff/*        ──► bff           :8080 ─┐ token relay
   ├─ /api/productos/*  ──► ms-productos  :8081 ◄┤
   └─ /api/pedidos/*    ──► ms-pedidos    :8082 ◄┘──► ms-productos (precios)
                                │                │
                                └── RDS PostgreSQL (BD productos / BD pedidos)
```

Cada microservicio vuelve a validar el JWT aunque API Gateway ya lo haya hecho (defensa en profundidad): firma RS256 contra el JWKS de Cognito, vigencia, `iss`, `client_id`/`aud` y `token_use=access`. Los roles salen de `cognito:groups` y los scopes de `scope`.

## Roles y scopes

| Grupo Cognito | Rol | Puede |
|---|---|---|
| `ADMIN` | ROLE_ADMIN | CRUD de productos, ver todos los pedidos, cambiar estado |
| `CLIENTE` (o sin grupo) | ROLE_CLIENTE | Ver catálogo, crear pedidos, ver y cancelar los propios |

Scopes del resource server `pedidos360`: `productos.read`, `productos.write`, `pedidos.read`, `pedidos.write`.

## Endpoints

| Método | Ruta | Servicio | Scope | Rol |
|---|---|---|---|---|
| GET | `/api/bff/me` | bff | (autenticado) | cualquiera |
| GET | `/api/bff/resumen` | bff | pedidos.read | cualquiera |
| GET | `/api/productos` | ms-productos | productos.read | cualquiera |
| GET | `/api/productos/{id}` | ms-productos | productos.read | cualquiera |
| POST | `/api/productos` | ms-productos | productos.write | ADMIN |
| PUT | `/api/productos/{id}` | ms-productos | productos.write | ADMIN |
| DELETE | `/api/productos/{id}` | ms-productos | productos.write | ADMIN |
| GET | `/api/pedidos` | ms-pedidos | pedidos.read | ADMIN: todos; CLIENTE: propios |
| GET | `/api/pedidos/{id}` | ms-pedidos | pedidos.read | dueño o ADMIN |
| POST | `/api/pedidos` | ms-pedidos | pedidos.write | CLIENTE o ADMIN |
| PATCH | `/api/pedidos/{id}/estado` | ms-pedidos | pedidos.write | ADMIN |
| POST | `/api/pedidos/{id}/cancelar` | ms-pedidos | pedidos.write | dueño (si está PENDIENTE) |
| PATCH | `/api/pedidos/{id}/pago` | ms-pedidos | pedidos.write | ADMIN (confirma transferencia/efectivo) |
| POST | `/api/pedidos/{id}/pago/webpay` | ms-pedidos | pedidos.write | dueño (inicia pago con tarjeta) |
| GET, POST | `/api/pagos/webpay/retorno` | ms-pedidos | pública | retorno del navegador desde Webpay |

Respuestas de error en `application/problem+json`: 400 validación, 401 token ausente/inválido, 403 sin rol o scope, 404, 409, 422, 502.

## Pagos y entrega

- **Tarjeta:** Transbank **Webpay Plus**, ambiente de integración (gratuito). `ms-pedidos` crea la transacción, el navegador paga en Webpay y vuelve a `/api/pagos/webpay/retorno` (ruta pública en API Gateway). El backend confirma la transacción directamente con Transbank, verifica el monto y marca el pedido como pagado o rechazado. Los datos de la tarjeta nunca pasan por Pedidos360.
  Tarjeta de prueba: `4051 8856 0044 6623`, CVV `123`, cualquier vencimiento futuro; en el banco simulado, RUT `11.111.111-1` y clave `123`.
- **Transferencia:** se muestran datos bancarios (ficticios) y un botón de WhatsApp con el mensaje del comprobante; un ADMIN confirma el pago.
- **Efectivo:** pago al recibir; un ADMIN lo confirma al entregar.
- **Dirección:** autocompletado con [Photon](https://photon.komoot.io) (OpenStreetMap, sin API key) y mapa Leaflet con tiles de OpenStreetMap; se guardan dirección y coordenadas.
- **Celular:** obligatorio en el registro de Cognito (`phone_number`) y en cada pedido.

## Pantallas

| Ruta | Acceso | Descripción |
|---|---|---|
| `/` | pública | Login: iniciar sesión o crear cuenta en el Hosted UI de Cognito |
| `/catalogo` | autenticado | Catálogo con búsqueda, categorías y ofertas |
| `/producto/:id` | autenticado | Detalle del producto |
| `/carrito` | CLIENTE o ADMIN | Carrito, dirección de entrega y confirmación del pedido |
| `/pedidos` | autenticado | Seguimiento del pedido en curso e historial |
| `/perfil` | autenticado | Claims de los tokens y pruebas 200/401 contra la API |
| `/admin` | ADMIN | Gestión de pedidos (usa el BFF para los indicadores) |
| `/admin/productos` | ADMIN | Mantenedor del catálogo |

## Estructura

```
backend/
  ms-productos/   catálogo (JPA + PostgreSQL/H2)
  ms-pedidos/     pedidos; consulta precios a ms-productos reenviando el token
  bff/            backend for frontend: /me y /resumen agregado
frontend/         React 19 + Vite + TypeScript
deploy/ec2/       units systemd, plantilla de variables y script de despliegue
infra/            scripts AWS CLI (Cognito, RDS, EC2, API Gateway, Amplify) y pruebas end-to-end
```

## Ejecutar en local

Requisitos: JDK 21+, Node 22+.

```bash
# Backend (cada uno en su terminal). Sin DB_URL usan H2 en memoria.
export COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/<user-pool-id>
export COGNITO_CLIENT_ID=<app-client-id>
cd backend/ms-productos && ./mvnw spring-boot:run
cd backend/ms-pedidos   && ./mvnw spring-boot:run
cd backend/bff          && ./mvnw spring-boot:run

# Frontend: por defecto usa la API de AWS (.env.development).
# Para usar los backends locales: crear frontend/.env.development.local con VITE_API_URL= (vacío, proxy de Vite).
cd frontend && npm install && npm run dev    # http://localhost:4200
```

Tests: `./mvnw test` en cada servicio y `npm test` en `frontend/`.

## Variables de entorno (backend)

| Variable | Uso | Default local |
|---|---|---|
| `COGNITO_ISSUER_URI` | Issuer del user pool | placeholder |
| `COGNITO_CLIENT_ID` | App client permitido (coma para varios) | placeholder |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión a RDS | H2 en memoria |
| `PRODUCTOS_URL`, `PEDIDOS_URL` | URLs internas | `localhost:8081/8082` |
| `PORT` | Puerto HTTP | 8080 / 8081 / 8082 |

## Despliegue en AWS (Learner Lab)

CloudFront está bloqueado en AWS Academy, así que el frontend se publica en **Amplify Hosting**, que entrega HTTPS (Cognito lo exige en las URLs de callback).

Los scripts de `infra/` usan el perfil `dce1` (`~/.aws/dce1-env.sh`) y guardan los IDs creados en `infra/state.env` (ignorado por git, incluye la clave de la BD y la de los usuarios de prueba).

| Script | Crea |
|---|---|
| `01-network-rds.sh` | Security groups y RDS PostgreSQL (privado, solo accesible desde la EC2) |
| `02-amplify-cognito.sh` | App de Amplify; user pool, grupos ADMIN/CLIENTE, resource server `pedidos360` con 4 scopes, app client público (Auth Code + PKCE), dominio del Hosted UI y usuarios de prueba |
| `03-ec2.sh` | EC2 t3.small (Amazon Linux 2023 + Corretto 21) con Elastic IP |
| `04-apigw.sh` | HTTP API con CORS, authorizer JWT de Cognito y una ruta por endpoint con su scope |
| `05-deploy-backend.sh` | BD `pedidos`, variables de entorno en la EC2 y despliegue de los 3 servicios con systemd |
| `06-deploy-frontend.sh` | Build de producción de React y publicación en Amplify (`BRANCH=preview` publica un ambiente de revisión) |
| `08-cognito-ui.sh` | Aplica logo y colores de Pedidos360 al Hosted UI de Cognito (`infra/cognito-ui/`) |
| `get-token.sh` | Obtiene un access token recorriendo el flujo Authorization Code + PKCE del Hosted UI |
| `smoke-test.sh` | Evidencia end-to-end: cada ruta sin token, con token alterado, como CLIENTE y como ADMIN |

Las credenciales del lab expiran cada ~4 horas; al reiniciar el lab hay que volver a pegarlas en `~/.aws/credentials_aws_dce1`. La EC2 y RDS se detienen junto con el lab, pero la Elastic IP mantiene válidas las integraciones de API Gateway.
