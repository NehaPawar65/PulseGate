-- Fixed window counter, executed atomically by Redis.
-- Windows are aligned to the epoch (e.g. every whole minute for a 60 s window).
-- KEYS[1] = key prefix   ARGV[1] = capacity   ARGV[2] = window length in seconds
-- Returns { allowed (1/0), requests remaining, seconds until the window ends (string) }
local capacity = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

local t = redis.call('TIME')
local now = tonumber(t[1]) + tonumber(t[2]) / 1000000
local index = math.floor(now / window)
local key = KEYS[1] .. ':' .. index

local count = redis.call('INCR', key)
if count == 1 then
  redis.call('EXPIRE', key, window + 1) -- the counter disappears with its window
end

if count <= capacity then
  return { 1, capacity - count, '0' }
end
return { 0, 0, tostring((index + 1) * window - now) }
