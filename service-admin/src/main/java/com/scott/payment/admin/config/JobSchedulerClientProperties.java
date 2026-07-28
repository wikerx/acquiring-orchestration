package com.scott.payment.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerClientProperties
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务调度客户端配置属性，仅维护远程调用开关。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "admin.job-client")
public class JobSchedulerClientProperties {

    /**
     * 是否启用远程调度中心调用。
     */
    private boolean remoteEnabled = true;

}
