package com.hmdp.constant;

import io.swagger.annotations.ApiParam;

/**
 * 有关reids业务使用的常量类
 */
public class RedisConstants {
    //缓存空值过期时间
    public static final Long CACHE_NULL_TTL = 2L;
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 5L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;
    //商铺缓存key
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    //商铺缓存过期时间
    public static final Long CACHE_SHOP_TTL = 30L;
    //商铺类型缓存key
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type:";
    //商铺互斥锁key
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    //商铺互斥锁过期时间
    public static final Long LOCK_SHOP_TTL = 10L;
    //6.2秒杀券库存信息key
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    //订单业务前缀
    public static final String ORDER = "order";
    //订单的消息队列key 注意没有冒号
    public static final String ORDER_QUEUE="stream.orders";
    //记录blog点赞过的用户的key前缀
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    //优惠券订单业务前缀
    public static final String VOUCHER_ORDER_PREFIX = "order";
}
