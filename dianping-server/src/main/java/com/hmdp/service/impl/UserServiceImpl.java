package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
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
import com.hmdp.constant.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.*;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void sendAndSaveCode(String phone, HttpSession session) {
        //1校验手机号
        //注意这个工具类倒着来的  是否是无效 true就是无效
        if (RegexUtils.isPhoneInvalid(phone))
            //2手机号不合法就返回错误信息
            throw new InvalidFormatException("手机号格式不合法");
        //3合法就生成验证码
        //使用hutool工具类   生成6位随机验证码
        String code = RandomUtil.randomNumbers(6);

/*        //4保存验证码和手机号到session  在登录的时候进行验证
        session.setAttribute("code", code);
        session.setAttribute("phone", phone);*/

        //1.4
        //4保存手机号和验证码到redis  5分钟过期
        //给key加上有关业务逻辑的前缀，防止跟其他业务重复
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);


        //5发送验证码
        //模拟一下
        log.debug("发送验证码{}", code);

    }

    /*@Override
    public void login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone))
            throw new InvalidFormatException("手机号格式不合法");
        //1校验前端现在提交的手机号和验证码是否与发送验证码时session中存的一致
        Object cacheCode = session.getAttribute("code");
        Object cachePhone = session.getAttribute("phone");
        String code = loginForm.getCode();
        if (cacheCode == null || cachePhone == null || !cachePhone.toString().equals(phone) || !cacheCode.toString().equals(code))
            //2不一致抛出异常
            throw new VerificationFailedException("手机号或验证码错误!");
        //3一致就先查询用户是否存在  使用mybatis-plus
        //相当于select * from tb_user where phone=phone;
        //.one表示查一个 .list表示多个  因为phone unique  所以肯定是一个结果
        //因为继承了extends ServiceImpl<UserMapper, User>  所以知道相当于到UserMapper中  封装到实体类User
        //User中指定了表@TableName("tb_user")  所以知道是哪张表
        User user = query().eq("phone", phone).one();
        //4不存在创建新用户并保存到tb_user
        if (user == null) {
            log.info("未查询到用户，注册新账号");
            //使用hutool随机一个用户名
            user = User.builder()
                    .phone(phone)
                    .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                    .build();
            //保存到tb_user  mybatis-plus save
            save(user);//默认会主键回填
        }
        //存在就直接登陆
        //5登陆成功就保存到session中
        //不保存user  保存一个减去敏感信息的userDTO
        //这个BeanUtil是hutool包里的  注意和这个包里的不同处package org.springframework.beans;
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        log.info("用户{}登陆成功", user);
    }*/

    /**
     * 1.4使用redis替代session实现短信登录
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public String login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone))
            throw new InvalidFormatException("手机号格式不合法");
        //1校验前端现在提交的手机号和验证码是否与发送验证码时redis中存的一致
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode()))
            //2不一致抛出异常
            throw new VerificationFailedException("手机号或验证码错误!");
        //3一致就先查询用户是否存在  使用mybatis-plus
        //相当于select * from tb_user where phone=phone;
        //.one表示查一个 .list表示多个  因为phone unique  所以肯定是一个结果
        //因为继承了extends ServiceImpl<UserMapper, User>  所以知道相当于到UserMapper中  封装到实体类User
        //User中指定了表@TableName("tb_user")  所以知道是哪张表
        User user = query().eq("phone", phone).one();
        //4不存在创建新用户并保存到tb_user
        if (user == null) {
            log.info("未查询到用户，注册新账号");
            //使用hutool随机一个用户名
            user = User.builder()
                    .phone(phone)
                    .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                    .build();
            //保存到tb_user  mybatis-plus save
            save(user);//默认会主键回填
        }
        //存在就直接登陆

        //5登陆成功就生成token并保存到redis中
        //5,1生成token（key）  使用hutool包下的工具类UUID  isSimple true表示生成的uuid不带中划线
        String token = UUID.randomUUID().toString(true);
        //5,2userDTO转成hash存储为value   opsForHash().putAll要求一个map集合 所以要将userDTO先转成map再存
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //这里会出现一个问题 userDTOMap往redis里存的时候会报类型转换异常 因为里面的Long id不能序列化成String
//        Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO);//hutool工具类
        //方法1 手动new个map然后自己把UserDTO转成map
        //方法2 BeanUtil.beanToMap可以自定义转换
        //所以转成map时 要自定义把Long改为String
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)//忽略为空的字段
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));//字段值编辑器 将字段值改成string
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userDTOMap);
        //5,3设置有效期  因为不能让token一直有效
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        log.info("用户{}登陆成功", user);
        return token;//一定别忘了返回
    }
}
