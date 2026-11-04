-- Resolver un caso: verificar el token, cerrar el caso y (si se ratifica o corrige) aplicar el acta,
-- todo en una sola operación atómica.
-- ARGV: 1 ballotId, 2 token, 3 decision, 4 correctedVotesJson ('' si no hay), 5 nowMs
local b = ARGV[1]
local lk = 'lock:' .. b
if redis.call('HGET', lk, 'token') ~= ARGV[2] then return { 'LOCK_NOT_OWNED' } end
local caseJson = redis.call('HGET', 'cases', b)
if not caseJson then return { 'CASE_NOT_FOUND' } end
local c = cjson.decode(caseJson)
local a = redis.call('HGET', lk, 'auditorId')
redis.call('DEL', lk)
if redis.call('GET', 'holding:' .. a) == b then redis.call('DEL', 'holding:' .. a) end
redis.call('SREM', 'locks:active', b)
redis.call('HDEL', 'cases', b)
redis.call('ZREM', 'cases:queue', b)

local decision = ARGV[3]
local current = tonumber(redis.call('HGET', 'applied:' .. b, 'version') or '0')
local version = tonumber(c.version)
if current < version then
  if decision == 'RATIFIED' then
    apply_sheet(b, version, c.votos, c.mesa, c.puesto, c.municipio, ARGV[5], 'Acta ratificada en verificación')
  elseif decision == 'CORRECTED' and ARGV[4] ~= '' then
    apply_sheet(b, version, cjson.decode(ARGV[4]), c.mesa, c.puesto, c.municipio, ARGV[5], 'Acta corregida en verificación')
  end
end
return { 'OK', caseJson }
