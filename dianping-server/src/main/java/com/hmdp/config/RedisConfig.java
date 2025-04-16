package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 5.1reidsson配置类
 *
 * @author 王天一
 * @version 1.0
 */
@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();//新建配置  注意不要导错包
        //配置地址和密码  单节点useSingleServer  多节点useClusterServers()
        config.useSingleServer().setAddress("redis://192.168.16.181:6379").setPassword("qwer");
        return Redisson.create(config);
    }
}
