package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/shop-type")
@Api("商铺类型相关接口")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    /**
     * 2.1查询所有商铺类型  添加查询缓存
     *
     * @return
     */
    @ApiOperation("查询所有商铺类型")
    @GetMapping("/list")
    public Result queryTypeList() {
/*        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);*/
        List<ShopType> shopTypeList = typeService.selectAll();
        return Result.ok(shopTypeList);
    }
}
