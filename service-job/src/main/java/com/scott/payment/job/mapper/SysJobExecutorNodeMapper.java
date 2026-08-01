package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.SysJobExecutorNodeDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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
     * 查询需要转为离线状态的超时节点主键。
     * <p>
     * 查询阶段使用一致性读，不申请更新锁；返回结果按心跳时间和主键稳定排序，供后续主键更新缩小锁范围。
     * 当前节点必须排除，避免节点自身在调度延迟时被错误标记为离线。
     * </p>
     *
     * @param offlineBefore 超时阈值
     * @param currentNodeId 当前节点 ID
     * @param limit 单次最大候选数量
     * @return 超时在线节点主键列表
     */
    @Select("""
            SELECT id
            FROM sys_job_executor_node
            WHERE last_heartbeat_time < #{offlineBefore}
              AND status = 'ONLINE'
              AND node_id <> #{currentNodeId}
            ORDER BY last_heartbeat_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<Long> selectTimedOutNodeIds(@Param("offlineBefore") LocalDateTime offlineBefore,
                                     @Param("currentNodeId") String currentNodeId,
                                     @Param("limit") int limit);

    /**
     * 按候选主键将仍然超时的在线节点标记为离线。
     * <p>
     * 主键访问顺序与心跳 UPSERT 的记录锁顺序保持一致，避免范围 UPDATE 先锁状态心跳二级索引、
     * 再等待主键所形成的反向锁环。更新时重新校验状态和心跳阈值，防止查询后刚恢复心跳的节点被误下线。
     * </p>
     *
     * @param nodeIds 候选节点主键，调用方保证非空
     * @param offlineBefore 超时阈值
     * @param currentNodeId 当前节点 ID
     * @return 影响行数
     */
    @Update("""
            <script>
            UPDATE sys_job_executor_node FORCE INDEX (PRIMARY)
            SET status = 'OFFLINE', update_time = NOW(3)
            WHERE id IN
            <foreach collection="nodeIds" item="nodeId" open="(" separator="," close=")">
                #{nodeId}
            </foreach>
              AND last_heartbeat_time &lt; #{offlineBefore}
              AND status = 'ONLINE'
              AND node_id &lt;&gt; #{currentNodeId}
            ORDER BY id ASC
            </script>
            """)
    int markOfflineByIds(@Param("nodeIds") List<Long> nodeIds,
                         @Param("offlineBefore") LocalDateTime offlineBefore,
                         @Param("currentNodeId") String currentNodeId);

}
