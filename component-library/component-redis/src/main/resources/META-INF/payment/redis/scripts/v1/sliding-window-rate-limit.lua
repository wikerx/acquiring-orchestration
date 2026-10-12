-- KEYS[1]: one identity ZSET
-- ARGV: limit, windowMillis, unique request token
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
if not limit or limit <= 0 or not window or window <= 0 then
    return redis.error_reply('invalid sliding window limit')
end
local time = redis.call('TIME')
local now = (tonumber(time[1]) * 1000) + math.floor(tonumber(time[2]) / 1000)
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
local count = redis.call('ZCARD', KEYS[1])
if count >= limit then
    local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
    local retry = 1
    if #oldest == 2 then
        retry = math.max(1, math.ceil((tonumber(oldest[2]) + window - now) / 1000))
    end
    return {0, retry}
end
redis.call('ZADD', KEYS[1], now, tostring(now) .. ':' .. ARGV[3])
redis.call('PEXPIRE', KEYS[1], window)
return {1, 0}
