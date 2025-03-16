package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * 注册web层相关组件
 *
 * @author 王天一WebMvcConfigurer  WebMvcConfigurationSupport
 * @version 1.0
 */
/*@Slf4j
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    */

/**
 * 注册拦截器
 *
 * @param interceptorRegistry
 *//*
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

} 为什么注释见问题列表*/

@Slf4j
@Configuration
public class MvcConfig extends WebMvcConfigurationSupport {

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
