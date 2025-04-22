package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyuncs.exceptions.ClientException;
import com.hmdp.dto.Result;
import com.hmdp.constant.SystemConstants;
import com.hmdp.utils.AliOssUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/upload")
@Api("上传文件的接口")
public class UploadController {
    @Autowired
    private AliOssUtils aliOssUtils;

    /*@PostMapping("/blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }*/

    /**
     * 8.1上传文件  从苍穹外卖复制过来的
     * @param image
     * @return
     */
    @ApiOperation("上传文件")
    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            String url = aliOssUtils.upload(image);
            log.info("文件上传成功，到{}", url);
            return Result.ok(url);
        } catch (ClientException | IOException e) {
            log.error("文件上传失败！");
        }
        return Result.fail("文件上传失败！");
    }

    /**
     * 8.1删除图片  不用检查直接返回ok就行
     * @param filename
     * @return
     */
    @ApiOperation("删除上传的图片")
    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
//        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
//        if (file.isDirectory()) {
//            return Result.fail("错误的文件名称");
//        }
//        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
