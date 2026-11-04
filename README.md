# Preconteo electoral · Backend

Spring Boot 4 (Java 21) + Redis + PostgreSQL (Supabase). Implementa el contrato `CONTRACT.md` del
[frontend](https://github.com/DirkinYohan/elections_frontend).

## Desplegar (Render + Vercel)

### 1. Backend en Render
1. Sube este repositorio a GitHub.
2. En Render: **New → Blueprint**, elige el repositorio. `render.yaml` crea el servicio `elections-api` (Docker) y su Redis `elections-redis`.
3. Render pide los secretos:
   - `SUPABASE_DB_HOST` = `aws-0-us-east-2.pooler.supabase.com`
   - `SUPABASE_DB_USER` = `postgres.<project-ref>`
   - `SUPABASE_DB_PASSWORD` = tu contraseña de Supabase
   - `CORS_ORIGINS` = la URL de Vercel, p. ej. `https://elections-frontend.vercel.app,https://*.vercel.app`
4. Cuando termine, comprueba `https://<tu-servicio>.onrender.com/api/v1/health`: debe decir `"redis":"UP"`.

**8 instancias (RT-1):** el plan free de Render permite una sola. Con un plan pago cambia `numInstances: 1` por `8` en `render.yaml`; el código no cambia.

### 2. Frontend en Vercel
1. En Vercel: **Add New → Project**, importa `elections_frontend`. Detecta Vite y usa `vercel.json`.
2. Variable de entorno: `VITE_API_URL` = `https://<tu-servicio>.onrender.com` (sin barra final).
3. Deploy. Si cambias `VITE_API_URL`, vuelve a desplegar (se fija al compilar).

Cuentas de demostración (contraseña `pass123`): `jury1`…`jury6400`, `auditor1`…`auditor120`, `coordinator1`.

## Correr localmente

```bash
docker run -d --name elections-redis -p 6379:6379 redis:7.4-alpine
./mvnw spring-boot:run          # lee Supabase de .env (ver .env.example)
```

Ocho instancias + balanceador sin afinidad (RT-1, RT-2, RT-3) en una máquina:

```bash
docker compose up --build       # http://localhost:8080 (o LB_PORT=8090)
node scripts/e2e.mjs http://localhost:8080
```

`scripts/e2e.mjs` comprueba 22 garantías del caso (idempotencia, versiones, ventana, reconexión, bloqueos, degradación…).

## Cómo cumple el caso

| Req. | Dónde | Cómo |
|---|---|---|
| RF-1 | `lua/submit.lua` | Acta, clave `idem` (TTL 24 h) y recibo se guardan en un solo script atómico. Un reenvío recibe `DUPLICATE` con el mismo recibo. Si cae entre registrar y confirmar, el reenvío con el mismo `idem` recibe el recibo original; si el `idem` expiró, responde la clave permanente `acta#versión` |
| RF-2 | `lua/lib.lua` | La versión nueva revierte la anterior en los 4 niveles; una versión vieja tardía queda `SUPERSEDED` y no retrocede el estado |
| RF-3 | `lua/lib.lua` | Mesa, puesto, municipio y departamento se actualizan dentro de un script Lua. Varios comandos sueltos desde la app dejarían ventanas en las que otro cliente lee un nivel actualizado y otro no; Redis ejecuta el script sin intercalar nada |
| RF-4 / RT-4 | `FanoutDispatcher`, `lua/flush.lua` | Ventana de 2 s con delta mínimo (valores absolutos). Fan-out en lotes de 500 en un hilo único que cede entre lotes; se mide su retraso igual que el Event Loop de Node (`eventLoopLagMs`). Cada cliente tiene su propio hilo virtual de escritura |
| RF-5 | `TopicRegistry` | Mapa tópico → conjunto de conexiones: alta y baja O(1), recorrido O(suscriptores del tópico) |
| RF-6 | `lua/resume.lua` | Redis Stream por tópico (`MAXLEN ~300`, unos 60 B por evento, alrededor de 30 MB con 1.868 tópicos llenos). Con `lastEventId` reenvía solo lo perdido; si ya no está en la ventana o son más de 300 eventos, manda un snapshot |
| Backplane | `BroadcastService` | Stream global `feed` leído con `XREAD BLOCK` por las 8 instancias. Tiene memoria, a diferencia de Pub/Sub: una instancia que pierde Redis sigue desde su último id |
| RF-7 | `lua/lock_*.lua`, `resolve.lua` | Token único por adquisición, TTL de 30 s que solo renuevan los latidos activos de la conexión dueña, y liberación con comparar-y-borrar sobre el token |
| RF-8 | `PresenceService` | Hash con TTL de 25 s más un ZSET índice; sin latido, el auditor desaparece solo |
| RF-9 | `FanoutDispatcher` | Más de 256 KB pendientes: se cierra la conexión y el cliente vuelve con su `lastEventId`. Para demostrarlo: `POST /api/v1/mock/flood` |
| RF-10 | `BallotService`, frontend `backoff.ts` | Con Redis caído responde `503 UNAVAILABLE`, nunca un éxito falso. El cliente reconecta con full jitter |
| Límite de tasa | `RateLimitService` | 120 por minuto por `X-Client-Id`; quien lo excede recibe `429` con `retryAfterSeconds` |
| Supabase | `PersistenceService` | Registro durable de actas y bitácora de auditoría, escritos en segundo plano |

Medido localmente con 8 instancias, 3.000 conexiones SSE y 6.400 actas en 40 s: 13.352 mensajes enviados
frente a 27,5 millones de la cifra ingenua (unas 2.000 veces menos), con latencia mediana de 1 s.
