package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/voucher-order")
@Api("优惠券下单相关接口")
public class VoucherOrderController {
    @Autowired
    private IVoucherOrderService voucherOrderService;

    /**
     * 3.3用户下单秒杀券
     *
     * @param voucherId
     * @return
     */
    @ApiOperation("用户下单秒杀券")
    @PostMapping("/seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        long id = voucherOrderService.placeAnSeckillOrder(voucherId);
        log.info("用户抢购到一张秒杀券{}",id);
        return Result.ok(id);
    }
}
