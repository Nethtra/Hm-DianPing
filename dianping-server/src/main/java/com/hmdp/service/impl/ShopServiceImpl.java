package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.exception.DataNotFoundException;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisUtils;
import lombok.Synchronized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisUtils redisUtils;
    //线程池
    private static final ExecutorService CACHE_REBUILE_EXECUTOR = Executors.newFixedThreadPool(10);


    @Override
    public Shop selectById(Long id) {
//        return selectById4(id);
//        return redisUtils.Query1(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return redisUtils.Query2(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
    }

    /**
     * 2.1查询商铺添加缓存
     *
     * @param id
     * @return
     */
    public Shop selectById1(Long id) {
        //1从redis中查询商铺缓存   这里缓存数据结构选的string演示
        String shopCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2命中就直接返回
        if (StrUtil.isNotBlank(shopCache)) {
            //转换成shop对象
            Shop shop = JSON.parseObject(shopCache, Shop.class);
            return shop;
        }
        //3未命中就查询数据库
        Shop shop = getById(id);//ctrl+b进去 是mybatisplus提供的查询方法
        //4也要判断存不存在
        if (shop == null)
            throw new DataNotFoundException("店铺信息不存在！");
        //5新增缓存
        String shopJson = JSON.toJSONString(shop);//转成json
        //添加超时剔除时间
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 2.3缓存空值解决商铺查询缓存穿透问题
     *
     * @param id
     * @return
     */
    //@Synchronized
    public Shop selectById2(Long id) {
        //1从redis中查询商铺缓存   这里缓存数据结构选的string演示
        String shopCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2命中直接返回
        if (StrUtil.isNotBlank(shopCache)) {
            //转换成shop对象
            Shop shop = JSON.parseObject(shopCache, Shop.class);
            return shop;
        }
        //2.3查看isNotBlank源码 为null和空串时都视为空 所以如果redis查出了""即缓存穿透 时不会进if 所以要加一个判断 不然就又到下面去了
        //缓存穿透时
        if (shopCache != null) {
            throw new DataNotFoundException("店铺信息不存在！");
        }

        //3未命中就查询数据库
        Shop shop = getById(id);//ctrl+b进去 是mybatisplus提供的查询方法
        //4也要判断存不存在
        if (shop == null) {
            //2.3数据库中不存在  缓存空串""  注意ttl不一样
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            throw new DataNotFoundException("店铺信息不存在！");
        }
        //5新增缓存
        String shopJson = JSON.toJSONString(shop);//转成json
        //添加超时剔除时间
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 2.4基于互斥锁解决商铺查询缓存击穿问题
     *
     * @param id
     * @return
     */
    public Shop selectById3(Long id) {
        //1从redis中查询商铺缓存   这里缓存数据结构选的string演示
        String shopCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2命中直接返回
        if (StrUtil.isNotBlank(shopCache)) {
            //转换成shop对象
            Shop shop = JSON.parseObject(shopCache, Shop.class);
            return shop;
        }
        //2.3查看isNotBlank源码 为null和空串时都视为空 所以如果redis查出了""即缓存穿透 时不会进if 所以要加一个判断 不然就又到下面去了
        //缓存穿透时
        if (shopCache != null) {
            throw new DataNotFoundException("店铺信息不存在！");
        }

        //3未命中就查询数据库
        //3,1尝试获取锁
        boolean lock = tryLock(LOCK_SHOP_KEY + id);
        //3,2没有拿到锁 休眠一段时间然后递归
        Shop shop = null;//ctrl+b进去 是mybatisplus提供的查询方法
        try {
            if (!lock) {
                Thread.sleep(100);
                return selectById3(id);
            }
            //3,3拿到锁就和之前一样查询数据库
            //拿到锁之后应该再检查一遍redis
            shop = getById(id);
            Thread.sleep(200);//模拟缓存重建时间
            //4也要判断存不存在
            if (shop == null) {
                //2.3数据库中不存在  缓存空串""  注意ttl不一样
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                throw new DataNotFoundException("店铺信息不存在！");
            }
            //5新增缓存
            String shopJson = JSON.toJSONString(shop);//转成json
            //添加超时剔除时间
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //6最后释放锁
            unLock(LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    /**
     * 2.5基于逻辑过期解决查询商铺缓存击穿问题
     *
     * @param id
     * @return
     */
    public Shop selectById4(Long id) {
        //1从redis中查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
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
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //3,1查看逻辑过期时间  未过期直接返回
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop;
        }
        //4逻辑过期时间过期 开始缓存重建
        //4,1尝试获取互斥锁
        boolean lock = tryLock(LOCK_SHOP_KEY + id);
        //4,2获取锁成功 开启新线程查询数据库缓存重建
        if (lock) {
            //获取锁成功应该再重新查一遍redis 再次检查逻辑时间是否过期

            //线程池提交任务
            CACHE_REBUILE_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, CACHE_SHOP_TTL);//为了赶快过期测试 逻辑过期时间设置的短一点
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(LOCK_SHOP_KEY + id);//释放锁
                }
            });
        }
        //获取成功失败都要返回  失败直接走这里
        return shop;
    }

    @Override
    @Transactional//开启事务
    public void update(Shop shop) {
        if (shop.getId() == null)
            throw new DataNotFoundException("未查询到商铺信息！");
        //先更新数据库
        updateById(shop);
        //再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
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

    /**
     * 将Shop热点数据存到（提前缓存）redis的方法(逻辑过期)
     *
     * @param id         商铺id
     * @param expireTime 过期时间s
     */
    public void saveShop2Redis(Long id, Long expireTime) throws InterruptedException {
        RedisData redisData = new RedisData();
        redisData.setData(getById(id));
        Thread.sleep(200);//模拟重建延迟
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));//为了赶快过期测试，弄成秒
        String redisDataJson = JSON.toJSONString(redisData);
        String jsonStr = JSONUtil.toJsonStr(redisData);//hutool的这个转json字符串会将LocalDateTime转成时间戳
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr);//已经有逻辑过期时间 就不用真正的ttl
    }

}
