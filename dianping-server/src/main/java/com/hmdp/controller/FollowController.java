package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IFollowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/follow")
@Api("关注功能接口")
public class FollowController {
    @Autowired
    private IFollowService followService;

    /**
     * 9.1关注/取关用户
     *
     * @param id       要关注的用户id
     * @param isFollow 行为： true关注  false取关
     * @return
     */
    @ApiOperation("9.1关注/取关用户")
    @PutMapping("/{id}/{isFollow}")
    public Result followAUser(@PathVariable Long id, @PathVariable Boolean isFollow) {
        followService.followAUser(id, isFollow);
        return Result.ok();
    }

    /**
     * 9.1查询是否关注了该用户
     *
     * @param id 该用户的id
     * @return
     */
    @ApiOperation("9.1查询是否关注了该用户")
    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable Long id) {
        boolean followedOrNot = followService.isFollowed(id);
        return Result.ok(followedOrNot);
    }

    /**
     * 9.3 共同关注 当前用户与目标用户的共同关注
     *
     * @param id 目标用户id
     * @return List(UserDTO)
     */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable Long id) {
        List<UserDTO> commonFollows = followService.commonFollow(id);
        return Result.ok(commonFollows);
    }
}
