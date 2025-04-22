package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.db.sql.Order;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BaseException;
import com.hmdp.exception.OrderBusinessException;
import com.hmdp.exception.TimeStateErrorException;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.constant.RedisConstants.*;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    //6.2判断秒杀资格的lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    //6.2静态代码块加载脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("7.1seckill.lua"));//设置lua脚本地址
        SECKILL_SCRIPT.setResultType(Long.class);//脚本返回值类型
    }

    //6,1阻塞队列 存放要下单到数据库的VoucherOrder
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);//初始化的容量大小
    //6,2线程池
    public static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();//开启一个单线程的线程池

    //注意这个init方法是springboot调用，所以也是springboot创建的子线程，每次请求对应的线程会和它并发执行
    @PostConstruct//Spring提供的注解 类初始化完就运行
    private void init() {//6,4初始化完就提交线程任务  让一初始化就一直执行异步下单
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //6,3线程任务   执行异步下单
    //从阻塞队列里取出VoucherOrder然后下单
    /*private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //从阻塞队列里拿出订单
                    VoucherOrder voucherOrder = orderTasks.take();//take 直到阻塞队列里有元素才拿出来
                    //调用另一个方法异步下单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }*/

    //7.1 stream执行异步下单任务
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //从消息队列里拿出消息 xreadgroup g1 c1 count 1 block 2000 streams stream.orders >
                    //拿出来是个list
                    List<MapRecord<String, Object, Object>> messages =
                            stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"),//消费组和消费者
                                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)), //读选项
                                    StreamOffset.create(ORDER_QUEUE, ReadOffset.lastConsumed()));//stream选项
                    //判断是否有消息
                    if (messages == null || messages.isEmpty())
                        //没有就下一次循环
                        continue;
                    //有消息 String就是消息id  两个obj是 消息里存的 kv键值对
                    //注意这个类型
                    MapRecord<String, Object, Object> msg = messages.get(0);
                    RecordId msgId = msg.getId();//消息id
                    Map<Object, Object> msgValue = msg.getValue();//消息内容
                    //转成voucherOrder
                    //注意这里因为VoucherOrder构造器私有 所以new的话看不到 可以改成public也可以用链式构造一个对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(msgValue, VoucherOrder.builder().build(), true);
                    //调用另一个方法异步下单
                    handleVoucherOrder(voucherOrder);
                    //ack确认
                    stringRedisTemplate.opsForStream().acknowledge(ORDER_QUEUE, "g1", msgId);
                } catch (Exception e) {
                    //出异常了去pending-list
                    log.error("消息队列处理异常{}", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    //从pending-list里拿出消息 xreadgroup g1 c1 count 1 block 2000 streams stream.orders 0
                    List<MapRecord<String, Object, Object>> messages =
                            stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"),//消费组和消费者
                                    StreamReadOptions.empty().count(1), //读选项
                                    StreamOffset.create(ORDER_QUEUE, ReadOffset.from("0")));//stream选项
                    //判断是否有消息
                    if (messages == null || messages.isEmpty())
                        //没有说明处理完了 就跳出
                        break;
                    //有消息
                    MapRecord<String, Object, Object> msg = messages.get(0);
                    RecordId msgId = msg.getId();
                    Map<Object, Object> msgValue = msg.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(msgValue, VoucherOrder.builder().build(), true);
                    //调用另一个方法异步下单
                    handleVoucherOrder(voucherOrder);
                    //ack确认
                    stringRedisTemplate.opsForStream().acknowledge(ORDER_QUEUE, "g1", msgId);
                } catch (Exception e) {
                    //再次出异常不用调自己，直接让他进行 就是继续循环
                    log.error("消息队列处理异常{}", e);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

    //7,1代理对象   在主线程里初始化
    private IVoucherOrderService proxy;
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
            throw new OrderBusinessException("来晚了，优惠券卖完了！");
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
            throw new OrderBusinessException("来晚了，优惠券卖完了！");
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
            throw new OrderBusinessException("来晚了，优惠券卖完了！");
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
//    @Override
//    public long placeASeckillOrder(Long voucherId) {
//        //1根据id查询秒杀券
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //2判断是否未到开始时间
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            throw new TimeStateErrorException("未到开始抢购时间！");
//        }
//        //3判断是否已到结束时间
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            throw new TimeStateErrorException("抢购时间已结束！");
//        }
//
//        //4判断是否还有库存
//        if (seckillVoucher.getStock() < 1) {
//            throw new OrderBusinessException("来晚了，优惠券卖完了！");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //5使用自定义的redis分布式锁  解决分布式并发不能一人一单
//        //注意这里 因为我们想要锁住每个user 所以粒度是userId   锁的key要加上userId
//        RedisLockUtils redisLockUtils = new RedisLockUtils(stringRedisTemplate, VOUCHER_ORDER_PREFIX + userId);
//        boolean lock = redisLockUtils.tryLock(2000);//尝试获取锁
//
//        /*synchronized (userId.toString().intern()) {//锁的范围应该是整个方法
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//拿到当前对象的代理对象
//            return proxy.addSeckillOrder(voucherId);//然后用代理对象而不是目标对象调事务的方法
//        }*/
//        if (!lock) {
//            //5,1如果没有拿到锁  说明已经有一个线程去下单了
//            throw new OrderBusinessException("一人只能下一单！");//注意这是分布式环境一人一单
//        }
//        //5,2如果拿到锁了 就去下单
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//拿到当前对象的代理对象
//            return proxy.addSeckillOrder(voucherId);//然后用代理对象而不是目标对象调事务的方法
//        } finally {//注意finally释放锁
//            redisLockUtils.unLock();
//        }
//    }
//
//    @Transactional
//    public long addSeckillOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        //6判断一人一单   3.5
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if (count > 0) {
//            throw new OrderBusinessException("此商品只能每用户下一单！");//这个是单体环境一人一单
//        }
//        //7下单库存-1  3.4 .gt("stock", 0)防止超卖
//        boolean success = seckillVoucherService.update()
//                .setSql("stock=stock-1")//set
//                .eq("voucher_id", voucherId).gt("stock", 0)//where
//                .update();
//        if (!success) {
//            throw new BaseException("未知错误！");
//        }
//        //8生成订单填写订单信息
//        long seckillVoucherOrderId = redisIdWorker.nextId(VOUCHER_ORDER_PREFIX);
//        VoucherOrder order = VoucherOrder.builder()
//                .id(seckillVoucherOrderId)
//                .userId(userId)
//                .voucherId(voucherId)
//                .build();
//        save(order);
//        //9返回订单id
//        return seckillVoucherOrderId;
//
//    }

    /**
     * 5.1redisson分布式锁入门 解决一人一单
     *
     * @param voucherId
     * @return
     */
    //回顾业务流程：两个步骤 扣减库存 新增订单
    //扣减库存要判断时间 保证一人一单（其中出现线程安全问题） 防止超卖
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
            throw new OrderBusinessException("来晚了，优惠券卖完了！");
        }
        Long userId = UserHolder.getUser().getId();
        //5.1使用redisson提供的分布式锁
        //注意这里 因为我们想要锁住每个user 所以粒度是userId   锁的key要加上userId
        RLock lock = redissonClient.getLock("lock:" + VOUCHER_ORDER_PREFIX + userId);//参数是锁的名称
        boolean isLock = lock.tryLock();//不给参数默认不重试 30s自动释放
//        RedisLockUtils redisLockUtils = new RedisLockUtils(stringRedisTemplate, VOUCHER_ORDER_PREFIX + userId);
//        boolean lock = redisLockUtils.tryLock(2000);//尝试获取锁
        if (!isLock) {
            //5,1如果没有拿到锁  说明已经有一个线程去下单了
            throw new OrderBusinessException("一人只能下一单！");//注意这是分布式环境一人一单
        }
        //5,2如果拿到锁了 就去下单
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//拿到当前对象的代理对象
            return proxy.addSeckillOrder(voucherId);//然后用代理对象而不是目标对象调事务的方法
        } finally {//注意finally释放锁
            lock.unlock();//5.1
        }
    }

    @Transactional
    public long addSeckillOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //6判断一人一单   3.5
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            throw new OrderBusinessException("此商品只能每用户下一单！");
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
    }*/


    /**
     * 6.2 异步秒杀 基于redis完成秒杀资格（库存 一人一单）的判断 基于阻塞队列完成异步秒杀下单
     * 1234就是目前所有的业务  但还没有在数据库层面进行修改
     *
     * @param voucherId
     * @return
     */
    /*@Override
    public long placeASeckillOrder(Long voucherId) {
        //1调用自己写的lua脚本  判断资格
        Long userId = UserHolder.getUser().getId();
        //ARGV只能传String   脚本返回long  用result接收
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        int r = result.intValue();
        //2result不为0 失败的情况
        if (r == 2) {
            throw new OrderBusinessException("你已经下过单了！");
        } else if (r == 1) {
            throw new OrderBusinessException("来晚了，优惠券已经被抢光了！");
        }
        //3result=0 代表下单成功
        //生成订单id
        long id = redisIdWorker.nextId(ORDER);

        //5生成订单信息并放到阻塞队列  这就是全部的主线程下单逻辑
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(id)
                .userId(userId)
                .voucherId(voucherId)
                .build();
        orderTasks.add(voucherOrder);//添加到阻塞队列
        //7,1在主线程里提前获取proxy
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //4返回订单id
        return id;
    }*/

    /**
     * 7.1基于redis完成秒杀资格的判断并且将订单信息存入消息队列
     * 基于redis的stream消息队列完成异步秒杀下单
     *
     * @param voucherId
     * @return
     */
    @Override
    public long placeASeckillOrder(Long voucherId) {
        //1调用自己写的lua脚本  判断资格
        Long userId = UserHolder.getUser().getId();
        //2生成订单id
        long id = redisIdWorker.nextId(ORDER);
        //3调用lua脚本 ARGV只能传String   脚本返回long  用result接收
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(id));//比之前多传一个订单id
        int r = result.intValue();
        //3result不为0 失败的情况
        if (r == 2) {
            throw new OrderBusinessException("你已经下过单了！");
        } else if (r == 1) {
            throw new OrderBusinessException("来晚了，优惠券已经被抢光了！");
        }
        //4result=0 代表下单成功
        //在主线程里提前获取proxy
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //5返回订单id
        return id;
    }


    /**
     * 6.2线程任务调用的 异步秒杀下单方法
     * 其实不再需要锁，因为前面基于redis判断秒杀资格已经保证了线程安全，这里只是保险
     *
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //7这个方法就抄原来的placeASeckillOrder剩下的逻辑
        //注意因为这已经不是主线程，所以UserHolder拿不到userId
        //Long userId = UserHolder.getUser().getId();
        Long userId = voucherOrder.getUserId();//直接从voucherOrder里取
        //使用redisson提供的分布式锁
        RLock lock = redissonClient.getLock("lock:" + VOUCHER_ORDER_PREFIX + userId);//参数是锁的名称
        boolean isLock = lock.tryLock();//不给参数默认不重试 30s自动释放
        if (!isLock) {
            //如果没有拿到锁  说明已经有一个线程去下单了
            throw new OrderBusinessException("一人只能下一单！");//注意这是分布式环境一人一单
        }
        //如果拿到锁了 就去下单
        try {//7,1这个proxy也是基于ThreadLocal 所以也废了拿不到
            //考虑在主线程提前获取，并且定义成成员变量
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//拿到当前对象的代理对象
            proxy.addSeckillOrder(voucherOrder);//然后用代理对象而不是目标对象调事务的方法
            //参数改造成VoucherOrder
        } finally {//注意finally释放锁
            lock.unlock();
        }
    }

    @Transactional
    public void addSeckillOrder(VoucherOrder voucherOrder) {
        //7,2这里就抄原来的addSeckillOrder的逻辑  删一下多余的
//        Long userId = UserHolder.getUser().getId();
        Long userId = voucherOrder.getUserId();
        //判断一人一单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            throw new OrderBusinessException("此商品只能每用户下一单！");
        }
        //库存-1  .gt("stock", 0)防止超卖
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")//set
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)//where
                .update();
        if (!success) {
            throw new BaseException("未知错误！");
        }
        //7,3真正下单
        save(voucherOrder);
    }

    @Override
    public long addSeckillOrder(Long voucherId) {
        return 0;
    }
}
