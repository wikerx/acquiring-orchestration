-- KEYS[1]: value hash; KEYS[2]: logical expiry zset
-- ARGV[1]: normalized IP digest; ARGV[2]: maximum incremental cleanup count
local time = redis.call('TIME')
local now = (tonumber(time[1]) * 1000) + math.floor(tonumber(time[2]) / 1000)
local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now, 'LIMIT', 0, tonumber(ARGV[2]))
if #expired > 0 then
    redis.call('HDEL', KEYS[1], unpack(expired))
    redis.call('ZREM', KEYS[2], unpack(expired))
end
local expiresAt = redis.call('ZSCORE', KEYS[2], ARGV[1])
if not expiresAt then
    return nil
end
if tonumber(expiresAt) <= now then
    redis.call('HDEL', KEYS[1], ARGV[1])
    redis.call('ZREM', KEYS[2], ARGV[1])
    return nil
end
local payload = redis.call('HGET', KEYS[1], ARGV[1])
if not payload then
    redis.call('ZREM', KEYS[2], ARGV[1])
    return nil
end
return payload
