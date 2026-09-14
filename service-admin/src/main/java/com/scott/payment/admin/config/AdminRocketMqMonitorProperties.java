package com.scott.payment.admin.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRocketMqMonitorProperties
 * @date : 2026-09-14 11:24
 * @email : scott_x@163.com
 * @description : 管理端 RocketMQ Admin 只读监控配置，连接地址复用 rocketmq.name-server。
 * @status : create
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "acquiring.monitor.rocketmq")
public class AdminRocketMqMonitorProperties {

    /** 是否启用管理端 RocketMQ Admin 只读监控；不允许为空，默认关闭且非敏感。 */
    private boolean enabled;

    /** Admin 客户端组名，不允许为空，不作为生产者或消费者分组使用，非敏感。 */
    private String adminGroup = "acquiring-admin-monitor";

    /** 需要监控的集群名称；允许为空，留空时汇总 NameServer 返回的全部集群，非敏感。 */
    private String clusterName;

    /** Admin 启动和查询超时，单位毫秒，不允许为空，范围为 500 至 30000。 */
    @Min(500)
    @Max(30_000)
    private int requestTimeoutMillis = 3_000;

    /** Admin 专用 ACL AccessKey；允许为空，留空时复用 Producer 配置，属于敏感凭据且禁止输出。 */
    @ToString.Exclude
    private String accessKey;

    /** Admin 专用 ACL SecretKey；允许为空，留空时复用 Producer 配置，属于敏感凭据且禁止输出。 */
    @ToString.Exclude
    private String secretKey;
}
