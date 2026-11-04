-- Cierre de una ventana de difusión (RF-4, RF-6). Para cada tópico que cambió calcula el delta
-- mínimo (valores ABSOLUTOS nuevos de lo que cambió), lo escribe en el stream global 'feed'
-- (backplane que leen las 8 instancias) y en el stream del tópico (historial para reconexión).
-- ARGV: 1 nowMs, 2 feedMaxlen, 3 topicMaxlen
local now = ARGV[1]
local topics = redis.call('SMEMBERS', 'dirty')
redis.call('DEL', 'dirty')
local written = 0
for _, topic in ipairs(topics) do
  local cur = redis.call('HGETALL', 'tot:' .. topic)
  local prev = redis.call('HGETALL', 'pub:' .. topic)
  local pm = {}
  for i = 1, #prev, 2 do pm[prev[i]] = prev[i + 1] end
  local votes, nv, reported = {}, 0, nil
  for i = 1, #cur, 2 do
    local k, v = cur[i], cur[i + 1]
    if pm[k] ~= v then
      if k == '_reported' then reported = tonumber(v) else votes[k] = tonumber(v); nv = nv + 1 end
    end
  end
  local corr = redis.call('LRANGE', 'pendcorr:' .. topic, 0, -1)
  redis.call('DEL', 'pendcorr:' .. topic)
  local firstAt = redis.call('HGET', 'dirty:at', topic) or now
  redis.call('HDEL', 'dirty:at', topic)
  if nv > 0 or reported or #corr > 0 then
    local changes = {}
    if nv > 0 then changes.votes = votes end
    if reported then changes.tablesReported = reported end
    local payload = { topic = topic, changes = changes, at = tonumber(now) }
    local etype = 'delta'
    if #corr > 0 then
      etype = 'correction'
      local list = {}
      for i, c in ipairs(corr) do list[i] = cjson.decode(c) end
      payload.corrections = list
    end
    payload.type = etype
    local data = cjson.encode(payload)
    local id = redis.call('XADD', 'feed', 'MAXLEN', '~', ARGV[2], '*',
      'topic', topic, 'type', etype, 'data', data, 'firstAt', firstAt)
    redis.call('XADD', 'stream:' .. topic, 'MAXLEN', '~', ARGV[3], id, 'type', etype, 'data', data)
    redis.call('DEL', 'pub:' .. topic)
    if #cur > 0 then redis.call('HSET', 'pub:' .. topic, unpack(cur)) end
    written = written + 1
  end
end
return written
