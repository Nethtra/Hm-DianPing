package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.exception.DataNotFoundException;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.constant.RedisConstants.USER_FOLLOWS_KEY;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public void followAUser(Long id, Boolean isFollow) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            //true 关注 insert一条数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean isSuccess = save(follow);
            //9.3添加到redis set中
            if (isSuccess)
                stringRedisTemplate.opsForSet().add(USER_FOLLOWS_KEY + userId, id.toString());
        } else {
            //false 取关 delete一条数据
            //delete from tb_follow where user_id= and follow_user_id=id
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            //9.3从reids set中移除
            if (isSuccess)
                stringRedisTemplate.opsForSet().remove(USER_FOLLOWS_KEY + userId, id.toString());
        }
    }

    @Override
    public boolean isFollowed(Long id) {
        Long userId = UserHolder.getUser().getId();
        //查数量 有就是关注了
        //select count(*) from tb_follow where user_id =userId and follow_user_id=id;
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return count > 0;
    }

    @Override
    public List<UserDTO> commonFollow(Long id) {
        //思路 求当前登录用户和目标用户这两个set的交集
        Long currentUserId = UserHolder.getUser().getId();//当前登录用户id
        //求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(USER_FOLLOWS_KEY + currentUserId, USER_FOLLOWS_KEY + id);
        if (intersect==null||intersect.isEmpty())
            throw new DataNotFoundException("未有共同关注用户！");
        //注意交集出的结果是String类型的set 用户的ids  转成Long
        List<Long> commomUserIdList = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //再查出userDTO
        List<User> userList = userService.listByIds(commomUserIdList);

        List<UserDTO> userDTOList = userList.stream().map(user -> {
            return BeanUtil.copyProperties(user, UserDTO.class);
        }).collect(Collectors.toList());
        return userDTOList;
    }
}
