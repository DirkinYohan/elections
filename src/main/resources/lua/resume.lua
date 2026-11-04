-- Reconexión de un cliente público (RF-6). Devuelve una lista plana:
--   {'SNAPSHOT', lastId, k1, v1, k2, v2...}  estado publicado + id del último evento del tópico
--   {'REPLAY', id1, type1, data1, id2, ...}  solo lo que el cliente se perdió
--   {'UPTODATE', lastId}                     no se perdió nada
-- ARGV: 1 topic, 2 lastEventId ('' si no trae), 3 maxReplay
local topic, lastId, maxReplay = ARGV[1], ARGV[2], tonumber(ARGV[3])
local skey = 'stream:' .. topic

local function parse(id)
  local ms, seq = string.match(id, '^(%d+)%-?(%d*)$')
  if not ms then return nil end
  if seq == '' then seq = '0' end
  return tonumber(ms), tonumber(seq)
end

local function cmp(a, b)
  local am, as = parse(a)
  local bm, bs = parse(b)
  if am ~= bm then
    if am < bm then return -1 else return 1 end
  end
  if as ~= bs then
    if as < bs then return -1 else return 1 end
  end
  return 0
end

local function snapshot()
  local last = redis.call('XREVRANGE', skey, '+', '-', 'COUNT', 1)
  local id = '0-0'
  if #last > 0 then id = last[1][1] end
  local out = { 'SNAPSHOT', id }
  local h = redis.call('HGETALL', 'pub:' .. topic)
  for i = 1, #h do out[#out + 1] = h[i] end
  return out
end

if lastId == '' or not parse(lastId) then return snapshot() end
local first = redis.call('XRANGE', skey, '-', '+', 'COUNT', 1)
local last = redis.call('XREVRANGE', skey, '+', '-', 'COUNT', 1)
if #first == 0 then return snapshot() end
if cmp(lastId, last[1][1]) == 0 then return { 'UPTODATE', lastId } end
if cmp(lastId, first[1][1]) < 0 or cmp(lastId, last[1][1]) > 0 then return snapshot() end

local entries = redis.call('XRANGE', skey, '(' .. lastId, '+', 'COUNT', maxReplay + 1)
if #entries > maxReplay then return snapshot() end -- demasiado atrasado: conviene la fotografía
local out = { 'REPLAY' }
for _, e in ipairs(entries) do
  local f = {}
  for i = 1, #e[2], 2 do f[e[2][i]] = e[2][i + 1] end
  out[#out + 1] = e[1]
  out[#out + 1] = f.type
  out[#out + 1] = f.data
end
return out
