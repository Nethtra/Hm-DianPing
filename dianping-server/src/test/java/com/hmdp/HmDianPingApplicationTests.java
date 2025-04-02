package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;//注意注入的是Impl

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
}
