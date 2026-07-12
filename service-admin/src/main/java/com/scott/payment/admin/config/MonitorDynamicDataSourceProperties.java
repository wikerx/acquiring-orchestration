package com.scott.payment.admin.config;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.strategy.DynamicDataSourceStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorDynamicDataSourceProperties
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Monitor Dynamic Data Source 配置属性，位于 service-admin 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
