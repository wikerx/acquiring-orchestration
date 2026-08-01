local windowKey = KEYS[1]
local windowMillis = tonumber(ARGV[1])
local transactionDigest = ARGV[2]
local maxMembers = tonumber(ARGV[3])

if not windowMillis or windowMillis <= 0
        or not transactionDigest or transactionDigest == ''
        or not maxMembers or maxMembers <= 0 then
    return -2
end

local redisTime = redis.call('TIME')
local nowMillis = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
local cutoffMillis = nowMillis - windowMillis

redis.call('ZREMRANGEBYSCORE', windowKey, '-inf', cutoffMillis)
local added = redis.call('ZADD', windowKey, 'NX', nowMillis, transactionDigest)
local current = redis.call('ZCARD', windowKey)

if current > maxMembers then
    if added == 1 then
        redis.call('ZREM', windowKey, transactionDigest)
    end
    redis.call('PEXPIRE', windowKey, windowMillis)
    return -1
end

redis.call('PEXPIRE', windowKey, windowMillis)
return current
