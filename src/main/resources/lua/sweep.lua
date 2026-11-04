-- Devuelve los casos cuyo bloqueo expiró solo (auditor que se fue) y los saca del índice.
-- Como el script es atómico, si varias instancias lo corren a la vez solo una recibe cada caso.
local out = {}
for _, b in ipairs(redis.call('SMEMBERS', 'locks:active')) do
  if redis.call('EXISTS', 'lock:' .. b) == 0 then
    redis.call('SREM', 'locks:active', b)
    out[#out + 1] = b
  end
end
return out
