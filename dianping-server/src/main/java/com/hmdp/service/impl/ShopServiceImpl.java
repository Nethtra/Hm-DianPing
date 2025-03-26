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

import static com.hmdp.constant.RedisConstants.CACHE_SHOP_KEY;

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
        //3没有就查询数据库
        Shop shop = getById(id);//ctrl+b进去 是mybatisplus提供的查询方法
        //4也要判断存不存在
        if (shop == null)
            throw new DataNotFoundException("店铺信息不存在！");
        //5新增缓存
        String shopJson = JSON.toJSONString(shop);//转成json
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson);
        return shop;
    }
}
