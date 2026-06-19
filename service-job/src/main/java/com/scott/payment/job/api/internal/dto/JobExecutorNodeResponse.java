package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeResponse
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务执行器节点响应对象
 * @status : create
 */

@Data
public class JobExecutorNodeResponse {

    private Long id;

    private String nodeId;

    private String appName;

    private String host;

    private Integer port;

    private String instanceId;

    private String status;

    private LocalDateTime lastHeartbeatTime;

    private Integer currentRunningCount;

    private Integer maxConcurrentCount;
}
