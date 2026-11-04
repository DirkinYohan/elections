-- El auditor reconectó (quizá en otra instancia) y sigue siendo dueño: la conexión nueva pasa a ser la dueña.
-- ARGV: 1 auditorId, 2 connId, 3 nowMs. Devuelve {ballotId, token, name, since, expiresAt} o {}.
local a = ARGV[1]
local b = redis.call('GET', 'holding:' .. a)
if not b then return {} end
local lk = 'lock:' .. b
if redis.call('HGET', lk, 'auditorId') ~= a then return {} end
local ttl = redis.call('PTTL', lk)
if ttl <= 0 then return {} end
redis.call('HSET', lk, 'connId', ARGV[2])
return { b, redis.call('HGET', lk, 'token'), redis.call('HGET', lk, 'auditorName'),
  redis.call('HGET', lk, 'since'), tostring(tonumber(ARGV[3]) + ttl) }
