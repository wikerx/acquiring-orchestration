-- KEYS[1]: one three-digit Card BIN shard hash
-- ARGV[1]: maximum candidates per prefix field
-- Remaining args are database-derived prefix field/JSON pairs.
if ((#ARGV - 1) % 2) ~= 0 then
    return redis.error_reply('card BIN prefix append requires field/value pairs')
end

local appended = 0
local maxCandidates = tonumber(ARGV[1])
if not maxCandidates or maxCandidates < 1 then
    return redis.error_reply('card BIN candidate limit is invalid')
end
for index = 2, #ARGV, 2 do
    local field = ARGV[index]
    local value = ARGV[index + 1]
    local current = redis.call('HGET', KEYS[1], field)
    if current then
        if #cjson.decode(current) >= maxCandidates then
            return redis.error_reply('card BIN prefix candidate limit exceeded')
        end
        redis.call('HSET', KEYS[1], field, string.sub(current, 1, -2) .. ',' .. value .. ']')
    else
        redis.call('HSET', KEYS[1], field, '[' .. value .. ']')
    end
    appended = appended + 1
end
return appended
