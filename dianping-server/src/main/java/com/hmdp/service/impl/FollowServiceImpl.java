package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public void followAUser(Long id, Boolean isFollow) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            //true 关注 insert一条数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            save(follow);
        } else {
            //false 取关 delete一条数据
            //delete from tb_follow where user_id= and follow_user_id=id
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
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
}
