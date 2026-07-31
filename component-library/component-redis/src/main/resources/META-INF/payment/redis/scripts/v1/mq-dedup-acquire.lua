local currentBucketKey = KEYS[1]
local previousBucketKey = KEYS[2]
local member = ARGV[1]
local ttlMillis = tonumber(ARGV[2])
local maxMembers = tonumber(ARGV[3])

if not ttlMillis or ttlMillis <= 0 or not maxMembers or maxMembers <= 0 then
    return -2
end

local redisTime = redis.call('TIME')
local nowMillis = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)
local cutoffMillis = nowMillis - ttlMillis

redis.call('ZREMRANGEBYSCORE', currentBucketKey, '-inf', cutoffMillis)
redis.call('ZREMRANGEBYSCORE', previousBucketKey, '-inf', cutoffMillis)

if redis.call('ZSCORE', currentBucketKey, member)
        or redis.call('ZSCORE', previousBucketKey, member) then
    return 0
end

if redis.call('ZCARD', currentBucketKey) >= maxMembers then
    return -1
end

redis.call('ZADD', currentBucketKey, 'NX', nowMillis, member)
redis.call('PEXPIRE', currentBucketKey, ttlMillis * 2)
return 1
