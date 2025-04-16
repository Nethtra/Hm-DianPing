package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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
    //锁key的前缀
    private static final String KEY_PREFIX = "lock:";
    //锁key传入的名称
    private String keyName;
    //用随机生成的UUID作为锁value的前缀  防止分布式环境线程id重复  4.2
    public static final String VALUE_PREFIX = UUID.randomUUID().toString(true) + "-";

    //4.3在类加载时就加载好lua脚本 性能好点
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    //静态代码块加载脚本
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unLock.lua"));//设置lua脚本地址
        UNLOCK_SCRIPT.setResultType(Long.class);//脚本返回类型
    }

    public RedisLockUtils(StringRedisTemplate stringRedisTemplate, String keyName) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyName = keyName;
    }

    /**
     * 尝试获取锁
     * 基于redis  需要StringRedisTemplate 锁的key 锁的val
     *
     * @param timeoutSec 超时释放时间
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //锁的key lock:业务:粒度
        //锁的val 用uuid拼上线程名   4.2
        String threadTag = VALUE_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + keyName, threadTag, timeoutSec, TimeUnit.SECONDS);
        //自动拆箱可能导致空指针  所以要注意
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁   4.2释放时先判断是不是当前线程在释放
     */
    /*@Override
    public void unLock() {
        String currentThreadTag = VALUE_PREFIX + Thread.currentThread().getId();//当前线程标识
        String lockThreadTag = stringRedisTemplate.opsForValue().get(KEY_PREFIX + keyName);//锁的线程标识
        if (currentThreadTag.equals(lockThreadTag))//如果符合就释放
            stringRedisTemplate.delete(KEY_PREFIX + keyName);
    }*/

    /**
     * 4.3释放锁的原子性问题
     */
    @Override
    public void unLock() {
        //execute要传入的第一个参数是RedisScript类型 就是脚本的类型 用这个类接收lua脚本文件
        //定义成类的属性 类加载时赋值
        //                          脚本文件               key集合即KEYS[]                                           其他参数即ARGV[]
        Long execute = stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + keyName), VALUE_PREFIX + Thread.currentThread().getId());
        //原来分两步的redis操作使用lua脚本变成了一个原子性操作
    }
}
