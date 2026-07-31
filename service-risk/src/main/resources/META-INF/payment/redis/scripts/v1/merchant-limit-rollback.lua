local amount = redis.call('GET', KEYS[2])
if not amount then
    return 0
end
local current = redis.call('DECRBY', KEYS[1], amount)
redis.call('DEL', KEYS[2])
if current <= 0 then
    redis.call('DEL', KEYS[1])
    return 0
end
return current
