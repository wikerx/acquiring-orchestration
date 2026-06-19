package com.scott.payment.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerClientProperties
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务调度客户端配置属性
 * @status : create
 */

@Data
@ConfigurationProperties(prefix = "admin.job-client")
public class JobSchedulerClientProperties {

    /**
     * 是否启用远程调度中心调用。
     */
    private boolean remoteEnabled = true;

    /**
     * 任务定义分页查询接口地址。
     */
    private String taskSearchUrl = "http://service-job/internal/job/tasks/search";

    /**
     * 任务处理器白名单查询接口地址。
     */
    private String handlerListUrl = "http://service-job/internal/job/tasks/handlers";

    /**
     * 任务创建接口基础地址。
     */
    private String taskBaseUrl = "http://service-job/internal/job/tasks";

    /**
     * 执行日志分页查询接口地址。
     */
    private String runLogSearchUrl = "http://service-job/internal/job/logs/search";

    /**
     * 执行节点查询接口地址。
     */
    private String nodeListUrl = "http://service-job/internal/job/nodes";
}
