package com.greenyi.charity.config;

import com.greenyi.charity.integration.FileStorageService;
import com.greenyi.charity.security.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Web 配置：注册鉴权拦截器 + 上传文件静态资源映射
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired private AuthInterceptor authInterceptor;
    @Autowired private FileStorageService fileStorageService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 管理后台静态页面（无需登录即可打开登录页）
                        "/",
                        "/index.html",
                        "/admin.html",
                        "/favicon.ico",
                        "/auth/**",
                        // 上传的图片公开可读（前端 <img>/<image> 直接访问）
                        "/uploads/**",
                        // 内容浏览（游客可读）
                        "/content/list",
                        "/content/*",
                        // 商城商品浏览（游客可读）—— 原 /product/list 路径错误，实际为 /shop/products
                        "/shop/products",
                        "/shop/products/*",
                        // 活动浏览（游客可读）
                        "/activity/list",
                        "/activity/*",
                        // 其他公开接口
                        "/certificate/verify",
                        "/pay/notify/**",
                        "/error"
                );
    }

    /** 把上传目录映射为 /uploads/**，与上传接口返回的相对 URL 对应
     *  同时挂载 classpath:/static/uploads/ —— 样例图随代码打包，避免不同启动目录导致 404 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = fileStorageService.root();
        String location = "file:" + root.toString().replace('\\', '/') + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location, "classpath:/static/uploads/");
    }
}
