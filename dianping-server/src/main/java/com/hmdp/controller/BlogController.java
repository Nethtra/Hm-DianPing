package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.constant.SystemConstants;
import com.hmdp.utils.UserHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/blog")
@Api("探店点评相关接口")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    /**
     * 8.1 发布探店点评Blog
     *
     * @param blog
     * @return
     */
    @ApiOperation("发布探店点评")
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 8.2根据Blog id查看Blog
     * 注意返回时不止要Blog 还要用户信息
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result viewBlogById(@PathVariable Long id) {
        Blog blog = blogService.queryBlogById(id);
        return Result.ok(blog);
    }

    /**
     * 8.2分页查询热点Blog
     * current是页码
     *
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        List<Blog> records = blogService.queryHotBlog(current);
        return Result.ok(records);
    }

    /**
     * 8.3博客点赞功能 保证一人一赞
     * 使用redis-set维护每个Blog点赞的用户
     * 给Blog添加一个字段isLike用来给前端判断是否点过赞
     *
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
//        // 修改点赞数量
//        blogService.update()
//                .setSql("liked = liked + 1").eq("id", id).update();
        blogService.likeBlog(id);
        return Result.ok();
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }


}
