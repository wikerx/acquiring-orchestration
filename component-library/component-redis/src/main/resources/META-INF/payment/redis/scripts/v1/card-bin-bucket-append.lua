-- KEYS[1]: inactive Card BIN hash; KEYS[2]: publication lock
-- ARGV[1]: owner token; ARGV[2]: lease millis; remaining args are field/JSON pairs
if redis.call('GET', KEYS[2]) ~= ARGV[1] then
    return -1
end
if ((#ARGV - 2) % 2) ~= 0 then
    return redis.error_reply('card BIN bucket append requires field/value pairs')
end

local appended = 0
for index = 3, #ARGV, 2 do
    local field = ARGV[index]
    local value = ARGV[index + 1]
    local current = redis.call('HGET', KEYS[1], field)
    if current then
        redis.call('HSET', KEYS[1], field, string.sub(current, 1, -2) .. ',' .. value .. ']')
    else
        redis.call('HSET', KEYS[1], field, '[' .. value .. ']')
    end
    appended = appended + 1
end
redis.call('PEXPIRE', KEYS[2], ARGV[2])
return appended
