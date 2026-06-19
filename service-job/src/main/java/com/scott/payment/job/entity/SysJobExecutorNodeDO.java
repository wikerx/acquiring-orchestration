package com.scott.payment.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysJobExecutorNodeDO
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 系统任务执行器节点数据对象
 * @status : create
 */

@Data
@TableName("sys_job_executor_node")
public class SysJobExecutorNodeDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 节点唯一标识。
     */
    private String nodeId;

    /**
     * 服务名称。
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
     * 注册中心实例 ID。
     */
    private String instanceId;

    /**
     * 节点状态。
     */
    private String status;

    /**
     * 最后心跳时间。
     */
    private LocalDateTime lastHeartbeatTime;

    /**
     * 当前运行任务数。
     */
    private Integer currentRunningCount;

    /**
     * 最大并发阈值。
     */
    private Integer maxConcurrentCount;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
