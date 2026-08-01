local generationKey = KEYS[1]
local publicationKey = KEYS[2]
if redis.call('EXISTS', publicationKey) == 1 then
    return 'PENDING'
end
local generation = redis.call('GET', generationKey)
if not generation then
    generation = ARGV[1]
    redis.call('SET', generationKey, generation)
end
return 'ACTIVE:' .. generation
