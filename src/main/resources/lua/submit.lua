-- Recepción de un acta (RF-1, RF-2, RF-3) en una sola operación atómica.
-- ARGV: 1 acta, 2 version, 3 idem, 4 doubtful(0|1), 5 votosJson, 6 mesa, 7 puesto, 8 municipio,
--       9 receipt, 10 nowMs, 11 caseJson, 12 priority, 13 idemTtlSeconds, 14 doubtfulMessage, 15 metaJson
local acta, version, idem = ARGV[1], tonumber(ARGV[2]), ARGV[3]
local key = acta .. '#' .. ARGV[2]

-- Idempotencia: primero la clave idem (con expiración); si ya expiró, la clave permanente acta#version.
local known = redis.call('GET', 'idem:' .. idem)
if (not known) and redis.call('HEXISTS', 'submissions', key) == 1 then known = key end
if known then
  return { 'DUPLICATE', redis.call('HGET', 'submissions', known) or '{}' }
end

local now = tonumber(ARGV[10])
local resp = { ballotId = acta, version = version, receipt = ARGV[9], registeredAt = now }
local current = tonumber(redis.call('HGET', 'applied:' .. acta, 'version') or '0')

if current >= version then
  -- Llegó tarde una versión vieja: se guarda, pero no retrocede el estado (RF-2).
  resp.status = 'SUPERSEDED'
  resp.message = 'Guardada, pero ya está aplicada la versión ' .. current
elseif ARGV[4] == '1' then
  resp.status = 'DOUBTFUL'
  resp.message = ARGV[14]
  local existing = redis.call('HGET', 'cases', acta)
  local replace = true
  if existing then replace = tonumber(cjson.decode(existing).version) < version end
  if replace then
    redis.call('HSET', 'cases', acta, ARGV[11])
    redis.call('ZADD', 'cases:queue', ARGV[12], acta)
    redis.call('PUBLISH', 'verif', cjson.encode({ type = 'case_added', ballotId = acta }))
  end
else
  apply_sheet(acta, version, cjson.decode(ARGV[5]), ARGV[6], ARGV[7], ARGV[8], now, 'Acta corregida por el jurado')
  resp.status = 'REGISTERED'
  resp.message = 'Acta registrada'
end

local json = cjson.encode(resp)
redis.call('HSET', 'submissions', key, json)
redis.call('HSET', 'submissions:meta', key, ARGV[15])
redis.call('SET', 'idem:' .. idem, key, 'EX', ARGV[13])
return { resp.status, json }
