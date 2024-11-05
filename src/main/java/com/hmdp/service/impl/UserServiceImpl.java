package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.exception.VerificationFailedException;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.exception.InvalidFormatException;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
        //4保存验证码和手机号到session
        session.setAttribute("code", code);
        session.setAttribute("phone", phone);
        //5发送验证码
        //模拟一下
        log.debug("发送验证码{}", code);

    }

    @Override
    public void login(LoginFormDTO loginForm, HttpSession session) {
        //1校验前端现在提交的手机号和验证码是否与发送验证码时session中存的一致
        Object cacheCode = session.getAttribute("code");
        Object cachePhone = session.getAttribute("phone");
        String code = loginForm.getCode();
        String phone = loginForm.getPhone();
        if (cacheCode == null || cachePhone == null || !cachePhone.toString().equals(phone) || !cacheCode.toString().equals(code))
            //2不一致抛出异常
            throw new VerificationFailedException("手机号或验证码错误!");
        //3一致就先查询用户是否存在  使用mybatis-plus
        //相当于select * from tb_user where phone=phone;
        //.one表示查一个 .list表示多个  因为phone unique  所以肯定是一个结果
        //因为继承了extends ServiceImpl<UserMapper, User>  所以知道相当于到UserMapper中  封装到实体类User
        //User中制定了表@TableName("tb_user")  所以知道是哪张表
        User user = query().eq("phone", phone).one();
        //4不存在创建新用户并保存到tb_user
        if (user == null) {
            //随机一个用户名
            user = User.builder()
                    .phone(phone)
                    .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                    .build();
            //保存到tb_user  mybatis-plus
            save(user);
        }
        //存在就直接登陆
        //5登陆成功就保存到session中
        //不用保存user  保存一个userDTO
        //这个BeanUtil是hutool包里的
        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
        log.info("登陆成功");
    }
}
