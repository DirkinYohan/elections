-- Funciones compartidas. Java antepone este archivo a submit.lua y resolve.lua, así que cada
-- script se ejecuta como UNA operación indivisible en Redis (RF-3): ningún cliente puede leer
-- un nivel actualizado y otro todavía sin actualizar.

-- Aplica un acta a los cuatro niveles (mesa, puesto, municipio, departamento). Si ya había una
-- versión aplicada, primero la revierte (RF-2) y deja la explicación para el próximo evento público.
local function apply_sheet(acta, version, votos, mesa, puesto, municipio, now, reason)
  local akey = 'applied:' .. acta
  local prevJson = redis.call('HGET', akey, 'votos')
  local prev = nil
  if prevJson then prev = cjson.decode(prevJson) end
  local prevVersion = tonumber(redis.call('HGET', akey, 'version') or '0')
  local levels = { 'table:' .. mesa, 'station:' .. puesto, 'municipality:' .. municipio, 'department' }
  for i, topic in ipairs(levels) do
    local tkey = 'tot:' .. topic
    if prev then
      for k, v in pairs(prev) do
        redis.call('HINCRBY', tkey, k, -v)
        if string.sub(k, 1, 1) == 'C' then redis.call('ZINCRBY', 'rank:' .. topic, -v, k) end
      end
    else
      redis.call('HINCRBY', tkey, '_reported', 1)
    end
    for k, v in pairs(votos) do
      redis.call('HINCRBY', tkey, k, v)
      if string.sub(k, 1, 1) == 'C' then redis.call('ZINCRBY', 'rank:' .. topic, v, k) end
    end
    if i > 1 then -- la mesa no es un tópico público
      redis.call('SADD', 'dirty', topic)
      redis.call('HSETNX', 'dirty:at', topic, now)
      if prev then
        redis.call('RPUSH', 'pendcorr:' .. topic,
          cjson.encode({ ballotId = acta, fromVersion = prevVersion, toVersion = version, reason = reason }))
      end
    end
  end
  redis.call('HSET', akey, 'version', version, 'votos', cjson.encode(votos),
    'mesa', mesa, 'puesto', puesto, 'municipio', municipio)
  redis.call('INCR', 'stat:applied')
end
