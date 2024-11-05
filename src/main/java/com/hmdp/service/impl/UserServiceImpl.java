package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.exception.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public void sendAndSaveCode(String phone, HttpSession session) {
        //1校验手机号
        //注意这个工具类倒着来的  是否是无效 true就是无效
        if (RegexUtils.isPhoneInvalid(phone))
            //2手机号不合法就返回错误信息
            throw new InvalidFormatException("手机号格式不合法");
        //3校验通过就生成验证码
        //使用hutool工具类   生成6位验证码
        String code = RandomUtil.randomNumbers(6);
        //4保存验证码到session
        session.setAttribute("code", code);
        //5发送验证码
        //模拟一下
        log.debug("发送验证码{}", code);

    }
}
