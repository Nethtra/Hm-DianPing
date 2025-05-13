package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;


@Slf4j
@RestController
@RequestMapping("/user")
@Api(tags = "用户相关接口")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 1.1发送短信验证码并保存
     */
    @ApiOperation("发送验证码")
    @PostMapping("/code")
    public Result sendCode(String phone, HttpSession session) {
        log.debug("手机号{}请求验证码", phone);
        userService.sendAndSaveCode(phone, session);
        return Result.ok();
    }

    /**
     * 1.2使用短信验证码登录/注册
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @ApiOperation("使用短信验证码登录/注册")
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        //DTO
        log.debug("用户手机号{}尝试登陆", loginForm.getPhone());
        String token = userService.login(loginForm, session);
        return Result.ok(token);
    }

    /**
     * 1.3这个应该是右下角 我的 页面
     * 要返回当前登陆用户的信息
     *
     * @return
     */
    @GetMapping("/me")
    public Result me() {
        //从ThreadLocal获取当前登录的用户并返回
        UserDTO userDTO = UserHolder.getUser();//拿出来UserDTO
        return Result.ok(userDTO);
    }

    /**
     * 登出功能
     *
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout() {
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    /**
     * 9.2个人主页  用户信息
     *
     * @param id 此用户id
     * @return
     */
    @ApiOperation("个人主页 用户信息")
    @GetMapping("/{id}")
    public Result personalPage(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null)
            return Result.ok();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
