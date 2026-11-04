-- Tomar un caso (RF-7): bloqueo con dueño identificable (token único por adquisición) y expiración.
-- ARGV: 1 ballotId, 2 auditorId, 3 auditorName, 4 token, 5 connId, 6 ttlMs, 7 nowMs
local b, a = ARGV[1], ARGV[2]
if redis.call('HEXISTS', 'cases', b) == 0 then return { 'CASE_NOT_FOUND' } end
local lk = 'lock:' .. b
local owner = redis.call('HGET', lk, 'auditorId')
if owner and owner ~= a then return { 'TAKEN', redis.call('HGET', lk, 'auditorName') or owner } end
local held = redis.call('GET', 'holding:' .. a)
if held and held ~= b and redis.call('EXISTS', 'lock:' .. held) == 1 then return { 'ALREADY', held } end
redis.call('DEL', lk)
redis.call('HSET', lk, 'auditorId', a, 'auditorName', ARGV[3], 'token', ARGV[4], 'connId', ARGV[5], 'since', ARGV[7])
redis.call('PEXPIRE', lk, ARGV[6])
redis.call('SET', 'holding:' .. a, b, 'PX', ARGV[6])
redis.call('SADD', 'locks:active', b)
return { 'OK', tostring(tonumber(ARGV[7]) + tonumber(ARGV[6])) }
