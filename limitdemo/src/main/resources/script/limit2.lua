--KEYS[1]: 限流 key
--ARGV[1]: 当前时间戳（作为score）
--ARGV[2]: 时间戳- 时间窗口
--ARGV[3]: key有效时间

redis.call('ZADD', KEYS[1], tonumber(ARGV[1]), ARGV[1])
local c= redis.call('ZCARD', KEYS[1])
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, tonumber(ARGV[2]))
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
return c


