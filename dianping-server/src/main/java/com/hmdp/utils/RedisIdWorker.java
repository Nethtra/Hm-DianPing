package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 3.1生成全局唯一ID工具类
 *
 * @author 王天一
 * @version 1.0
 */
@Component
public class RedisIdWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 2025/1/1 0时 0分 的秒时间戳
     */
    public static final long BEGIN_TIMESTAMP = 1735689600L;

    /**
     * 生成全局唯一ID
     *
     * @param keyPrefix 业务前缀
     * @return
     */
    public long nextId(String keyPrefix) {
        //分两部分
        //1时间戳部分   用现在的时间戳减去一个定义好的时间戳   得到timestamp
        LocalDateTime now = LocalDateTime.now();
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = epochSecond - BEGIN_TIMESTAMP;
        //2自增序号部分  由redis生成      得到count
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));//每天的日期
        //每天都是一个新的key  保证自增部分不会超过位数且便于统计每天的业务量
        long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + date);
        //3最后把两部分拼接到一起 先把timestamp左移然后或运算
        return timestamp << 32 | count;
    }

}
