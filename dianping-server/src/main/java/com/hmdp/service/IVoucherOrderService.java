package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherOrderService extends IService<VoucherOrder> {
    /**
     * 3.3用户下单秒杀券
     * @param voucherId
     * @return
     */
    long placeAnSeckillOrder(Long voucherId);
}
