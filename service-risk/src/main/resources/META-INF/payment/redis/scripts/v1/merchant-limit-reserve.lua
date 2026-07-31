local aggregateKey = KEYS[1]
local reservationKey = KEYS[2]
local amount = tonumber(ARGV[1])
local ttlSeconds = tonumber(ARGV[2])
local limitAmount = tonumber(ARGV[3])
local enforceLimit = ARGV[4] == '1'
local seedAmount = ARGV[5]
redis.call('SET', aggregateKey, seedAmount, 'EX', ttlSeconds, 'NX')
local current = tonumber(redis.call('GET', aggregateKey) or '0')
if redis.call('EXISTS', reservationKey) == 1 then
    return current
end
local nextAmount = current + amount
if enforceLimit and nextAmount > limitAmount then
    return 0 - nextAmount
end
redis.call('SET', reservationKey, ARGV[1], 'EX', ttlSeconds)
redis.call('INCRBY', aggregateKey, ARGV[1])
if redis.call('TTL', aggregateKey) < 0 then
    redis.call('EXPIRE', aggregateKey, ttlSeconds)
end
return nextAmount
