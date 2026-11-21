package com.greenyi.charity.integration;

import com.greenyi.charity.common.BusinessException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
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
 * 图片存储，双模式：
 * - local（默认，本地开发）：写磁盘目录，对外提供 /uploads/** 相对路径静态访问。
 * - cos（微信云托管）：上传对象存储，返回完整 https URL。
 *
 * URL 兼容策略：local 返回 /uploads/...（前端拼 baseUrl）；
 * cos 返回 https://...（前端 normalizeImageUrls 遇到绝对地址原样保留）。
 */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXT = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp"));

    @Value("${greenyi.upload.mode:local}")
    private String mode;

    @Value("${greenyi.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${greenyi.cos.bucket:}")
    private String bucket;

    @Value("${greenyi.cos.region:ap-shanghai}")
    private String region;

    @Value("${greenyi.cos.secret-id:}")
    private String secretId;

    @Value("${greenyi.cos.secret-key:}")
    private String secretKey;

    @Value("${greenyi.cos.public-base-url:}")
    private String publicBaseUrl;

    private volatile COSClient cosClient;

    /** 上传根目录（仅 local 模式使用，静态资源映射也依赖它） */
    public Path root() {
        Path p = Paths.get(uploadDir);
        if (!p.isAbsolute()) p = Paths.get(System.getProperty("user.dir")).resolve(p);
        return p.toAbsolutePath().normalize();
    }

    /** 保存图片，返回可被前端直接展示的 URL */
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
        String key = dateDir + "/" + fileName;
        if ("cos".equalsIgnoreCase(mode)) return saveToCos(file, key, ext);
        return saveToLocal(file, key);
    }

    private String saveToLocal(MultipartFile file, String key) {
        try {
            Path target = root().resolve(key);
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new BusinessException(500, "图片保存失败：" + e.getMessage());
        }
        return "/uploads/" + key;
    }

    private String saveToCos(MultipartFile file, String key, String ext) {
        if (!hasText(bucket) || !hasText(secretId) || !hasText(secretKey)) {
            throw new BusinessException(500, "对象存储未配置：请设置 COS_BUCKET / COS_SECRET_ID / COS_SECRET_KEY");
        }
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(contentTypeOf(ext));
            meta.setContentLength(file.getSize());
            PutObjectRequest req = new PutObjectRequest(bucket, key, file.getInputStream(), meta);
            client().putObject(req);
        } catch (RuntimeException e) {
            throw new BusinessException(500, "图片上传对象存储失败：" + e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(500, "图片读取失败：" + e.getMessage());
        }
        if (hasText(publicBaseUrl)) return trimEnd(publicBaseUrl) + "/" + key;
        return "https://" + bucket + ".cos." + region + ".myqcloud.com/" + key;
    }

    private COSClient client() {
        if (cosClient == null) {
            synchronized (this) {
                if (cosClient == null) {
                    COSCredentials cred = new BasicCOSCredentials(secretId.trim(), secretKey.trim());
                    ClientConfig cfg = new ClientConfig(new Region(region));
                    cfg.setHttpProtocol(HttpProtocol.https);
                    cosClient = new COSClient(cred, cfg);
                }
            }
        }
        return cosClient;
    }

    private String contentTypeOf(String ext) {
        if (ext.equals("jpg") || ext.equals("jpeg")) return "image/jpeg";
        if (ext.equals("png")) return "image/png";
        if (ext.equals("gif")) return "image/gif";
        if (ext.equals("webp")) return "image/webp";
        if (ext.equals("bmp")) return "image/bmp";
        return "application/octet-stream";
    }

    private boolean hasText(String s) { return s != null && s.trim().length() > 0; }
    private String trimEnd(String s) { return s.endsWith("/") ? s.substring(0, s.length() - 1) : s; }
}
