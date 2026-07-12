package com.scott.payment.component.db.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MybatisPlusMapperScanConfig
 * @date : 2026-06-03 14:20
 * @email : scott_x@163.com
 * @description : MyBatis Plus Mapper 扫描配置，统一发现组件库和业务服务中的 Mapper
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MybatisPlusMapperScanConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Mybatis Plus Mapper Scan 配置，位于 component-library/component-db 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
@MapperScan("com.scott.payment.**.mapper")
public class MybatisPlusMapperScanConfig {

    /**
     * 注册 MyBatis Plus 拦截器。
     * <p>
     * 当前统一使用 MySQL 8.x，分页插件按 MySQL 方言生成分页 SQL，供后台管理、商户管理等内部列表接口复用。
     *
     * @return MyBatis Plus 拦截器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
