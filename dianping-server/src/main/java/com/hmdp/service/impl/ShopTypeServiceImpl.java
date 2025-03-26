package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.hmdp.entity.ShopType;
import com.hmdp.exception.DataNotFoundException;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.hmdp.constant.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> selectAll() {
        //注意是一个集合  redis数据结构可以选string
        //查询redis缓存
        String shopTypeListCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        //有就直接返回
        if (StrUtil.isNotBlank(shopTypeListCache)) {
            List<ShopType> shopTypeList = JSON.parseArray(shopTypeListCache, ShopType.class);
            return shopTypeList;
        }
        //没有就查数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        if (shopTypeList == null) {
            throw new DataNotFoundException("商铺类型不存在！");
        }
        //写入缓存
        String shopTypeListJson = JSON.toJSONString(shopTypeList);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, shopTypeListJson);
        return shopTypeList;
    }
}
