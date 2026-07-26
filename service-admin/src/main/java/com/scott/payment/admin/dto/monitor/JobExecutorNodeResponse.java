package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeResponse
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务执行器节点响应 DTO
 * @status : create
 *
 * <p>用于监控页面展示调度执行节点的注册信息、心跳状态和并发容量。</p>
 */
@Data
public class JobExecutorNodeResponse {

    /**
     * 节点记录主键 ID。
     */
    private Long id;

    /**
     * 节点业务标识。
     */
    private String nodeId;

    /**
     * 所属应用名称。
     */
    private String appName;

    /**
     * 节点主机地址。
     */
    private String host;

    /**
     * 节点端口。
     */
    private Integer port;

    /**
     * 服务实例 ID。
     */
    private String instanceId;

    /**
     * 节点状态。
     */
    private String status;

    /**
     * 最近一次心跳时间。
     */
    private LocalDateTime lastHeartbeatTime;

    /**
     * 当前正在执行的任务数。
     */
    private Integer currentRunningCount;

    /**
     * 节点最大并发执行数。
     */
    private Integer maxConcurrentCount;
}
