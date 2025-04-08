package com.hmdp.service;

import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IShopService extends IService<Shop> {

    /**
     * 2.1根据id查询商铺信息   添加商铺缓存
     * @param id
     * @return
     */
    Shop selectById(Long id);

    /**
     * 2.2更新商铺信息 同时删除缓存
     * @param shop
     */
    void update(Shop shop);
}
