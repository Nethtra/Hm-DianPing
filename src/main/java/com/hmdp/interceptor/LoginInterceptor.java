package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 1.3拦截器 检查是否登陆
 *
 * @author 王天一
 * @version 1.0
 */
public class LoginInterceptor implements HandlerInterceptor {
    /**
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
        HttpSession session = request.getSession();
        Object userDTO = session.getAttribute("user");//获取session中的用户
        if (userDTO == null) {//不存在
            response.setStatus(401);
            return false;//拦截
        }
        UserHolder.saveUser((UserDTO) userDTO);//共享信息保存到ThreadLocal中
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
