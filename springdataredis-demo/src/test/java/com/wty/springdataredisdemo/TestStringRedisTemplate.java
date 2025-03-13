package com.wty.springdataredisdemo;

import com.alibaba.fastjson2.JSON;
import com.wty.springdataredisdemo.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

/**
 * 测试StringRedisTemplate类
 * ctrl+b进去看，四个序列化器都是StringRedisSerializer即序列化为String
 *
 * @author 王天一
 * @version 1.0
 */
@SpringBootTest
public class TestStringRedisTemplate {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     *  当存储的value是对象时，手动进行序列化反序列化
     */
    @Test
    public void testSerializer() {
        User user = new User("lisi", 30);//对象
        String jsonString = JSON.toJSONString(user);//手动序列化
        stringRedisTemplate.opsForValue().set("lisi", jsonString);//存入redis
        String lisiString = stringRedisTemplate.opsForValue().get("lisi");
        User lisi = JSON.parseObject(lisiString, User.class);//手动反序列化
        System.out.println(lisi);
    }

    /**
     * 当存储的value是字符串时，不需要手动
     */
    @Test
    public void testHash(){
        stringRedisTemplate.opsForHash().put("user:20","name","jerry");
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries("user:20");
        System.out.println(entries);
    }
}
