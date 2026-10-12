-- KEYS[1]: publication lock; KEYS[2]: failed inactive Card BIN hash
-- ARGV[1]: owner token
if redis.call('GET', KEYS[1]) ~= ARGV[1] then
    return 0
end
redis.call('UNLINK', KEYS[2])
redis.call('DEL', KEYS[1])
return 1
