package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.SysJobExecutorNodeDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysJobExecutorNodeMapper
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 系统任务执行器节点数据访问接口
 * @status : create
 */

public interface SysJobExecutorNodeMapper extends BaseMapper<SysJobExecutorNodeDO> {

    /**
     * 新增或更新节点心跳。
     *
     * @param node 节点实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO sys_job_executor_node
            (node_id, app_name, host, port, instance_id, status, last_heartbeat_time, current_running_count, max_concurrent_count, create_time, update_time)
            VALUES
            (#{node.nodeId}, #{node.appName}, #{node.host}, #{node.port}, #{node.instanceId}, #{node.status}, #{node.lastHeartbeatTime},
             #{node.currentRunningCount}, #{node.maxConcurrentCount}, #{node.createTime}, #{node.updateTime})
            ON DUPLICATE KEY UPDATE
                app_name = VALUES(app_name),
                host = VALUES(host),
                port = VALUES(port),
                instance_id = VALUES(instance_id),
                status = VALUES(status),
                last_heartbeat_time = VALUES(last_heartbeat_time),
                current_running_count = VALUES(current_running_count),
                max_concurrent_count = VALUES(max_concurrent_count),
                update_time = VALUES(update_time)
            """)
    int upsertHeartbeat(@Param("node") SysJobExecutorNodeDO node);

    /**
     * 批量将超时节点标记为离线。
     *
     * @param offlineBefore 超时阈值
     * @return 影响行数
     */
    @Update("""
            UPDATE sys_job_executor_node
            SET status = 'OFFLINE', update_time = NOW()
            WHERE last_heartbeat_time < #{offlineBefore}
              AND status <> 'OFFLINE'
            """)
    int markOffline(@Param("offlineBefore") LocalDateTime offlineBefore);
}
