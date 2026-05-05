local voucherId = ARGV[1]
local userId = ARGV[2]

-- 库存 key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单 key
local orderKey = 'seckill:order:' .. voucherId

-- 判断库存
local stock = redis.call('get', stockKey)

if not stock then
    return 1
end

if tonumber(stock) <= 0 then
    return 1
end

-- 判断用户是否下单
if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

-- 扣减库存
redis.call('incrby', stockKey, -1)

-- 保存用户
redis.call('sadd', orderKey, userId)

return 0