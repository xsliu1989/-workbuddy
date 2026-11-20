package com.greenyi.charity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 绿益公益后台服务启动类
 */
@SpringBootApplication
@MapperScan("com.greenyi.charity.mapper")
@EnableScheduling
public class CharityApplication {
    public static void main(String[] args) {
        SpringApplication.run(CharityApplication.class, args);
    }
}
