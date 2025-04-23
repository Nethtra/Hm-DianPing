package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constant.SystemConstants;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.hmdp.constant.RedisConstants.BLOG_LIKED_KEY;

@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Blog queryBlogById(Long id) {
        //根据id查询blog
        Blog blog = getById(id);
        //封装用户的相关信息
        //因为返回值要是Blog  不是dto 所以在Blog中添加非数据库字段 然后手动封装上 然后返回
        queryUserInformation(blog);
        //8.3再封装该用户的点赞状态
        Long userId = UserHolder.getUser().getId();
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, userId.toString());
        blog.setIsLike(isLiked);
        return blog;
    }


    @Override
    public List<Blog> queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 封装用户信息
        /*records.forEach(blog -> {
            queryUserInformation(blog);
        });*/
        //替换为这个更简洁
//        records.forEach(this::queryUserInformation);
        //8.3
        records.forEach(blog -> {
            queryUserInformation(blog);
            Long userId = UserHolder.getUser().getId();
            Boolean isLiked = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + blog.getId(), userId.toString());
            blog.setIsLike(isLiked);
        });
        return records;
    }

    @Override
    public void likeBlog(Long id) {
        //拿到用户id
        Long userId = UserHolder.getUser().getId();
        //先去redis-set里查该用户是否点过赞
        Boolean liked = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, userId.toString());
        //未点过
        if (BooleanUtil.isFalse(liked)) {
            //数据库点赞数+1  update tb_blog set liked =liked +1 where id=id
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            //userId加入set
            if (isSuccess)
                stringRedisTemplate.opsForSet().add(BLOG_LIKED_KEY + id, userId.toString());
            log.info("用户给Blog {}点赞", id);
        }
        //点过
        else {
            //数据库点赞数-1
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            //userId移除set
            if (isSuccess)
                stringRedisTemplate.opsForSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            log.info("用户给Blog {}取消点赞", id);
        }
    }

    private void queryUserInformation(Blog blog) {
        //Blog中有UserId 所以可以用这个id来查user信息
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }
}
