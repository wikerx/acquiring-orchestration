package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationMapper
 * @date : 2026-07-14 19:48
 * @email : scott_x@163.com
 * @description : 商户通知任务 Mapper，位于 service-payment 数据访问层，仅负责 transaction_merchant_notification 物理分表写入。
 * @status : create
 */
public interface TransactionMerchantNotificationMapper extends BaseMapper<TransactionMerchantNotificationDO> {

    /**
     * 写入商户通知任务物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param notificationDO    商户通知任务
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
            (
              notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
              notify_type, event_type, notify_status, notify_config_version, notify_config_snapshot_json,
              target_url_hash, target_url_masked, payload_json_masked, sign_type, last_attempt_no,
              max_retry_count, next_retry_time, success_time, fail_reason, transaction_date_time,
              transaction_utc_time, transaction_time_zone, version, deleted, create_time, update_time
            )
            VALUES
            (
              #{notificationDO.notifyId}, #{notificationDO.transactionId}, #{notificationDO.operationId},
              #{notificationDO.merchantId}, #{notificationDO.merchantOrderNo}, #{notificationDO.notifyType},
              #{notificationDO.eventType}, #{notificationDO.notifyStatus}, #{notificationDO.notifyConfigVersion},
              #{notificationDO.notifyConfigSnapshotJson}, #{notificationDO.targetUrlHash},
              #{notificationDO.targetUrlMasked}, #{notificationDO.payloadJsonMasked}, #{notificationDO.signType},
              #{notificationDO.lastAttemptNo}, #{notificationDO.maxRetryCount}, #{notificationDO.nextRetryTime},
              #{notificationDO.successTime}, #{notificationDO.failReason}, #{notificationDO.transactionDateTime},
              #{notificationDO.transactionUtcTime}, #{notificationDO.transactionTimeZone}, #{notificationDO.version},
              #{notificationDO.deleted}, #{notificationDO.createTime}, #{notificationDO.updateTime}
            )
            """)
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       @Param("notificationDO") TransactionMerchantNotificationDO notificationDO);

    /**
     * 按平台交易 ID 查询商户通知任务。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @return 商户通知任务列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
              AND deleted = 0
            ORDER BY create_time DESC
            LIMIT 100
            """)
    List<TransactionMerchantNotificationDO> selectByTransactionIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                          @Param("transactionId") String transactionId);

    /**
     * 按 operation_id 查询同一生命周期的商户通知任务。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 商户通知任务列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
              AND deleted = 0
            ORDER BY create_time DESC
            LIMIT 200
            """)
    List<TransactionMerchantNotificationDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                        @Param("operationId") String operationId);

    /**
     * 按交易时间范围查询商户通知任务。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param merchantId 平台商户号，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param notifyStatus 通知状态，可为空
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @param offset 分页偏移
     * @param limit 分页大小
     * @return 商户通知任务列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="merchantId != null and merchantId != ''">
                AND merchant_id = #{merchantId}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
              <if test="notifyStatus != null and notifyStatus != ''">
                AND notify_status = #{notifyStatus}
              </if>
            ORDER BY create_time DESC, id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<TransactionMerchantNotificationDO> selectPagePhysical(@Param("physicalTableName") String physicalTableName,
                                                               @Param("merchantId") String merchantId,
                                                               @Param("transactionId") String transactionId,
                                                               @Param("notifyStatus") String notifyStatus,
                                                               @Param("beginTime") LocalDateTime beginTime,
                                                               @Param("endTime") LocalDateTime endTime,
                                                               @Param("offset") long offset,
                                                               @Param("limit") long limit);

    /**
     * 统计交易时间范围内的商户通知任务数量。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param merchantId 平台商户号，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param notifyStatus 通知状态，可为空
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @return 命中记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="merchantId != null and merchantId != ''">
                AND merchant_id = #{merchantId}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
              <if test="notifyStatus != null and notifyStatus != ''">
                AND notify_status = #{notifyStatus}
              </if>
            </script>
            """)
    long countPagePhysical(@Param("physicalTableName") String physicalTableName,
                           @Param("merchantId") String merchantId,
                           @Param("transactionId") String transactionId,
                           @Param("notifyStatus") String notifyStatus,
                           @Param("beginTime") LocalDateTime beginTime,
                           @Param("endTime") LocalDateTime endTime);

    /**
     * 查询到期待通知任务。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param now 当前时间
     * @param limit 最大返回条数
     * @return 待通知任务
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND notify_status IN ('INIT', 'FAILED')
              AND next_retry_time IS NOT NULL
              AND next_retry_time <= #{now}
              AND last_attempt_no < max_retry_count
            ORDER BY next_retry_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<TransactionMerchantNotificationDO> selectDueForNotify(@Param("physicalTableName") String physicalTableName,
                                                               @Param("now") LocalDateTime now,
                                                               @Param("limit") int limit);

    /**
     * 按交易 ID 查询当前可通知的单笔任务。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @param now 当前时间
     * @return 可通知任务；无任务或尚未到重试时间时返回空
     */
    @Select("""
            SELECT *
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
    TransactionMerchantNotificationDO selectReadyByTransactionId(@Param("physicalTableName") String physicalTableName,
                                                                 @Param("transactionId") String transactionId,
                                                                 @Param("now") LocalDateTime now);

    /**
     * 抢占通知任务执行权。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param id 通知任务主键
     * @param expectedVersion 读取任务时的版本号
     * @param now 当前时间
     * @return 影响行数，1 表示抢占成功
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
     * 标记通知成功。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param id 通知任务主键
     * @param expectedVersion 当前版本号
     * @param successTime 通知成功时间
     * @return 影响行数，1 表示更新成功
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
     * 标记通知失败并安排下一次重试；超过最大次数时关闭任务。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param id 通知任务主键
     * @param expectedVersion 当前版本号
     * @param nextStatus 下一状态，FAILED 或 CLOSED
     * @param nextRetryTime 下一次重试时间，关闭任务时为空
     * @param failReason 失败原因摘要
     * @param now 当前时间
     * @return 影响行数，1 表示更新成功
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
     * 激活交易终态商户通知。
     * <p>
     * 初始交易或后续动作处于 PROCESSING/PENDING 时会先保存通知地址和载荷快照但不设置 next_retry_time；
     * 渠道回调或查询确认推进终态后，通过该方法把任务加入重试队列。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @param payloadJsonMasked 脱敏后的终态通知载荷
     * @param nextRetryTime 下一次通知时间
     * @param now 当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET payload_json_masked = #{payloadJsonMasked},
                next_retry_time = #{nextRetryTime},
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND notify_status = 'INIT'
              AND next_retry_time IS NULL
              AND deleted = 0
            """)
    int activateByTransactionId(@Param("physicalTableName") String physicalTableName,
                                @Param("transactionId") String transactionId,
                                @Param("payloadJsonMasked") String payloadJsonMasked,
                                @Param("nextRetryTime") LocalDateTime nextRetryTime,
                                @Param("now") LocalDateTime now);
}
