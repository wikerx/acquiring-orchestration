package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务执行器节点响应对象
 */
@Data
public class JobExecutorNodeResponse {


    private Long id;


    private String nodeId;


    private String appName;


    private String host;

    /**
     * 响应中的{@code port}，用于管理端或商户端展示当前处理结果。
     */
    private Integer port;


    private String instanceId;


    private String status;

    /**
     * 响应中的{@code lastHeartbeatTime}，用于管理端或商户端展示当前处理结果。
     */
    private LocalDateTime lastHeartbeatTime;


    private Integer currentRunningCount;


    private Integer maxConcurrentCount;
}
