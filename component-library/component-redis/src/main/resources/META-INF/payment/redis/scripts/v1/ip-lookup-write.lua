-- KEYS[1]: value hash; KEYS[2]: logical expiry zset
-- ARGV: digest, payload, ttlMillis, maxEntries, cleanupCount, namespaceTtlMillis
local time = redis.call('TIME')
local now = (tonumber(time[1]) * 1000) + math.floor(tonumber(time[2]) / 1000)
local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now, 'LIMIT', 0, tonumber(ARGV[5]))
if #expired > 0 then
    redis.call('HDEL', KEYS[1], unpack(expired))
    redis.call('ZREM', KEYS[2], unpack(expired))
end
if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 0 then
    local size = redis.call('ZCARD', KEYS[2])
    local overflow = size - tonumber(ARGV[4]) + 1
    if overflow > 0 then
        local oldest = redis.call('ZRANGE', KEYS[2], 0, overflow - 1)
        if #oldest > 0 then
            redis.call('HDEL', KEYS[1], unpack(oldest))
            redis.call('ZREM', KEYS[2], unpack(oldest))
        end
    end
end
redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
redis.call('ZADD', KEYS[2], now + tonumber(ARGV[3]), ARGV[1])
-- 版本化 Key 在路由停止使用后自然回收；活跃版本每次写入都会延长物理寿命。
redis.call('PEXPIRE', KEYS[1], ARGV[6])
redis.call('PEXPIRE', KEYS[2], ARGV[6])
return 1
