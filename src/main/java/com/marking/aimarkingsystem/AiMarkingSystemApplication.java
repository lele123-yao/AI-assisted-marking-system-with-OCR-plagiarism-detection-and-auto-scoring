package com.marking.aimarkingsystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类。
 *
 * @MapperScan 统一扫描各业务模块的 mapper 包：
 * 新增课程、答卷、AI 阅卷等模块时，只要按 com.xxx.mapper 建包即可自动注册，无需逐个添加。
 */
@MapperScan("com.marking.aimarkingsystem.**.mapper")
@SpringBootApplication
public class AiMarkingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiMarkingSystemApplication.class, args);
    }

}
