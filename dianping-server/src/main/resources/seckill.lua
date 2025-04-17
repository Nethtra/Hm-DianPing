-- 6.2判断秒杀券库存和一人一单的lua脚本    照着逻辑图写
-- 先想要用到的变量
-- 秒杀券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 秒杀券库存的key    注意lua中的拼接字符串 俩点..
local stockKey = 'seckill:stock:' .. voucherId
-- 秒杀券的set集合的key 保存的是已经下单的用户userId
local orderKey = 'seckill:order:' .. voucherId

-- 判断库存 tonumber将字符串转成数字  因为从redis拿出来的是string
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 没有库存
    return 1
end
--判断是否已经下过单
if (redis.call('sismember', userId)==1) then
    -- 已经下过单
    return 2
end

-- 扣减库存
redis.call('incrby', stockKey, -1)
-- 将该用户存入该秒杀券已经下单的用户集合
redis.call('sadd', orderKey, userId)
return 0