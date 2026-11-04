-- Latido ACTIVO: renueva el bloqueo solo si lo pide la conexión dueña (RF-7).
-- ARGV: 1 auditorId, 2 connId, 3 ttlMs, 4 nowMs. Devuelve {ballotId, token, name, since, expiresAt} o {}.
local a = ARGV[1]
local b = redis.call('GET', 'holding:' .. a)
if not b then return {} end
local lk = 'lock:' .. b
if redis.call('HGET', lk, 'auditorId') ~= a or redis.call('HGET', lk, 'connId') ~= ARGV[2] then return {} end
redis.call('PEXPIRE', lk, ARGV[3])
redis.call('PEXPIRE', 'holding:' .. a, ARGV[3])
return { b, redis.call('HGET', lk, 'token'), redis.call('HGET', lk, 'auditorName'),
  redis.call('HGET', lk, 'since'), tostring(tonumber(ARGV[4]) + tonumber(ARGV[3])) }
