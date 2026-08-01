package com.scott.payment.data.mapper;

import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationMapper
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知任务 Mapper，只负责从主库抢占任务以及按乐观锁推进通知执行状态
 * @status : create
 */
public interface DataMerchantNotificationMapper {

    /**
     * 查询当前分表中已经到期的通知任务。
     *
     * @param physicalTableName 已由分表组件校验的物理表名
     * @param now 当前时间
     * @param limit 最大返回数量
     * @return 按重试时间和主键升序排列的任务
     */
    @Select("""
            SELECT id, notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
                   notify_config_snapshot_json, target_url_hash, target_url_masked,
                   payload_json_masked, sign_type, last_attempt_no, max_retry_count,
                   transaction_date_time, version
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND notify_status IN ('INIT', 'FAILED')
              AND next_retry_time IS NOT NULL
              AND next_retry_time <= #{now}
              AND last_attempt_no < max_retry_count
            ORDER BY next_retry_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<DataMerchantNotificationTaskDO> selectDueForNotify(@Param("physicalTableName") String physicalTableName,
                                                            @Param("now") LocalDateTime now,
                                                            @Param("limit") int limit);

    /**
     * 按交易 ID 查询当前可抢占的一条通知任务。
     *
     * @param physicalTableName 已校验物理表名
     * @param transactionId 平台交易 ID
     * @param now 当前时间
     * @return 可执行任务；未到期、已完成或不存在时返回空
     */
    @Select("""
            SELECT id, notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
                   notify_config_snapshot_json, target_url_hash, target_url_masked,
                   payload_json_masked, sign_type, last_attempt_no, max_retry_count,
                   transaction_date_time, version
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND transaction_id = #{transactionId}
              AND notify_status IN ('INIT', 'FAILED')
              AND next_retry_time IS NOT NULL
              AND next_retry_time <= #{now}
              AND last_attempt_no < max_retry_count
            ORDER BY next_retry_time ASC, id ASC
            LIMIT 1
            """)
    DataMerchantNotificationTaskDO selectReadyByTransactionId(@Param("physicalTableName") String physicalTableName,
                                                              @Param("transactionId") String transactionId,
                                                              @Param("now") LocalDateTime now);

    /**
     * 使用版本号和可执行状态抢占通知任务；数据库 CAS 是多实例和重复 MQ 的最终互斥依据。
     *
     * @param physicalTableName 已校验物理表名
     * @param id 通知任务主键
     * @param expectedVersion 读取时的乐观锁版本
     * @param now 抢占时间
     * @return 1 表示抢占成功，0 表示已被其他实例处理
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET notify_status = 'PROCESSING',
                last_attempt_no = last_attempt_no + 1,
                next_retry_time = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND notify_status IN ('INIT', 'FAILED')
              AND deleted = 0
            """)
    int markProcessing(@Param("physicalTableName") String physicalTableName,
                       @Param("id") Long id,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("now") LocalDateTime now);

    /**
     * 将本次抢占的任务推进为成功终态，禁止覆盖其他实例已经改变的版本或状态。
     *
     * @param physicalTableName 已校验的通知任务物理表名
     * @param id 通知任务主键
     * @param expectedVersion 抢占成功后的预期版本号
     * @param successTime 商户端点确认成功的时间
     * @return 1 表示推进成功，0 表示任务状态或版本已变更
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET notify_status = 'SUCCESS',
                success_time = #{successTime},
                next_retry_time = NULL,
                fail_reason = NULL,
                version = version + 1,
                update_time = #{successTime}
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND notify_status = 'PROCESSING'
              AND deleted = 0
            """)
    int markSuccess(@Param("physicalTableName") String physicalTableName,
                    @Param("id") Long id,
                    @Param("expectedVersion") Integer expectedVersion,
                    @Param("successTime") LocalDateTime successTime);

    /**
     * 将本次抢占的任务推进为可重试失败或关闭终态，禁止覆盖其他实例状态。
     *
     * @param physicalTableName 已校验的通知任务物理表名
     * @param id 通知任务主键
     * @param expectedVersion 抢占成功后的预期版本号
     * @param nextStatus 失败后状态：FAILED 表示允许重试，CLOSED 表示停止投递
     * @param nextRetryTime 下次可投递时间；关闭任务时为空
     * @param failReason 已截断且不含敏感数据的失败摘要
     * @param now 本次状态更新时间
     * @return 1 表示推进成功，0 表示任务状态或版本已变更
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET notify_status = #{nextStatus},
                next_retry_time = #{nextRetryTime},
                fail_reason = #{failReason},
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND notify_status = 'PROCESSING'
              AND deleted = 0
            """)
    int markFailed(@Param("physicalTableName") String physicalTableName,
                   @Param("id") Long id,
                   @Param("expectedVersion") Integer expectedVersion,
                   @Param("nextStatus") String nextStatus,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("failReason") String failReason,
                   @Param("now") LocalDateTime now);

    /**
     * 回收超过执行窗口仍未推进的 PROCESSING 任务。
     *
     * <p>回收后沿用原 notify_id 重试，商户必须据此实现至少一次投递下的业务幂等。</p>
     *
     * @param physicalTableName 已校验物理表名
     * @param staleBefore PROCESSING 更新时间上界
     * @param now 恢复时间和下一次可执行时间
     * @return 恢复任务数量
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET notify_status = 'FAILED',
                next_retry_time = #{now},
                fail_reason = 'processing timeout recovered',
                version = version + 1,
                update_time = #{now}
            WHERE deleted = 0
              AND notify_status = 'PROCESSING'
              AND update_time < #{staleBefore}
              AND last_attempt_no < max_retry_count
            """)
    int recoverStaleProcessing(@Param("physicalTableName") String physicalTableName,
                               @Param("staleBefore") LocalDateTime staleBefore,
                               @Param("now") LocalDateTime now);
}
