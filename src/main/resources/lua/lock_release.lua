-- Liberación segura (RF-7): comparar el token y borrar en un solo paso. Un auditor cuyo bloqueo
-- expiró y fue reasignado trae un token viejo, así que no puede liberar el bloqueo del nuevo dueño.
-- ARGV: 1 ballotId, 2 token. Devuelve 1 si liberó.
local b = ARGV[1]
local lk = 'lock:' .. b
if redis.call('HGET', lk, 'token') ~= ARGV[2] then return 0 end
local a = redis.call('HGET', lk, 'auditorId')
redis.call('DEL', lk)
if redis.call('GET', 'holding:' .. a) == b then redis.call('DEL', 'holding:' .. a) end
redis.call('SREM', 'locks:active', b)
return 1
