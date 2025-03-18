package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.constant.RedisConstants.LOGIN_USER_TTL;

/**
 * 1.4刷新token拦截器
 *
 * @author 王天一
 * @version 1.0
 */
@Slf4j
//@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 1.4只刷新token和保存到ThreadLocal，不做校验
     * 请求到达controller之前
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1检查token是否存在
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            log.warn("用户未登录！");
//            response.setStatus(401);
//            return false;   直接放行
            return true;
        }
        //2存在就拿出redis中的信息  用opsForHash().get只能get一个field 用entries可以整个v拿出来
        Map<Object, Object> userDTOMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        if (userDTOMap.isEmpty()) {
//            response.setStatus(401);
//            return false;
            return true;
        }
        //拿出来是Map 再转回UserDTO 还是用hutool的BeanUtil Map       填充的目标        是否忽略错误
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userDTOMap, new UserDTO(), false);
        //3共享信息保存到ThreadLocal中
        UserHolder.saveUser(userDTO);

        //4刷新token有效期   为什么要刷新：因为要模拟session的行为 断开连接30分钟后才过期，只要建立连接就刷新成30分钟
        //而且注意要在拦截器里做这个逻辑
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;//放行
    }

    /**
     * 视图渲染之后
     *
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();//移除用户  防止内存泄露
    }
}
