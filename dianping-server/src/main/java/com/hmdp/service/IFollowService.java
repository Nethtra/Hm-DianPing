package com.hmdp.service;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface IFollowService extends IService<Follow> {

    /**
     * 9.1关注/取关用户
     *
     * @param id
     * @param isFollow
     */
    void followAUser(Long id, Boolean isFollow);

    /**
     * 9.1查询是否关注了该用户
     * @param id
     * @return
     */
    boolean isFollowed(Long id);

    /**
     * 9.3寻找共同关注的用户
     * @param id 目标用户
     * @return
     */
    List<UserDTO> commonFollow(Long id);
}
