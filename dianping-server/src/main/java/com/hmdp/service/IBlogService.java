package com.hmdp.service;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface IBlogService extends IService<Blog> {

    /**
     * 8.2根据Blog id 查看点评
     *
     * @param id
     * @return
     */
    Blog queryBlogById(Long id);

    /**
     * 8.2分页查询热点Blog
     *
     * @param current
     * @return
     */
    List<Blog> queryHotBlog(Integer current);

    /**
     * 8.3给Blog点赞
     *
     * @param id
     */
    void likeBlog(Long id);

    /**
     * 8.4查询前五名点赞Blog的用户
     *
     * @param id
     * @return
     */
    List<UserDTO> top5BlogLikes(Long id);
}
