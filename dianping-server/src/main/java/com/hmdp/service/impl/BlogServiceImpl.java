package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constant.SystemConstants;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;

    @Override
    public Blog queryBlogById(Long id) {
        //根据id查询blog
        Blog blog = getById(id);
        //封装用户的相关信息
        //因为返回值要是Blog  不是dto 所以在Blog中添加非数据库字段 然后手动封装上 然后返回
        queryUserInformation(blog);
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
        records.forEach(this::queryUserInformation);
        return records;
    }

    private void queryUserInformation(Blog blog) {
        //Blog中有UserId 所以可以用这个id来查user信息
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }
}
