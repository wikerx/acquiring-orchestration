-- KEYS[1]: publication lock; KEYS[2]: inactive Card BIN hash
-- ARGV[1]: owner token; ARGV[2]: lease millis
local acquired = redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX')
if not acquired then
    return 0
end
redis.call('UNLINK', KEYS[2])
return 1
