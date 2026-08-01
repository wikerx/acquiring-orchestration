local publicationKey = KEYS[1]
local acquired = redis.call('SET', publicationKey, ARGV[1], 'PX', ARGV[2], 'NX')
if acquired then
    return 1
end
return 0
