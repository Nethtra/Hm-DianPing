package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    /**
     * 1.1发送验证码并保存
     *
     * @param phone
     * @param session
     * @return
     */
    void sendAndSaveCode(String phone, HttpSession session);

    /**
     * 1.2短信验证码登陆，未注册的要一并注册
     *
     * @param loginForm
     * @param session
     */
    String login(LoginFormDTO loginForm, HttpSession session);
}
