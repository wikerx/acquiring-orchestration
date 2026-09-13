local key = KEYS[1]
local member = ARGV[1]
local ttlMillis = tonumber(ARGV[2])

local added = redis.call('SADD', key, member)
if added == 1 and ttlMillis and ttlMillis > 0 then
    redis.call('PEXPIRE', key, ttlMillis)
end
return added
