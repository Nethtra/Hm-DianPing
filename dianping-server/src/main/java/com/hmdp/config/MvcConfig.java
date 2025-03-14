package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author 王天一
 * @version 1.0
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    /**
     * 注册拦截器
     *
     * @param interceptorRegistry
     */
    public void addInterceptors(InterceptorRegistry interceptorRegistry) {
        interceptorRegistry.addInterceptor(new LoginInterceptor())
                //把不需要登陆校验的请求排除
                .excludePathPatterns(
                        "/user/login",
                        "/user/code",
                        "/shop/**",
                        "/upload/**",
                        "/voucher/**"
                );
    }
}
