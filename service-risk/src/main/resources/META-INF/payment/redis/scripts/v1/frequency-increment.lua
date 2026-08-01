local counterKey = KEYS[1]
local transactionKey = KEYS[2]
local ttlSeconds = tonumber(ARGV[1])
if redis.call('EXISTS', transactionKey) == 1 then
    return tonumber(redis.call('GET', counterKey) or '0')
end
local current = redis.call('INCR', counterKey)
if current == 1 or redis.call('TTL', counterKey) < 0 then
    redis.call('EXPIRE', counterKey, ttlSeconds)
end
redis.call('SET', transactionKey, 'COUNTED', 'EX', ttlSeconds)
return current
