-- Token bucket, executed atomically by Redis: no two gateway instances can interleave
-- between reading and updating a client's bucket.
-- KEYS[1] = bucket key   ARGV[1] = capacity   ARGV[2] = refill tokens per second
-- Returns { allowed (1/0), whole tokens remaining, seconds until one token (string) }
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])

-- Redis's own clock: every instance sees the same time, so skewed app clocks don't matter.
local t = redis.call('TIME')
local now = tonumber(t[1]) + tonumber(t[2]) / 1000000

local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1])
local ts = tonumber(state[2])
if tokens == nil then
  tokens = capacity -- a new bucket starts full
  ts = now
end

tokens = math.min(capacity, tokens + math.max(0, now - ts) * rate)

local allowed = 0
local wait = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
else
  wait = (1 - tokens) / rate
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
-- Once a bucket would be full again it carries no information: let Redis delete it.
redis.call('EXPIRE', KEYS[1], math.ceil(capacity / rate) + 60)

return { allowed, math.floor(tokens), tostring(wait) }
