package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherOrderService extends IService<VoucherOrder> {
    /**
     * 3.3用户下单秒杀券 3.4解决超卖  3.5保证一人一单
     *
     * @param voucherId
     * @return
     */
    long placeASeckillOrder(Long voucherId);

    /**
     * 3.5保证一人一单  抽取出的新增订单方法
     *
     * @param voucherId
     * @return
     */
    long addSeckillOrder(Long voucherId);
}
