-- 释放redis分布式锁的Lua脚本

-- 获取当前线程标识
local currentThreadTag=ARGV[1]
--获取锁的线程标识
local lockThreadTag=redis.call('get',KEYS[1])
--比较
if (currentThreadTag==lockThreadTag) then
	return redis.call('del',KEYS[1])
end
return 0
-- 返回0或1