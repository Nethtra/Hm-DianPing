package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BaseException;
import com.hmdp.exception.OrderBusinessException;
import com.hmdp.exception.OutOfStockException;
import com.hmdp.exception.TimeStateErrorException;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedisLockUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 3.3下单秒杀券  3.4防止超卖
     *
     * @param voucherId
     * @return
     */
    /*@Override
    public long placeAnSeckillOrder(Long voucherId) {
        //1根据id查询秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2判断是否未到开始时间
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            throw new TimeStateErrorException("未到开始抢购时间！");
        }
        //3判断是否已到结束时间
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            throw new TimeStateErrorException("抢购时间已结束！");
        }

        //4判断是否还有库存
        if (seckillVoucher.getStock() < 1) {
            throw new OutOfStockException("来晚了，优惠券卖完了！");
        }
        //5下单库存-1  3.4 .gt("stock", 0)防止超卖
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")//set
                .eq("voucher_id", voucherId).gt("stock", 0)//where
                .update();
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
    }*/


    /**
     * 3.5解决一人一单超卖初版
     *
     * @param voucherId
     * @return
     */
    /*@Override
    public long placeAnSeckillOrder(Long voucherId) {
        //1根据id查询秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2判断是否未到开始时间
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            throw new TimeStateErrorException("未到开始抢购时间！");
        }
        //3判断是否已到结束时间
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            throw new TimeStateErrorException("抢购时间已结束！");
        }

        //4判断是否还有库存
        if (seckillVoucher.getStock() < 1) {
            throw new OutOfStockException("来晚了，优惠券卖完了！");
        }
        return extracted(voucherId);
    }

    @Transactional
    public long extracted(Long voucherId) {

        Long userId = UserHolder.getUser().getId();
        //synchronized加在方法上表示锁在this对象上，但是我们想要将锁加在每个用户上
        //而且加在方法上会让所有用户进来都串行执行 性能不好  需要让锁的粒度减小
        //因为userId是Long 即使是同一用户进来,每次UserHolder返回的都是不同的新对象，所以加在userId上不能锁住
        //将它toString  但是ctrl+B看toString的源码  toString每次也是返回新的String对象 所以还是不能锁住
        //.intern会从常量池中找字符串 有就返回引用  多次返回保证了是同一个串 符合业务
        synchronized (userId.toString().intern()) {
            //5判断一人一单   3.5
            //但是多线程并发还是会产生线程安全问题，不能保证一人一单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                throw new OrderBusinessException("此商品只能每用户下一单！");
            }
            //6下单库存-1  3.4 .gt("stock", 0)防止超卖
            boolean success = seckillVoucherService.update()
                    .setSql("stock=stock-1")//set
                    .eq("voucher_id", voucherId).gt("stock", 0)//where
                    .update();
            if (!success) {
                throw new BaseException("未知错误！");
            }
            //7生成订单填写订单信息
            //只需要填前三个字段
            long seckillVoucherOrderId = redisIdWorker.nextId(VOUCHER_ORDER_PREFIX);
            VoucherOrder order = VoucherOrder.builder()
                    .id(seckillVoucherOrderId)
                    .userId(userId)
                    .voucherId(voucherId)
                    .build();
            save(order);
            //8返回订单id
            return seckillVoucherOrderId;
        }
        //释放锁然后提交事务 但是如果释放锁还没提交事务时就线程切换 那又有userId.toString().intern()可以拿到锁
        //因为事务还没提交 这时候查询也是没有订单  就又会发生线程安全问题
        //所以锁的范围太小 要保证获取锁 提交事务 然后才释放锁
    }*/

    /**
     * 3.5解决一人一单超卖终版
     * 考虑锁的粒度 锁的范围 事务失效
     * 但是分布式环境仍存在线程安全问题
     *
     * @param voucherId
     * @return
     */
    /*@Override
    public long placeASeckillOrder(Long voucherId) {
        //1根据id查询秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2判断是否未到开始时间
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            throw new TimeStateErrorException("未到开始抢购时间！");
        }
        //3判断是否已到结束时间
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            throw new TimeStateErrorException("抢购时间已结束！");
        }

        //4判断是否还有库存
        if (seckillVoucher.getStock() < 1) {
            throw new OutOfStockException("来晚了，优惠券卖完了！");
        }
        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {//锁的范围应该是整个方法
//            return extracted(voucherId);//还有一个问题 事务失效问题
//            // return extracted(voucherId)=return this.extracted(voucherId)
//            //Spring 的事务管理是通过 AOP 代理实现的
//            //当调用来自类外部时，会经过代理，事务注解生效
//            //但类内部方法互相调用时，是直接调用，不经过代理
//            //这一个this.不是代理对象 是目标对象 spring事务注解不会生效
//        }
        synchronized (userId.toString().intern()) {//锁的范围应该是整个方法
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//拿到当前对象的代理对象
            return proxy.addSeckillOrder(voucherId);//然后用代理对象而不是目标对象调事务的方法
        }
    }

    @Transactional
    public long addSeckillOrder(Long voucherId) {
        //synchronized加在方法上表示锁在this对象上，但是我们想要将锁加在每个用户上
        //而且加在方法上会让所有用户进来都串行执行 性能不好  需要让锁的粒度减小
        //因为userId是Long 即使是同一用户进来,每次UserHolder返回的都是不同的新对象，所以加在userId上不能锁住
        //将它toString  但是ctrl+B看toString的源码  toString每次也是返回新的String对象 所以还是不能锁住
        //.intern会从常量池中找字符串 有就返回引用  多次返回保证了是同一个串 符合业务
        Long userId = UserHolder.getUser().getId();
        //5判断一人一单   3.5
        //但是多线程并发还是会产生线程安全问题，不能保证一人一单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            throw new OrderBusinessException("此商品只能每用户下一单！");
        }
        //6下单库存-1  3.4 .gt("stock", 0)防止超卖
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")//set
                .eq("voucher_id", voucherId).gt("stock", 0)//where
                .update();
        if (!success) {
            throw new BaseException("未知错误！");
        }
        //7生成订单填写订单信息
        //只需要填前三个字段
        long seckillVoucherOrderId = redisIdWorker.nextId(VOUCHER_ORDER_PREFIX);
        VoucherOrder order = VoucherOrder.builder()
                .id(seckillVoucherOrderId)
                .userId(userId)
                .voucherId(voucherId)
                .build();
        save(order);
        //8返回订单id
        return seckillVoucherOrderId;
        //释放锁然后提交事务 但是如果释放锁还没提交事务时就线程切换 那又有userId.toString().intern()可以拿到锁
        //因为事务还没提交 这时候查询也是没有订单  就又会发生线程安全问题
        //所以锁的范围太小 要保证获取锁 提交事务 然后才释放锁
    }*/

    /**
     * 4.1解决分布式环境下一人一单超卖问题
     *
     * @param voucherId
     * @return
     */
    @Override
    public long placeASeckillOrder(Long voucherId) {
        //1根据id查询秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2判断是否未到开始时间
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            throw new TimeStateErrorException("未到开始抢购时间！");
        }
        //3判断是否已到结束时间
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            throw new TimeStateErrorException("抢购时间已结束！");
        }

        //4判断是否还有库存
        if (seckillVoucher.getStock() < 1) {
            throw new OutOfStockException("来晚了，优惠券卖完了！");
        }
        Long userId = UserHolder.getUser().getId();
        //5使用自定义的redis分布式锁  解决分布式并发不能一人一单
        //注意这里 因为我们想要锁住每个user 所以粒度是userId   锁的key要加上userId
        RedisLockUtils redisLockUtils = new RedisLockUtils(stringRedisTemplate, VOUCHER_ORDER_PREFIX + userId);
        boolean lock = redisLockUtils.tryLock(2000);//尝试获取锁

        /*synchronized (userId.toString().intern()) {//锁的范围应该是整个方法
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//拿到当前对象的代理对象
            return proxy.addSeckillOrder(voucherId);//然后用代理对象而不是目标对象调事务的方法
        }*/
        if (!lock) {
            //5,1如果没有拿到锁  说明已经有一个线程去下单了
            throw new OrderBusinessException("一人只能下一单！");//注意这是分布式环境一人一单
        }
        //5,2如果拿到锁了 就去下单
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//拿到当前对象的代理对象
            return proxy.addSeckillOrder(voucherId);//然后用代理对象而不是目标对象调事务的方法
        } finally {//注意finally释放锁
            redisLockUtils.unLock();
        }
    }

    @Transactional
    public long addSeckillOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //6判断一人一单   3.5
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            throw new OrderBusinessException("此商品只能每用户下一单！");//这个是单体环境一人一单
        }
        //7下单库存-1  3.4 .gt("stock", 0)防止超卖
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")//set
                .eq("voucher_id", voucherId).gt("stock", 0)//where
                .update();
        if (!success) {
            throw new BaseException("未知错误！");
        }
        //8生成订单填写订单信息
        long seckillVoucherOrderId = redisIdWorker.nextId(VOUCHER_ORDER_PREFIX);
        VoucherOrder order = VoucherOrder.builder()
                .id(seckillVoucherOrderId)
                .userId(userId)
                .voucherId(voucherId)
                .build();
        save(order);
        //9返回订单id
        return seckillVoucherOrderId;

    }
}
