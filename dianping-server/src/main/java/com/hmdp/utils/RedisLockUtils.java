package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 4.1自定义redis分布式锁 实现类
 *
 * @author 王天一
 * @version 1.0
 */
//为什么不用@Component让String管理
//我觉得其实差不多 要作为bean的话就要在tryLock里定义属性了
public class RedisLockUtils implements ILock {
    private StringRedisTemplate stringRedisTemplate;
    //锁的前缀
    private static final String KEY_PREFIX = "lock:";
    private String keyName;

    public RedisLockUtils(StringRedisTemplate stringRedisTemplate, String keyName) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyName = keyName;
    }

    /**
     * 尝试获取锁
     * 基于redis 需要StringRedisTemplate 锁的key 锁的val
     *
     * @param timeoutSec 超时释放时间
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //锁的key lock:业务:粒度
        //锁的val 用线程名
        long threadId = Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + keyName, threadId + "", timeoutSec, TimeUnit.SECONDS);
        //自动拆箱可能导致空指针  所以要注意
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unLock() {
        stringRedisTemplate.delete(KEY_PREFIX + keyName);
    }
}
