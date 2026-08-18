local transactionIndexKey = KEYS[1]
if redis.call('HGET', transactionIndexKey, '@status') ~= 'RELEASING' then
    return 0
end

local released = 0
for index = 2, #KEYS do
    local counterKey = KEYS[index]
    if redis.call('HEXISTS', transactionIndexKey, counterKey) == 1 then
        local current = tonumber(redis.call('GET', counterKey) or '0')
        if current > 0 then
            local remaining = redis.call('DECR', counterKey)
            if remaining <= 0 then
                redis.call('DEL', counterKey)
            end
        end
        redis.call('HDEL', transactionIndexKey, counterKey)
        released = released + 1
    end
end
redis.call('HSET', transactionIndexKey, '@status', 'RELEASED')
return released
