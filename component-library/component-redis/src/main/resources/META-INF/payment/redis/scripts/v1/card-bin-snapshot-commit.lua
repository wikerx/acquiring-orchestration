-- KEYS[1]: active slot pointer; KEYS[2]: publication lock; KEYS[3]: retired slot hash
-- ARGV[1]: owner token; ARGV[2]: target slot
if redis.call('GET', KEYS[2]) ~= ARGV[1] then
    return 0
end
redis.call('SET', KEYS[1], ARGV[2])
-- The script is atomic: readers see either the old pointer with its hash or the new pointer.
-- UNLINK releases the retired large hash asynchronously so the Redis event loop is not blocked.
redis.call('UNLINK', KEYS[3])
redis.call('DEL', KEYS[2])
return 1
