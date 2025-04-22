package com.hmdp.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 8.1 oss配置类  读取yaml中的配置
 *
 * @author 王天一
 * @version 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "dianping.alioss")
public class AliOssProperties {
    private String endpoint;
    private String bucketName;
}
