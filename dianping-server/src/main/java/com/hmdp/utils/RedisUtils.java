package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.hmdp.entity.Shop;
import com.hmdp.entity.RedisData;
import com.hmdp.exception.DataNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.constant.RedisConstants.*;

/**
 * 2.6Redis缓存封装工具类
 *
 * @author 王天一
 * @version 1.0
 */
@Slf4j
@Component
public class RedisUtils {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILE_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 2.6.1将任意java对象序列化为jsonString并存储到String类型的v中，有ttl
     *
     * @param key      key
     * @param value    java对象
     * @param time     过期时间
     * @param timeUnit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        String jsonStr = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, jsonStr, time, timeUnit);
    }

    /**
     * 2.6.2将任意java对象序列化为jsonString并存储到String类型的v中，有逻辑过期时间  (热点数据)
     *
     * @param key      key
     * @param value    java对象
     * @param time     过期时间
     * @param timeUnit 时间单位
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = RedisData.builder()
                .data(value)
                .expireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)))//为了赶快过期测试 设置成秒
                .build();
        String jsonStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key, jsonStr);
    }


    /**
     * 2.6.3根据指定key查询缓存，反序列化返回指定类型的java对象，并用缓存空值的方式解决 缓存穿透 问题
     *
     * @param prefix     key前缀
     * @param id         id
     * @param type       反序列化类型
     * @param dbFallback 未命中查询数据库的逻辑
     * @param time       过期时间
     * @param timeUnit   时间单位
     * @param <R>        反序列化类型
     * @return
     */
    public <R> R Query1(String prefix, Long id, Class<R> type, Function<Long, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = prefix + id;
        //1从redis中查询缓存
        String cacheData = stringRedisTemplate.opsForValue().get(key);
        //2命中直接返回
        if (StrUtil.isNotBlank(cacheData)) {
            //转换成R对象
            R r = JSON.parseObject(cacheData, type);
            return r;
        }
        //2.3查看isNotBlank源码 为null和空串时都视为空 所以如果redis查出了""即缓存穿透 时不会进if 所以要加一个判断 不然就又到下面去了
        //缓存穿透时
        if (cacheData != null) {
            throw new DataNotFoundException("信息不存在！");
        }

        //3未命中就查询数据库
        R r = dbFallback.apply(id);//由于工具类不能查数据库，所以这段逻辑就由调用者传 定义一个Function 类型的参数 代表一个有参有返回值的函数
        //由调用者传这个函数 就是lambda表达式  函数式编程
        //4也要判断存不存在
        if (r == null) {
            //2.3数据库中不存在  缓存空串""  注意ttl不一样
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            throw new DataNotFoundException("信息不存在！");
        }
        //5新增缓存
        //cacheData = JSON.toJSONString(r);//不需要自己转
        this.set(key, r, time, timeUnit);//直接调上面的方法
        return r;
    }


    /**
     * 2.6.4根据指定key查询缓存，反序列化返回指定类型的java对象，并用逻辑过期的方式解决 缓存击穿 问题
     *
     * @param prefix key前缀
     * @param id id
     * @param type 反序列化类型
     * @param dbFallback 未命中查询数据库的逻辑
     * @param time 过期时间
     * @param timeUnit 时间单位
     * @param <R>
     * @return
     */
    public <R> R Query2(String prefix, Long id, Class<R> type, Function<Long, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = prefix + id;
        //1从redis中查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        //2因为热点数据已经提前缓存  所以必须命中
        //未命中
        if (StrUtil.isBlank(shopCache)) {
            throw new DataNotFoundException("热点店铺信息不存在！");
        }

        //3命中   先转回shop
        /*RedisData redisData = JSON.parseObject(shopCache, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = data.toJavaObject(Shop.class);
        System.out.println(redisData.getExpireTime());*/
        RedisData redisData = JSONUtil.toBean(shopCache, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        //3,1查看逻辑过期时间  未过期直接返回
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return r;
        }
        //4逻辑过期时间过期 开始缓存重建
        //4,1尝试获取互斥锁
        boolean lock = tryLock(LOCK_SHOP_KEY + id);
        //4,2获取锁成功 开启新线程查询数据库缓存重建
        if (lock) {
            //获取锁成功应该再重新查一遍redis 再次检查逻辑时间是否过期

            //线程池提交任务
            CACHE_REBUILE_EXECUTOR.submit(() -> {
                try {//缓存重建
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //存到redis
                    setWithLogicExpire(key, r1, time, timeUnit);
                    //其实就是原来的saveShop2Redis逻辑
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(LOCK_SHOP_KEY + id);//释放锁
                }
            });
        }
        //获取成功失败都要返回  失败直接走这里
        return r;
    }


    /**
     * 获取锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {//锁要设置ttl 防止未释放死锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//返回值为boolean 直接returnBoolean会自动拆箱 可能拆出null，所以为了安全用一个工具类
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
