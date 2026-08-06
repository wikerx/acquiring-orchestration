local counterKey = KEYS[1]
local transactionIndexKey = KEYS[2]
local successLimit = tonumber(ARGV[1])
local ttlSeconds = tonumber(ARGV[2])
local current = tonumber(redis.call('GET', counterKey) or '0')

if redis.call('HEXISTS', transactionIndexKey, counterKey) == 1 then
    return {2, current}
end

local transactionStatus = redis.call('HGET', transactionIndexKey, '@status')
if transactionStatus and transactionStatus ~= 'OPEN' then
    return {-1, current}
end
if current >= successLimit then
    return {0, current}
end

local nextCount = redis.call('INCR', counterKey)
if nextCount == 1 or redis.call('TTL', counterKey) < 0 then
    redis.call('EXPIRE', counterKey, ttlSeconds)
end
redis.call('HSET', transactionIndexKey, '@status', 'OPEN', counterKey, '1')
local indexTtl = redis.call('TTL', transactionIndexKey)
if indexTtl < ttlSeconds then
    redis.call('EXPIRE', transactionIndexKey, ttlSeconds)
end
return {1, nextCount}
