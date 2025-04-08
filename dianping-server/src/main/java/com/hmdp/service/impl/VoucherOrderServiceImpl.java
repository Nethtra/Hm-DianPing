package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BaseException;
import com.hmdp.exception.OutOfStockException;
import com.hmdp.exception.TimeStateErrorException;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.hmdp.constant.RedisConstants.VOUCHER_ORDER_PREFIX;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public long placeAnSeckillOrder(Long voucherId) {
        //1根据id查询秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2是否未到开始时间
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            throw new TimeStateErrorException("未到开始抢购时间！");
        }
        //3是否已到结束时间
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            throw new TimeStateErrorException("抢购时间已结束！");
        }

        //4是否还有库存
        if (seckillVoucher.getStock() < 1) {
            throw new OutOfStockException("来晚了，优惠券卖完了！");
        }
        //5下单库存-1
        boolean success = seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherId).update();
        if (!success) {
            throw new BaseException("未知错误！");
        }
        //6生成订单填写订单信息
        //只需要填前三个字段
        long seckillVoucherOrderId = redisIdWorker.nextId(VOUCHER_ORDER_PREFIX);
        VoucherOrder order = VoucherOrder.builder()
                .id(seckillVoucherOrderId)
                .userId(UserHolder.getUser().getId())
                .voucherId(voucherId)
                .build();
        save(order);
        //7返回订单id
        return seckillVoucherOrderId;
    }
}
