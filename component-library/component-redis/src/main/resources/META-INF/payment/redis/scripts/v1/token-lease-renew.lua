-- KEYS[1]: lease key
-- ARGV[1]: owner token; ARGV[2]: lease duration in milliseconds
if redis.call('GET', KEYS[1]) ~= ARGV[1] then
    return 0
end
redis.call('PEXPIRE', KEYS[1], ARGV[2])
return 1
