local key = KEYS[1]
local lease_millis = tonumber(ARGV[1])
local max_concurrent = tonumber(ARGV[2])
local token = ARGV[3]

if not lease_millis or lease_millis <= 0 or not max_concurrent or max_concurrent <= 0 or not token then
    return -1
end

local redis_time = redis.call('TIME')
local now = redis_time[1] * 1000 + math.floor(redis_time[2] / 1000)
local expires_at = now + lease_millis
redis.call('ZREMRANGEBYSCORE', key, '-inf', now)
if redis.call('ZCARD', key) >= max_concurrent then
    return 0
end

redis.call('ZADD', key, expires_at, token)
redis.call('PEXPIRE', key, lease_millis)
return 1
