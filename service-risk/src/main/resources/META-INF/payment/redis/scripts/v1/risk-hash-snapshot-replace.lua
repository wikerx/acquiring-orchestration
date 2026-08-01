redis.call('DEL', KEYS[1])
for index = 1, #ARGV, 2 do
    redis.call('HSET', KEYS[1], ARGV[index], ARGV[index + 1])
end
return #ARGV / 2
