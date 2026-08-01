local generationKey = KEYS[1]
local publicationKey = KEYS[2]
local token = ARGV[1]
local generation = ARGV[2]
local owner = redis.call('GET', publicationKey)
if owner == token then
    redis.call('SET', generationKey, generation)
    redis.call('DEL', publicationKey)
    return 1
end
if redis.call('GET', generationKey) == generation then
    return 1
end
return 0
