package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用于将实体类封装上逻辑过期时间缓存到redis中
 *
 * @author 王天一
 * @version 1.0
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
