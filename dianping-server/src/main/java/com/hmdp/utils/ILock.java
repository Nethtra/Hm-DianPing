package com.hmdp.utils;

/**
 * 4.1自定义redis分布式锁接口
 *
 * @author 王天一
 * @version 1.0
 */
public interface ILock {
    /**
     * 尝试获取锁
     *
     * @param timeoutSec 超时释放时间
     * @return true成功  false失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();
}
