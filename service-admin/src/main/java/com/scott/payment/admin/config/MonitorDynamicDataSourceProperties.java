package com.scott.payment.admin.config;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.strategy.DynamicDataSourceStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "spring.datasource.dynamic")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorDynamicDataSourceProperties
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Monitor Dynamic Data Source Properties 配置属性模型，位于 运营后台服务，绑定 application 配置项并提供运行时默认值。
 * @status : create
 */
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
