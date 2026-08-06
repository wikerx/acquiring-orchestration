local key = KEYS[1]
local lease_millis = tonumber(ARGV[1])
local token = ARGV[2]

if not lease_millis or lease_millis <= 0 or not token then
    return -1
end

if not redis.call('ZSCORE', key, token) then
    return 0
end

local redis_time = redis.call('TIME')
local now = redis_time[1] * 1000 + math.floor(redis_time[2] / 1000)
redis.call('ZADD', key, now + lease_millis, token)
redis.call('PEXPIRE', key, lease_millis)
return 1
