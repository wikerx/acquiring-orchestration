package com.scott.payment.component.db.config;

import org.mybatis.spring.annotation.MapperScan;
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
@Configuration
@MapperScan("com.scott.payment.**.mapper")
public class MybatisPlusMapperScanConfig {
}
