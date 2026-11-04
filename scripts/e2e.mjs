// Prueba de punta a punta contra el backend real (local o desplegado).
//   node scripts/e2e.mjs http://localhost:8080
// Necesita el paquete "ws" (está en elections_frontend/node_modules).
import { createRequire } from 'node:module'
import { randomUUID } from 'node:crypto'
const require = createRequire(new URL('../../elections_frontend/package.json', import.meta.url))
const WebSocket = require('ws')

const BASE = process.argv[2] ?? 'http://localhost:8080'
const api = (p) => `${BASE}/api/v1${p}`
let failures = 0
const ok = (cond, label) => { console.log(`${cond ? 'OK ' : 'FAIL'} ${label}`); if (!cond) failures++ }
const post = async (p, body, headers = {}) => {
  const r = await fetch(api(p), { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) })
  return { status: r.status, body: await r.json().catch(() => null) }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const catalog = await fetch(api('/catalog')).then((r) => r.json())
ok(catalog.municipalities.length === 25, 'catálogo con 25 municipios')
const tables = catalog.municipalities.flatMap((m) => m.stations.flatMap((s) => s.tables.map((t) => ({ mesa: t, puesto: s.id, municipio: m.id }))))
ok(tables.length === 6400, '6.400 mesas')

await post('/mock/sim', { running: false })
await post('/mock/redis', { up: true })
await post('/mock/chaos', { dropResponseRate: 0 })

// Una mesa que nadie haya transmitido todavía
const t = tables[Math.floor(Math.random() * tables.length)]
const acta = `E14-${t.puesto}-${String(t.mesa).padStart(4, '0')}-03`
const sheet = (version, votos, extra = {}) => ({
  acta, mesa: t.mesa, puesto: t.puesto, municipio: t.municipio, version, jurado: 'jury1', ts_cierre: 1793577600,
  votos, sufragantes: Object.values(votos).reduce((a, b) => a + b, 0), hash_foto: 'abc', idem: randomUUID().slice(0, 16), ...extra,
})

const login = await post('/auth/login', { username: 'jury1', password: 'pass123' })
ok(login.status === 200 && login.body.role === 'JURY', 'login de jurado')

// SSE del puesto: snapshot inicial
const topic = `station:${t.puesto}`
const events = []
const ctrl = new AbortController()
const sse = fetch(api(`/stream?topic=${topic}`), { signal: ctrl.signal }).then(async (res) => {
  const reader = res.body.getReader(); const dec = new TextDecoder(); let buf = ''
  for (;;) {
    const { value, done } = await reader.read(); if (done) break
    buf += dec.decode(value, { stream: true })
    let i
    while ((i = buf.indexOf('\n\n')) >= 0) {
      const raw = buf.slice(0, i); buf = buf.slice(i + 2)
      const ev = {}; for (const line of raw.split('\n')) { const k = line.indexOf(':'); if (k > 0) ev[line.slice(0, k)] = line.slice(k + 1).trimStart() }
      if (ev.event) events.push({ id: ev.id, type: ev.event, data: JSON.parse(ev.data) })
    }
  }
}).catch(() => {})
await sleep(800)
ok(events[0]?.type === 'snapshot', 'SSE envía snapshot al conectar')
const before = events[0]?.data.totals

// RF-1 idempotencia
const v1 = sheet(1, { C1: 100, C2: 50, C3: 30, BLANCO: 5, NULO: 2, NO_MARCADO: 1 })
const a = await post('/ballots', v1, { 'X-Client-Id': 'e2e' })
const b = await post('/ballots', v1, { 'X-Client-Id': 'e2e' })
ok(a.status === 201 && a.body.status === 'REGISTERED', 'acta registrada (201)')
ok(b.body.status === 'DUPLICATE' && b.body.receipt === a.body.receipt, 'reenvío → DUPLICATE con el mismo recibo')

// RF-2 corrección y versión vieja tardía
const v3 = sheet(3, { C1: 90, C2: 60, C3: 30, BLANCO: 5, NULO: 2, NO_MARCADO: 1 })
const c = await post('/ballots', v3, { 'X-Client-Id': 'e2e' })
const late = await post('/ballots', sheet(2, { C1: 1, C2: 1, C3: 1, BLANCO: 0, NULO: 0, NO_MARCADO: 0 }), { 'X-Client-Id': 'e2e' })
ok(c.body.status === 'REGISTERED', 'versión 3 aplicada')
ok(late.body.status === 'SUPERSEDED', 'versión 2 tardía → SUPERSEDED, no retrocede')

await sleep(4500) // dos ventanas
const corr = events.find((e) => e.type === 'correction')
const lastEv = events[events.length - 1]
ok(events.some((e) => e.type === 'delta' || e.type === 'correction'), 'llegó el delta agregado por ventana')
ok(!!corr && corr.data.corrections.some((x) => x.ballotId === acta), 'evento correction con la explicación')
const st = await fetch(api(`/results/station/${t.puesto}`)).then((r) => r.json())
ok(st.candidateTotals.C1 - (before?.votes.C1 ?? 0) === 90, 'acumulado del puesto = versión 3 (convergencia)')

// RF-6 reconexión: con el último id no reenvía nada; con uno anterior reenvía solo lo perdido
ctrl.abort(); await sse
const live = events.filter((e) => e.type !== 'snapshot')
const readFor = (id) => fetch(api(`/stream?topic=${topic}&lastEventId=${encodeURIComponent(id)}`), { signal: AbortSignal.timeout(1500) })
  .then(async (r) => { let txt = ''; const rd = r.body.getReader(); try { for (;;) { const { value, done } = await rd.read(); if (done) break; txt += new TextDecoder().decode(value) } } catch {} return txt }).catch(() => '')
const upToDate = await readFor(lastEv.id)
ok(!upToDate.includes('event:'), 'reconexión al día: no reenvía nada ni manda snapshot')
if (live.length >= 2) {
  const replay = await readFor(live[0].id)
  ok(replay.includes(`id:${lastEv.id}`) && !replay.includes('event:snapshot') && !replay.includes(`id: ${live[0].id}
`), 'reconexión atrasada: reenvía solo lo perdido')
}
const old = await readFor('1-0')
ok(old.includes('event:snapshot'), 'id fuera de la ventana retenida → snapshot')

// RF-3 acta dudosa → verificación con bloqueo exclusivo (RF-7)
const t2 = tables.find((x) => x.mesa !== t.mesa && x.puesto !== t.puesto && Math.random() < 0.01) ?? tables[1]
const acta2 = `E14-${t2.puesto}-${String(t2.mesa).padStart(4, '0')}-03`
const doubtful = { ...sheet(1, { C1: 10, C2: 10, C3: 10, BLANCO: 0, NULO: 0, NO_MARCADO: 0 }), acta: acta2, mesa: t2.mesa, puesto: t2.puesto, municipio: t2.municipio, sufragantes: 33 }
const d = await post('/ballots', doubtful, { 'X-Client-Id': 'e2e' })
ok(d.status === 202 && d.body.status === 'DOUBTFUL', 'acta que no cuadra → DOUBTFUL (202)')

const tok = async (u) => (await post('/auth/login', { username: u, password: 'pass123' })).body.token
const connect = (token) => new Promise((resolve) => {
  const ws = new WebSocket(`${BASE.replace(/^http/, 'ws')}/ws/verification?token=${token}`)
  const msgs = []; ws.on('message', (m) => msgs.push(JSON.parse(String(m)))); ws.on('open', () => resolve({ ws, msgs }))
})
const a1 = await connect(await tok('auditor1'))
const a2 = await connect(await tok('auditor2'))
await sleep(500)
ok(a1.msgs[0]?.type === 'welcome' && a1.msgs.some((m) => m.type === 'queue_snapshot' && m.cases.some((x) => x.ballotId === acta2)), 'auditor ve el caso en la cola')
a1.ws.send(JSON.stringify({ type: 'take_case', ballotId: acta2 }))
await sleep(500)
const lock1 = a1.msgs.find((m) => m.type === 'case_locked' && m.ballotId === acta2)
const lock2 = a2.msgs.find((m) => m.type === 'case_locked' && m.ballotId === acta2)
ok(!!lock1?.lockToken, 'el dueño recibe el lockToken')
ok(!!lock2 && !lock2.lockToken, 'el otro auditor ve el bloqueo sin token')
a2.ws.send(JSON.stringify({ type: 'take_case', ballotId: acta2 }))
a2.ws.send(JSON.stringify({ type: 'release_case', ballotId: acta2, lockToken: 'token-viejo' }))
await sleep(500)
ok(a2.msgs.some((m) => m.type === 'take_rejected'), 'segundo auditor → take_rejected')
ok(a2.msgs.some((m) => m.type === 'error' && m.code === 'LOCK_NOT_OWNED'), 'liberar con token ajeno → LOCK_NOT_OWNED')
a1.ws.send(JSON.stringify({ type: 'resolve_case', ballotId: acta2, lockToken: lock1?.lockToken, decision: 'CORRECTED', correctedVotes: { C1: 13, C2: 10, C3: 10, BLANCO: 0, NULO: 0, NO_MARCADO: 0 } }))
await sleep(500)
ok(a2.msgs.some((m) => m.type === 'case_resolved' && m.ballotId === acta2), 'resolución llega a todas las consolas')
ok(a1.msgs.some((m) => m.type === 'presence'), 'presencia de auditores')
a1.ws.close(); a2.ws.close()

// RF-10 degradación
await post('/mock/redis', { up: false })
await sleep(1200)
const down = await post('/ballots', sheet(4, { C1: 1, C2: 1, C3: 1, BLANCO: 0, NULO: 0, NO_MARCADO: 0 }), { 'X-Client-Id': 'e2e' })
ok(down.status === 503 && down.body.status === 'UNAVAILABLE', 'Redis caído → 503 UNAVAILABLE (nunca falso éxito)')
await post('/mock/redis', { up: true })

const metrics = await fetch(api('/metrics')).then((r) => r.json())
ok(metrics.instances.length >= 1 && 'eventLoopLagMs' in metrics.instances[0], 'métricas por instancia')

console.log(failures ? `\n${failures} fallas` : '\nTodo OK')
process.exit(failures ? 1 : 0)
