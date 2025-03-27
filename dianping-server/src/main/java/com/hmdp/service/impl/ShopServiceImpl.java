package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.hmdp.entity.Shop;
import com.hmdp.exception.DataNotFoundException;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /*@Override
    public Shop selectById(Long id) {
        //1从reids中查询商铺缓存   这里缓存数据结构选的string演示
        String shopCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2有就直接返回
        if (StrUtil.isNotBlank(shopCache)) {
            //转换成shop对象
            Shop shop = JSON.parseObject(shopCache, Shop.class);
            return shop;
        }
        //3没有就查询数据库
        Shop shop = getById(id);//ctrl+b进去 是mybatisplus提供的查询方法
        //4也要判断存不存在
        if (shop == null)
            throw new DataNotFoundException("店铺信息不存在！");
        //5新增缓存
        String shopJson = JSON.toJSONString(shop);//转成json
        //添加超时剔除时间
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }*/

    /**
     * 2.3解决商铺缓存穿透问题
     *
     * @param id
     * @return
     */
    @Override
    public Shop selectById(Long id) {
        //1从reids中查询商铺缓存   这里缓存数据结构选的string演示
        String shopCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2有就直接返回
        if (StrUtil.isNotBlank(shopCache)) {
            //转换成shop对象
            Shop shop = JSON.parseObject(shopCache, Shop.class);
            return shop;
        }
        //2.3查看isNotBlank源码 为null和空串时都视为空 所以如果redis查出了""即缓存穿透 时不会进if 所以要加一个判断 不然就又到下面去了
        if (shopCache != null) {
            throw new DataNotFoundException("店铺信息不存在！");
        }

        //3没有就查询数据库
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
}
