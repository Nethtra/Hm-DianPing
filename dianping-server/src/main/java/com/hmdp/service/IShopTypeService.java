package com.hmdp.service;

import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询所有商铺类型  添加查询缓存
     *
     * @return
     */
    List<ShopType> selectAll();
}
