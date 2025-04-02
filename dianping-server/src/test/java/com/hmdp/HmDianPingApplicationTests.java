package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;//注意注入的是Impl
    @Autowired
    private RedisIdWorker redisIdWorker;
    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    public void testsping() {
        System.out.println("helolo");
    }

    /**
     * 提前缓存热点数据
     *
     * @throws InterruptedException
     */
    @Test
    public void testSaveShop2Redis() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    /**
     * 测试查询店铺缓存
     */
    @Test
    public void testSelectByShopId() {
        Shop shop = shopService.selectById4(1L);
        System.out.println(shop);
    }

    /**
     * 3.1测试工具类生成全局唯一id
     *
     * @throws InterruptedException
     */
    @Test
    public void testGenerateID() throws InterruptedException {
        //设置计数器
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {//每个线程生成100次id
                long id = redisIdWorker.nextId("order");
                System.out.println(id);
            }
            //每次执行完任务计数器-1
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {//300个线程
            executorService.submit(task);
        }
        //等待计数器减为0即所有线程都执行完成
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println(end - begin);
    }//打开redis可以看到今天生成的总数
}
