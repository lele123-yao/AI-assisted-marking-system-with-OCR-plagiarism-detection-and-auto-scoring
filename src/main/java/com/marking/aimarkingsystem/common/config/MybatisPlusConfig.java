package com.marking.aimarkingsystem.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * 必须注册分页插件，否则 selectPage 只会在内存里截取，
 * 实际 SQL 没有 LIMIT，数据量大时既慢又可能返回错误的总数。
 *
 * 数据权限扩展点（预留，暂不实现）：
 * 后续【课程试卷】【答卷存储】模块需要"教师只能查询自己课程的数据"，
 * 可在此处追加自定义 InnerInterceptor（如按角色拼接 teacher_id 条件），
 * 属于 SQL 层的行级过滤；当前阶段不写任何业务规则。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
