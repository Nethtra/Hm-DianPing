package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.exception.DataNotFoundException;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        //8.3再封装该用户的点赞状态   就是去redis的set里查有没有这个用户
//        Long userId = UserHolder.getUser().getId();
//        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, userId.toString());
//        blog.setIsLike(isLiked);
        //8.4封装点赞状态
        Long userId = null;
        //同下面的bug处理方式
        try {
            userId = UserHolder.getUser().getId();
        } catch (NullPointerException e) {
            return blog;
        }
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        blog.setIsLike(score != null);
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
//        records.forEach(blog -> {
//            queryUserInformation(blog);
//            Long userId = UserHolder.getUser().getId();
//            Boolean isLiked = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + blog.getId(), userId.toString());
//            blog.setIsLike(isLiked);
//        });
        //8.4封装用户信息和点赞状态
        records.forEach(blog -> {
            queryUserInformation(blog);
            //bug:如果用户没登录 进到首页 首页会调queryHotBlog这个方法，会走到这一步，
            //但是没登陆UserHolder里get不到Userid，会跳空指针异常 所以要加一个判断
            Long userId = null;
            try {
                userId = UserHolder.getUser().getId();
            } catch (NullPointerException e) {
                return;//如果没登录  跳过查询点赞状态   foreach里return相当于continue
            }
            Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
            blog.setIsLike(score != null);
        });
        return records;
    }

    /**
     * 8.3
     *
     * @param id
     */
    /*@Override
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
    }*/
    @Override
    public void likeBlog(Long id) {
        //拿到用户id
        Long userId = UserHolder.getUser().getId();
        //先去redis-set里查该用户是否点过赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        //未点过
        if (score == null) {
            //数据库点赞数+1  update tb_blog set liked =liked +1 where id=id
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            //userId加入zset
            if (isSuccess)//用时间戳做score
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
            log.info("用户给Blog {}点赞", id);
        }
        //点过
        else {
            //数据库点赞数-1
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            //userId移除zset
            if (isSuccess)
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            log.info("用户给Blog {}取消点赞", id);
        }
    }

    @Override
    public List<UserDTO> top5BlogLikes(Long id) {
        //使用zrange查zset里前五个用户
        Set<String> userIdStringSet = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if (userIdStringSet == null || userIdStringSet.isEmpty())
            return Collections.emptyList();//没有人点赞就返回一个空集合
        //查出来是 String集合 转成Long集合
        List<Long> userIdLongList = userIdStringSet.stream().map(Long::valueOf).collect(Collectors.toList());
        //因为要返回List<UserDTO> 所以还要根据这些id查user select * from tb_user where id in()
//        List<User> top5Users = userService.listByIds(userIdLongList);
        String ids = StrUtil.join(",", userIdLongList);//hutool
        String ids2 = StringUtils.join(userIdLongList, ",");//lang3
        //select * from tb_user where id in (5,1) order by field(id,5,1)
        List<User> top5Users = userService
                .query()
                .in("id", userIdLongList)
                .last("order by field (id ," + ids + ")").list();
        //user转成UserDTO
        List<UserDTO> top5UserDTOs = top5Users.stream().map(user -> {
            return BeanUtil.copyProperties(user, UserDTO.class);
        }).collect(Collectors.toList());
        return top5UserDTOs;
    }

    private void queryUserInformation(Blog blog) {
        //Blog中有UserId 所以可以用这个id来查user信息
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }
}
