local transactionIndexKey = KEYS[1]
local action = ARGV[1]
if redis.call('EXISTS', transactionIndexKey) == 0 then
    return {0}
end

local status = redis.call('HGET', transactionIndexKey, '@status')
if action == 'CONFIRM' then
    if status == 'OPEN' then
        redis.call('HSET', transactionIndexKey, '@status', 'CONFIRMED')
        return {1}
    end
    if status == 'CONFIRMED' then
        return {2}
    end
    return {-1}
end

if action ~= 'RELEASE' then
    return {-2}
end
if status == 'CONFIRMED' then
    return {-1}
end
if status == 'RELEASED' then
    return {3}
end
local code = 2
if status == 'OPEN' then
    redis.call('HSET', transactionIndexKey, '@status', 'RELEASING')
    code = 1
elseif status ~= 'RELEASING' then
    return {-1}
end

local result = {code}
local fields = redis.call('HKEYS', transactionIndexKey)
for _, field in ipairs(fields) do
    if field ~= '@status' then
        table.insert(result, field)
    end
end
return result
