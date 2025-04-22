package com.hmdp.service;

import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface IBlogService extends IService<Blog> {

    /**
     * 8.2根据Blog id 查看点评
     * @param id
     * @return
     */
    Blog queryBlogById(Long id);

    /**
     * 8.2分页查询热点Blog
     * @param current
     * @return
     */
    List<Blog> queryHotBlog(Integer current);
}
