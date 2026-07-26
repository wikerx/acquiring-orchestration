package com.scott.payment.component.mq.properties;

import com.scott.payment.component.mq.admin.MqResourceType;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqResourceDefinitionProperties
 * @date : 2026-06-20 22:41
 * @email : scott_x@163.com
 * @description : 单个 RocketMQ 资源声明配置
 * @status : create
 */
@Data
public class MqResourceDefinitionProperties {

    /**
     * 资源名称。
     */
    private String name;

    /**
     * 资源类型。
     */
    private MqResourceType type;

    /**
     * 所属集群名称，缺省时对全部集群广播检查。
     */
    private String clusterName;

    /**
     * Topic 读队列数。
     */
    private Integer readQueueNums = 4;

    /**
     * Topic 写队列数。
     */
    private Integer writeQueueNums = 4;

    /**
     * Topic 权限位。
     */
    private Integer perm;

    /**
     * Consumer Group 是否允许消费。
     */
    private Boolean consumeEnable = Boolean.TRUE;

    /**
     * Consumer Group 是否允许广播消费。
     */
    private Boolean consumeBroadcastEnable = Boolean.TRUE;

    /**
     * Consumer Group 重试队列数。
     */
    private Integer retryQueueNums = 1;

    /**
     * Consumer Group 最大重试次数。
     */
    private Integer retryMaxTimes = 16;

    /**
     * Consumer Group 消费超时，单位分钟。
     */
    private Integer consumeTimeoutMinute = 15;
}
