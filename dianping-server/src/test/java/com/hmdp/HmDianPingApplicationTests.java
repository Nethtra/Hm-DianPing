package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.constant.RedisConstants.LOGIN_USER_TTL;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;//注意注入的是Impl
    @Autowired
    private RedisIdWorker redisIdWorker;
    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    @Autowired
    private UserServiceImpl userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    @Test
    public void testUUID() {//toString(true) 使生成的uuid不带-
        System.out.println(UUID.randomUUID().toString(true));
    }

    /**
     * 6.1给所有用户生成token并保存到文件  用于测试并发
     */
    @Test
    public void generateUserTokens() {
        List<User> userList = userService.list();//select * from tb_user
        int userCount = userService.count();//select count(*) from tb_user

        for (int i = 0; i < userCount; i++) {
            User user = userList.get(i);
            String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)//忽略为空的字段
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));//字段值编辑器 将字段值改成string
            stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userDTOMap);
            stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
            //Java 7+推荐使用try-with-resources自动关闭资源
            //使用BufferedWriter 有缓冲区和跨平台换行 性能比FileWriter更好   默认相对路径起始点为工作目录 true追加模式
            try (FileWriter fileWriter = new FileWriter("output.txt", true);
                 BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)
            ) {
                bufferedWriter.write(token);//写一行
                bufferedWriter.newLine();//跨平台换行
                if (i % 100 == 0)
                    bufferedWriter.flush();//写100次就刷新缓冲区
            } catch (IOException e) {
                System.out.println("写入文件时出错");
                e.printStackTrace();
            }
        }
    }

    /**
     * 6.1查看当前项目的工作目录
     */
    @Test
    public void testWorkDirectory() {
        String property = System.getProperty("user.dir");
        System.out.println(property);
    }

    /**
     * 快速生成一个token
     */
    @Test
    public void generateAToken() {
        User user = userService.getById(1010);
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)//忽略为空的字段
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));//字段值编辑器 将字段值改成string
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userDTOMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        System.out.println(token);
    }
}
