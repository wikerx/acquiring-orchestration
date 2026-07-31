local stateKey = KEYS[1]
local currentMillis = tonumber(ARGV[1])
local maxSequence = tonumber(ARGV[2])
local restoreFloorMillis = tonumber(ARGV[3] or '0')

local lastMillis = tonumber(redis.call('HGET', stateKey, 'last_millis') or '0')
local lastSequence = tonumber(redis.call('HGET', stateKey, 'sequence') or '0')
local effectiveMillis = currentMillis
local sequence = 1

if restoreFloorMillis > effectiveMillis then
    effectiveMillis = restoreFloorMillis
end

if lastMillis > effectiveMillis then
    effectiveMillis = lastMillis
end

if effectiveMillis == lastMillis then
    sequence = lastSequence + 1
end

redis.call('HSET', stateKey,
    'last_millis', effectiveMillis,
    'sequence', sequence)

if sequence > maxSequence then
    return {effectiveMillis, sequence, 1}
end

return {effectiveMillis, sequence, 0}
