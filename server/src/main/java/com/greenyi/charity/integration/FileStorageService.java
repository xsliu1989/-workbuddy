package com.greenyi.charity.integration;

import com.greenyi.charity.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储：把上传的图片写到磁盘，并对外提供 /uploads/** 静态访问。
 * 目录可用 greenyi.upload.dir 配置（默认 ./uploads，相对应用启动目录）。
 */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXT = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp"));

    @Value("${greenyi.upload.dir:./uploads}")
    private String uploadDir;

    /** 上传根目录（绝对路径） */
    public Path root() {
        Path p = Paths.get(uploadDir);
        if (!p.isAbsolute()) p = Paths.get(System.getProperty("user.dir")).resolve(p);
        return p.toAbsolutePath().normalize();
    }

    /**
     * 保存图片，返回以 /uploads/ 开头的相对 URL（不含 context-path）。
     * 前端展示时：后台拼 API='/api'，小程序拼 baseUrl='http://IP:8080/api'。
     */
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请选择要上传的图片");
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(400, "仅支持 jpg / jpeg / png / gif / webp / bmp 格式");
        }
        String dateDir = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = root().resolve(dateDir);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException(500, "图片保存失败：" + e.getMessage());
        }
        return "/uploads/" + dateDir + "/" + fileName;
    }
}
