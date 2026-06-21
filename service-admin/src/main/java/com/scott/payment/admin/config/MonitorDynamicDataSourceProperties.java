package com.scott.payment.admin.config;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.strategy.DynamicDataSourceStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源监控静态配置快照。
 *
 * <p>该模型只用于读取当前环境下的动态数据源配置，帮助监控页展示默认数据源、
 * 严格模式、读写分组策略和静态 JDBC 声明，不参与真实数据源装配流程。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.datasource.dynamic")
public class MonitorDynamicDataSourceProperties {

    /**
     * 默认数据源名称。未显式声明 {@code @DS} 时使用该数据源。
     */
    private String primary = "master";

    /**
     * 严格模式开关。开启后访问不存在的数据源或分组会直接失败。
     */
    private Boolean strict = Boolean.FALSE;

    /**
     * 分组内路由策略类。多个从库成员之间会按该策略进行选择。
     */
    private Class<? extends DynamicDataSourceStrategy> strategy;

    /**
     * 当前环境声明的数据源明细，key 为数据源名称，value 为数据源配置。
     */
    private Map<String, DataSourceProperty> datasource = new LinkedHashMap<>();
}
