package com.scott.payment.component.mq.properties;

import lombok.Data;
import org.apache.rocketmq.common.constant.PermName;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqResourceInitializerProperties
 * @date : 2026-06-20 22:43
 * @email : scott_x@163.com
 * @description : 声明式 RocketMQ 资源初始化器配置
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "acquiring.mq.initializer")
public class MqResourceInitializerProperties {

    /**
     * 是否启用资源初始化器。
     */
    private boolean enabled = false;

    /**
     * 资源缺失或初始化失败时是否立即中断服务启动。
     */
    private boolean failFast = false;

    /**
     * 启动时是否扫描资源。
     */
    private boolean scanOnStartup = true;

    /**
     * 资源缺失时是否自动创建。
     */
    private boolean autoCreate = false;

    /**
     * 资源已存在时是否按声明配置覆盖更新。
     */
    private boolean updateIfExists = false;

    /**
     * Topic 默认读队列数。
     */
    private int defaultReadQueueNums = 4;

    /**
     * Topic 默认写队列数。
     */
    private int defaultWriteQueueNums = 4;

    /**
     * Topic 默认权限，默认可读可写。
     */
    private int defaultTopicPerm = PermName.PERM_READ | PermName.PERM_WRITE;

    /**
     * 声明式资源列表。
     */
    private List<MqResourceDefinitionProperties> resources = new ArrayList<>();
}
