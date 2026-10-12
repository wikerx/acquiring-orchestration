-- KEYS[1]: active slot pointer; KEYS[2]/KEYS[3]: slot a/b hashes
-- ARGV[1]: six-digit bucket
local slot = redis.call('GET', KEYS[1])
if slot == 'a' then
    return redis.call('HGET', KEYS[2], ARGV[1]) or ''
end
if slot == 'b' then
    return redis.call('HGET', KEYS[3], ARGV[1]) or ''
end
return nil
