package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementManualReviewCurrencyDO;
import com.scott.payment.settlement.entity.SettlementManualReviewPreviewLineDO;
import com.scott.payment.settlement.entity.SettlementManualReviewProfileDO;
import com.scott.payment.settlement.entity.SettlementManualReviewTaskDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大批量手动交易或保证金结算预审的冻结范围、进度和候选游标数据访问。
 */
public interface SettlementManualReviewTaskMapper {

    /**
     * 查询资料；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param profileId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param merchantId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT profile.id AS settlement_profile_id, profile.merchant_id,
                   profile.settlement_account_id, account.account_no AS settlement_account_no,
                   profile.target_currency, profile.target_currency_exponent,
                   profile.business_time_zone, profile.daily_cutoff_time,
                   version.initial_delay_unit, version.initial_delay_days,
                   version.regular_delay_days, version.settlement_frequency, version.frequency_day
            FROM merchant_settlement_profile profile
            INNER JOIN merchant_fund_account account
                    ON account.id = profile.settlement_account_id
                   AND account.merchant_id = profile.merchant_id
                   AND account.settlement_currency = profile.target_currency
                   AND account.account_status = 'NORMAL' AND account.deleted = 0
            LEFT JOIN fee_plan plan
                    ON plan.merchant_id = profile.merchant_id
                   AND plan.plan_type = 'MERCHANT' AND plan.status = 'ENABLED' AND plan.deleted = 0
            LEFT JOIN fee_plan_version version
                    ON version.id = plan.current_version_id
                   AND version.plan_id = plan.id
                   AND version.version_status = 'ACTIVE' AND version.deleted = 0
                   AND version.settlement_currency = profile.target_currency
            WHERE profile.id = #{profileId}
              AND profile.merchant_id = #{merchantId}
              AND profile.profile_status = 'ACTIVE'
              AND profile.effective_date <= CURRENT_DATE()
              AND (profile.expire_date IS NULL OR profile.expire_date >= CURRENT_DATE())
            LIMIT 1 FOR UPDATE
            """)
    SettlementManualReviewProfileDO selectProfile(@Param("profileId") Long profileId,
                                                   @Param("merchantId") String merchantId);

    /**
     * 统计{@code countActiveByProfile}，返回分页、扫描或报表汇总所需的数量结果。
     * @param profileId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Select("""
            SELECT COUNT(1)
            FROM settlement_manual_review_task
            WHERE settlement_profile_id = #{profileId}
              AND task_status IN ('PREVIEWED', 'QUEUED', 'PROCESSING', 'FINALIZING', 'CANCELLING')
            """)
    int countActiveByProfile(@Param("profileId") Long profileId);

    /**
     * 查询{@code selectBlockingManualReviewBatch}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param profileId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param reviewType 待检查的普通交易或保证金释放预审类型
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT *
            FROM settlement_batch
            WHERE settlement_profile_id = #{profileId}
              AND batch_type = #{reviewType}
              AND batch_status = 'MANUAL_REVIEW'
              AND candidate_count > 0
            ORDER BY business_date ASC, id ASC
            LIMIT 1
            """)
    SettlementBatchDO selectBlockingManualReviewBatch(@Param("profileId") Long profileId,
                                                       @Param("reviewType") String reviewType);

    /**
     * 查询{@code selectLastPostedCutoffEnd}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param profileId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param reviewType 用于隔离普通交易和保证金释放批次的预审类型
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT COALESCE(MAX(cutoff_end_time), TIMESTAMP('1970-01-01 00:00:00'))
            FROM settlement_batch
            WHERE settlement_profile_id = #{profileId}
              AND batch_type = #{reviewType}
              AND batch_status = 'POSTED'
            """)
    LocalDateTime selectLastPostedCutoffEnd(@Param("profileId") Long profileId,
                                            @Param("reviewType") String reviewType);

    /**
     * 查询{@code selectSnapshotMaxCandidateId}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param merchantId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param profileId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param targetCurrency 币种代码，格式为 ISO 4217 三位大写字母
     * @param businessDate 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param cutoffEndTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param reviewType 普通交易或保证金释放预审类型
     * @param paymentType 可选支付业务类型筛选条件
     * @param paymentMethod 可选支付方式筛选条件
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            <script>
            SELECT candidate.id
            FROM settlement_candidate candidate
            LEFT JOIN transaction_clearing_detail detail
                   ON candidate.source_type = 'CLEARING_REVISION'
                  AND detail.transaction_id = candidate.source_transaction_id
                   AND detail.transaction_date_time = candidate.source_transaction_date_time
                   AND detail.clearing_revision = candidate.source_revision
                   AND detail.line_no = 1 AND detail.record_status = 'ACTIVE'
            LEFT JOIN transaction_reserve_clearing_detail reserve_detail
                   ON candidate.source_type = 'RESERVE_RELEASE'
                  AND reserve_detail.transaction_id = candidate.source_transaction_id
                  AND reserve_detail.transaction_date_time = candidate.source_transaction_date_time
                  AND reserve_detail.clearing_revision = candidate.source_revision
                  AND reserve_detail.line_no = 1 AND reserve_detail.record_status = 'ACTIVE'
            WHERE candidate.merchant_id = #{merchantId}
              AND candidate.settlement_profile_id = #{profileId}
              AND candidate.target_currency = #{targetCurrency}
              AND ((#{reviewType} = 'REGULAR'
                    AND candidate.source_type = 'CLEARING_REVISION' AND detail.id IS NOT NULL)
                   OR (#{reviewType} = 'RESERVE_RELEASE'
                       AND candidate.source_type = 'RESERVE_RELEASE'
                       AND reserve_detail.reserve_action_type = 'RELEASE'
                       AND reserve_detail.expected_reserve_release_date IS NOT NULL
                       AND reserve_detail.expected_reserve_release_date &lt;= #{businessDate}))
              AND candidate.candidate_status = 'READY'
              AND candidate.shadow_mode = 0
              AND candidate.settlement_batch_no IS NULL AND candidate.review_order_no IS NULL
              AND candidate.settlement_eligible_date &lt;= #{businessDate}
              AND candidate.create_time &lt; #{cutoffEndTime}
              <if test="paymentType != null and paymentType != ''">
                AND COALESCE(detail.payment_type, reserve_detail.payment_type) = #{paymentType}
              </if>
              <if test="paymentMethod != null and paymentMethod != ''">
                AND COALESCE(detail.payment_method, reserve_detail.payment_method) = #{paymentMethod}
              </if>
            ORDER BY candidate.id DESC
            LIMIT 1
            </script>
            """)
    Long selectSnapshotMaxCandidateId(@Param("merchantId") String merchantId,
                                      @Param("profileId") Long profileId,
                                      @Param("targetCurrency") String targetCurrency,
                                      @Param("businessDate") java.time.LocalDate businessDate,
                                      @Param("cutoffEndTime") LocalDateTime cutoffEndTime,
                                      @Param("reviewType") String reviewType,
                                      @Param("paymentType") String paymentType,
                                      @Param("paymentMethod") String paymentMethod);

    /**
     * 查询{@code selectPreviewLines}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param merchantId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param profileId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param targetCurrency 币种代码，格式为 ISO 4217 三位大写字母
     * @param businessDate 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param cutoffEndTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param maxCandidateId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param reviewType 普通交易或保证金释放预审类型
     * @param paymentType 可选支付业务类型筛选条件
     * @param paymentMethod 可选支付方式筛选条件
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            <script>
            SELECT preview.*
            FROM (
                SELECT detail.label_currency AS source_currency,
                       detail.label_currency_exponent AS source_currency_exponent,
                       COUNT(DISTINCT candidate.id) AS transaction_count,
                       COALESCE(SUM(finance.gross_label_amount), 0) AS gross_amount,
                       CASE WHEN SUM(finance.platform_fee_amount IS NULL) = 0
                            THEN COALESCE(SUM(finance.platform_fee_amount), 0) ELSE NULL END AS platform_fee_amount,
                       COALESCE(SUM(finance.reserve_amount), 0) AS reserve_amount,
                       CAST(0 AS DECIMAL(24,8)) AS released_reserve_amount,
                       CASE WHEN SUM(finance.net_settlement_amount IS NULL) = 0
                            THEN COALESCE(SUM(finance.net_settlement_amount), 0) ELSE NULL END AS net_settlement_amount,
                       SUM(finance.net_settlement_amount IS NULL) AS pending_fee_count,
                       CAST(NULL AS CHAR(8)) AS reserve_delay_unit,
                       CAST(NULL AS SIGNED) AS minimum_reserve_delay_days,
                       CAST(NULL AS SIGNED) AS maximum_reserve_delay_days,
                       CAST(NULL AS DATE) AS earliest_expected_release_date,
                       CAST(NULL AS DATE) AS latest_expected_release_date
                FROM settlement_candidate candidate
                INNER JOIN transaction_finance_state finance
                        ON finance.finance_state_id = candidate.source_business_id
                       AND finance.transaction_id = candidate.source_transaction_id
                       AND finance.transaction_date_time = candidate.source_transaction_date_time
                       AND finance.clearing_revision = candidate.source_revision AND finance.deleted = 0
                INNER JOIN transaction_clearing_detail detail
                        ON detail.transaction_id = candidate.source_transaction_id
                       AND detail.transaction_date_time = candidate.source_transaction_date_time
                       AND detail.clearing_revision = candidate.source_revision
                       AND detail.line_no = 1 AND detail.record_status = 'ACTIVE'
                WHERE #{reviewType} = 'REGULAR'
                  AND candidate.merchant_id = #{merchantId}
                  AND candidate.settlement_profile_id = #{profileId}
                  AND candidate.target_currency = #{targetCurrency}
                  AND candidate.source_type = 'CLEARING_REVISION'
                  AND candidate.candidate_status = 'READY' AND candidate.shadow_mode = 0
                  AND candidate.settlement_batch_no IS NULL AND candidate.review_order_no IS NULL
                  AND candidate.settlement_eligible_date &lt;= #{businessDate}
                  AND candidate.create_time &lt; #{cutoffEndTime}
                  AND candidate.id &lt;= #{maxCandidateId}
                  <if test="paymentType != null and paymentType != ''">AND detail.payment_type = #{paymentType}</if>
                  <if test="paymentMethod != null and paymentMethod != ''">AND detail.payment_method = #{paymentMethod}</if>
                GROUP BY detail.label_currency, detail.label_currency_exponent

                UNION ALL

                SELECT reserve_detail.reserve_currency AS source_currency,
                       reserve_detail.reserve_currency_exponent AS source_currency_exponent,
                       COUNT(DISTINCT candidate.id) AS transaction_count,
                       CAST(0 AS DECIMAL(24,8)) AS gross_amount,
                       CAST(NULL AS DECIMAL(24,8)) AS platform_fee_amount,
                       CAST(0 AS DECIMAL(24,8)) AS reserve_amount,
                       COALESCE(SUM(reserve_detail.released_amount), 0) AS released_reserve_amount,
                       CAST(NULL AS DECIMAL(24,8)) AS net_settlement_amount,
                       0 AS pending_fee_count,
                       CASE WHEN COUNT(DISTINCT reserve_detail.reserve_delay_unit) = 1
                            THEN MIN(reserve_detail.reserve_delay_unit) ELSE 'MIXED' END AS reserve_delay_unit,
                       MIN(reserve_detail.reserve_delay_days) AS minimum_reserve_delay_days,
                       MAX(reserve_detail.reserve_delay_days) AS maximum_reserve_delay_days,
                       MIN(reserve_detail.expected_reserve_release_date) AS earliest_expected_release_date,
                       MAX(reserve_detail.expected_reserve_release_date) AS latest_expected_release_date
                FROM settlement_candidate candidate
                INNER JOIN transaction_reserve_clearing_detail reserve_detail
                        ON reserve_detail.transaction_id = candidate.source_transaction_id
                       AND reserve_detail.transaction_date_time = candidate.source_transaction_date_time
                       AND reserve_detail.clearing_revision = candidate.source_revision
                       AND reserve_detail.line_no = 1 AND reserve_detail.record_status = 'ACTIVE'
                       AND reserve_detail.reserve_action_type = 'RELEASE'
                WHERE #{reviewType} = 'RESERVE_RELEASE'
                  AND candidate.merchant_id = #{merchantId}
                  AND candidate.settlement_profile_id = #{profileId}
                  AND candidate.target_currency = #{targetCurrency}
                  AND candidate.source_type = 'RESERVE_RELEASE'
                  AND candidate.candidate_status = 'READY' AND candidate.shadow_mode = 0
                  AND candidate.settlement_batch_no IS NULL AND candidate.review_order_no IS NULL
                  AND candidate.settlement_eligible_date &lt;= #{businessDate}
                  AND reserve_detail.expected_reserve_release_date IS NOT NULL
                  AND reserve_detail.expected_reserve_release_date &lt;= #{businessDate}
                  AND candidate.create_time &lt; #{cutoffEndTime}
                  AND candidate.id &lt;= #{maxCandidateId}
                  <if test="paymentType != null and paymentType != ''">AND reserve_detail.payment_type = #{paymentType}</if>
                  <if test="paymentMethod != null and paymentMethod != ''">AND reserve_detail.payment_method = #{paymentMethod}</if>
                GROUP BY reserve_detail.reserve_currency, reserve_detail.reserve_currency_exponent
            ) preview
            ORDER BY preview.source_currency
            </script>
            """)
    List<SettlementManualReviewPreviewLineDO> selectPreviewLines(
            @Param("merchantId") String merchantId,
            @Param("profileId") Long profileId,
            @Param("targetCurrency") String targetCurrency,
            @Param("businessDate") java.time.LocalDate businessDate,
            @Param("cutoffEndTime") LocalDateTime cutoffEndTime,
            @Param("maxCandidateId") long maxCandidateId,
            @Param("reviewType") String reviewType,
            @Param("paymentType") String paymentType,
            @Param("paymentMethod") String paymentMethod);

    /**
     * 创建{@code insertIdempotent}，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 结算服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 写入、更新或删除后的处理结果
     */
    @Insert("""
            INSERT INTO settlement_manual_review_task
            (task_no, request_key, review_order_no, review_type, merchant_id, settlement_profile_id,
             settlement_account_id, target_currency, target_currency_exponent, payment_type, payment_method,
             business_date, business_time_zone, cutoff_begin_time, cutoff_end_time,
             snapshot_max_candidate_id,
             expected_candidate_count, processed_candidate_count, locked_candidate_count, last_candidate_id,
             initial_delay_unit, initial_delay_days, regular_delay_days, settlement_frequency, frequency_day,
             preview_json, task_status, submitted_by_account_id, submitted_by_account_name,
             submitted_role_snapshot, submit_client_ip, submit_user_agent, submit_reason, operation_time,
             retry_count, next_retry_time, version, create_time, update_time)
            VALUES
            (#{taskNo}, #{requestKey}, #{reviewOrderNo}, #{reviewType}, #{merchantId}, #{settlementProfileId},
             #{settlementAccountId}, #{targetCurrency}, #{targetCurrencyExponent}, #{paymentType}, #{paymentMethod},
             #{businessDate}, #{businessTimeZone}, #{cutoffBeginTime}, #{cutoffEndTime},
             #{snapshotMaxCandidateId},
             #{expectedCandidateCount}, 0, 0, 0,
             #{initialDelayUnit}, #{initialDelayDays}, #{regularDelayDays}, #{settlementFrequency}, #{frequencyDay},
             #{previewJson}, #{taskStatus}, #{submittedByAccountId}, #{submittedByAccountName},
             #{submittedRoleSnapshot}, #{submitClientIp}, #{submitUserAgent}, #{submitReason}, #{operationTime},
             0, #{nextRetryTime}, 0, #{createTime}, #{updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(SettlementManualReviewTaskDO row);

    /**
     * 查询{@code selectByRequestKeyForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param requestKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("SELECT * FROM settlement_manual_review_task WHERE request_key = #{requestKey} LIMIT 1 FOR UPDATE")
    SettlementManualReviewTaskDO selectByRequestKeyForUpdate(@Param("requestKey") String requestKey);

    /**
     * 查询{@code selectByTaskNo}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param taskNo 可恢复手动结算任务号
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("SELECT * FROM settlement_manual_review_task WHERE task_no = #{taskNo} LIMIT 1")
    SettlementManualReviewTaskDO selectByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 查询{@code selectByTaskNoForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param taskNo 待加锁读取的手动结算任务号
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("SELECT * FROM settlement_manual_review_task WHERE task_no = #{taskNo} LIMIT 1 FOR UPDATE")
    SettlementManualReviewTaskDO selectByTaskNoForUpdate(@Param("taskNo") String taskNo);

    /**
     * 查询按提交请求密钥；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param requestKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("SELECT * FROM settlement_manual_review_task WHERE submit_request_key = #{requestKey} LIMIT 1")
    SettlementManualReviewTaskDO selectBySubmitRequestKey(@Param("requestKey") String requestKey);

    /**
     * 使用提交幂等键和预期版本将预览任务推进到待处理状态。
     * @param taskNo 待启动的手动结算任务号
     * @param requestKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param expectedVersion 调用方读取到的任务版本，防止并发启动覆盖
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_manual_review_task
            SET submit_request_key = #{requestKey}, task_status = 'QUEUED', next_retry_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PREVIEWED' AND version = #{expectedVersion}
            """)
    int start(@Param("taskNo") String taskNo, @Param("requestKey") String requestKey,
              @Param("expectedVersion") long expectedVersion, @Param("now") LocalDateTime now);

    /**
     * 查询{@code selectNextDueForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT * FROM settlement_manual_review_task
            WHERE task_status IN ('QUEUED', 'PROCESSING', 'CANCELLING')
              AND next_retry_time <= #{now}
              AND (processing_deadline IS NULL OR processing_deadline <= #{now})
            ORDER BY next_retry_time, id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    SettlementManualReviewTaskDO selectNextDueForUpdate(@Param("now") LocalDateTime now);

    /**
     * 通过版本和过期租约条件领取到期任务，取消中的任务保持补偿状态。
     * @param taskNo 待领取的手动结算任务号
     * @param version 乐观锁预期版本，防止并发节点重复领取
     * @param owner 当前任务租约持有者
     * @param deadline 本次处理租约截止时间
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_manual_review_task
            SET task_status = CASE WHEN task_status = 'CANCELLING' THEN 'CANCELLING' ELSE 'PROCESSING' END,
                processing_owner = #{owner}, processing_deadline = #{deadline},
                started_time = COALESCE(started_time, #{now}), version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status IN ('QUEUED', 'PROCESSING', 'CANCELLING')
              AND (processing_deadline IS NULL OR processing_deadline <= #{now})
              AND version = #{version}
            """)
    int markProcessing(@Param("taskNo") String taskNo, @Param("version") long version,
                       @Param("owner") String owner, @Param("deadline") LocalDateTime deadline,
                       @Param("now") LocalDateTime now);

    /**
     * 查询{@code selectNextCandidatesForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param task 已加锁读取的任务快照，限定候选范围和处理游标
     * @param limit 分页或扫描窗口参数，用于限制单次查询范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            <script>
            SELECT candidate.*
            FROM settlement_candidate candidate
            LEFT JOIN transaction_clearing_detail detail
                   ON candidate.source_type = 'CLEARING_REVISION'
                  AND detail.transaction_id = candidate.source_transaction_id
                   AND detail.transaction_date_time = candidate.source_transaction_date_time
                   AND detail.clearing_revision = candidate.source_revision
                   AND detail.line_no = 1 AND detail.record_status = 'ACTIVE'
            LEFT JOIN transaction_reserve_clearing_detail reserve_detail
                   ON candidate.source_type = 'RESERVE_RELEASE'
                  AND reserve_detail.transaction_id = candidate.source_transaction_id
                  AND reserve_detail.transaction_date_time = candidate.source_transaction_date_time
                  AND reserve_detail.clearing_revision = candidate.source_revision
                  AND reserve_detail.line_no = 1 AND reserve_detail.record_status = 'ACTIVE'
            WHERE candidate.id &gt; #{task.lastCandidateId}
              AND candidate.id &lt;= #{task.snapshotMaxCandidateId}
              AND candidate.merchant_id = #{task.merchantId}
              AND candidate.settlement_profile_id = #{task.settlementProfileId}
              AND candidate.target_currency = #{task.targetCurrency}
              AND candidate.target_currency_exponent = #{task.targetCurrencyExponent}
              AND ((#{task.reviewType} = 'REGULAR'
                    AND candidate.source_type = 'CLEARING_REVISION' AND detail.id IS NOT NULL)
                   OR (#{task.reviewType} = 'RESERVE_RELEASE'
                       AND candidate.source_type = 'RESERVE_RELEASE'
                       AND reserve_detail.reserve_action_type = 'RELEASE'
                       AND reserve_detail.expected_reserve_release_date IS NOT NULL
                       AND reserve_detail.expected_reserve_release_date &lt;= #{task.businessDate}))
              AND candidate.candidate_status = 'READY' AND candidate.shadow_mode = 0
              AND candidate.settlement_batch_no IS NULL AND candidate.review_order_no IS NULL
              AND candidate.settlement_eligible_date &lt;= #{task.businessDate}
              AND candidate.create_time &lt; #{task.cutoffEndTime}
              <if test="task.paymentType != null and task.paymentType != ''">AND COALESCE(detail.payment_type, reserve_detail.payment_type) = #{task.paymentType}</if>
              <if test="task.paymentMethod != null and task.paymentMethod != ''">AND COALESCE(detail.payment_method, reserve_detail.payment_method) = #{task.paymentMethod}</if>
            ORDER BY candidate.id ASC LIMIT #{limit} FOR UPDATE
            </script>
            """)
    List<SettlementCandidateDO> selectNextCandidatesForUpdate(
            @Param("task") SettlementManualReviewTaskDO task, @Param("limit") int limit);

    /**
     * 查询{@code selectCurrencies}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param task 任务快照，限定候选范围和所需币种集合
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            <script>
            SELECT currency, MAX(currency_exponent) AS currency_exponent
            FROM (
                SELECT detail.currency, detail.currency_exponent
                FROM settlement_candidate candidate
                INNER JOIN transaction_clearing_detail detail
                        ON detail.transaction_id = candidate.source_transaction_id
                       AND detail.transaction_date_time = candidate.source_transaction_date_time
                       AND detail.clearing_revision = candidate.source_revision
                       AND detail.record_status = 'ACTIVE'
                WHERE #{task.reviewType} = 'REGULAR'
                  AND candidate.id &lt;= #{task.snapshotMaxCandidateId}
                  AND candidate.merchant_id = #{task.merchantId}
                  AND candidate.settlement_profile_id = #{task.settlementProfileId}
                  AND candidate.target_currency = #{task.targetCurrency}
                  AND candidate.source_type = 'CLEARING_REVISION'
                  AND candidate.candidate_status IN ('READY', 'REVIEW_LOCKED')
                  AND candidate.shadow_mode = 0
                  AND candidate.settlement_eligible_date &lt;= #{task.businessDate}
                  AND candidate.create_time &lt; #{task.cutoffEndTime}
                  <if test="task.paymentType != null and task.paymentType != ''">AND detail.payment_type = #{task.paymentType}</if>
                  <if test="task.paymentMethod != null and task.paymentMethod != ''">AND detail.payment_method = #{task.paymentMethod}</if>
                UNION ALL
                SELECT 'USD', 2
                FROM settlement_candidate candidate
                INNER JOIN transaction_clearing_detail detail
                        ON detail.transaction_id = candidate.source_transaction_id
                       AND detail.transaction_date_time = candidate.source_transaction_date_time
                       AND detail.clearing_revision = candidate.source_revision
                       AND detail.record_status = 'ACTIVE'
                WHERE #{task.reviewType} = 'REGULAR'
                  AND candidate.id &lt;= #{task.snapshotMaxCandidateId}
                  AND candidate.merchant_id = #{task.merchantId}
                  AND candidate.settlement_profile_id = #{task.settlementProfileId}
                  AND candidate.target_currency = #{task.targetCurrency}
                  AND candidate.source_type = 'CLEARING_REVISION'
                  AND candidate.candidate_status IN ('READY', 'REVIEW_LOCKED')
                  AND candidate.shadow_mode = 0
                  AND candidate.settlement_eligible_date &lt;= #{task.businessDate}
                  AND candidate.create_time &lt; #{task.cutoffEndTime}
                  AND (detail.minimum_amount_usd IS NOT NULL OR detail.maximum_amount_usd IS NOT NULL)
                  <if test="task.paymentType != null and task.paymentType != ''">AND detail.payment_type = #{task.paymentType}</if>
                  <if test="task.paymentMethod != null and task.paymentMethod != ''">AND detail.payment_method = #{task.paymentMethod}</if>
                UNION ALL
                SELECT reserve_detail.reserve_currency, reserve_detail.reserve_currency_exponent
                FROM settlement_candidate candidate
                INNER JOIN transaction_reserve_clearing_detail reserve_detail
                        ON reserve_detail.transaction_id = candidate.source_transaction_id
                       AND reserve_detail.transaction_date_time = candidate.source_transaction_date_time
                       AND reserve_detail.clearing_revision = candidate.source_revision
                       AND reserve_detail.line_no = 1 AND reserve_detail.record_status = 'ACTIVE'
                       AND reserve_detail.reserve_action_type = 'RELEASE'
                WHERE #{task.reviewType} = 'RESERVE_RELEASE'
                  AND candidate.id &lt;= #{task.snapshotMaxCandidateId}
                  AND candidate.merchant_id = #{task.merchantId}
                  AND candidate.settlement_profile_id = #{task.settlementProfileId}
                  AND candidate.target_currency = #{task.targetCurrency}
                  AND candidate.source_type = 'RESERVE_RELEASE'
                  AND candidate.candidate_status IN ('READY', 'REVIEW_LOCKED')
                  AND candidate.shadow_mode = 0
                  AND candidate.settlement_eligible_date &lt;= #{task.businessDate}
                  AND reserve_detail.expected_reserve_release_date IS NOT NULL
                  AND reserve_detail.expected_reserve_release_date &lt;= #{task.businessDate}
                  AND candidate.create_time &lt; #{task.cutoffEndTime}
                  <if test="task.paymentType != null and task.paymentType != ''">AND reserve_detail.payment_type = #{task.paymentType}</if>
                  <if test="task.paymentMethod != null and task.paymentMethod != ''">AND reserve_detail.payment_method = #{task.paymentMethod}</if>
                UNION ALL SELECT #{task.targetCurrency}, #{task.targetCurrencyExponent}
            ) currencies
            GROUP BY currency
            HAVING MIN(currency_exponent) = MAX(currency_exponent)
            ORDER BY currency
            </script>
            """)
    List<SettlementManualReviewCurrencyDO> selectCurrencies(@Param("task") SettlementManualReviewTaskDO task);

    /**
     * 提交本段候选锁定进度并释放节点租约；仅当前 owner 和版本匹配时更新。
     * @param taskNo 正在处理的手动结算任务号
     * @param version 乐观锁预期版本
     * @param owner 当前任务租约持有者
     * @param count 本段成功锁定的候选数量
     * @param lastCandidateId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_manual_review_task
            SET processed_candidate_count = processed_candidate_count + #{count},
                locked_candidate_count = locked_candidate_count + #{count},
                last_candidate_id = #{lastCandidateId}, processing_owner = NULL,
                processing_deadline = NULL, next_retry_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING'
              AND version = #{version} AND processing_owner = #{owner}
            """)
    int advance(@Param("taskNo") String taskNo, @Param("version") long version,
                @Param("owner") String owner, @Param("count") int count,
                @Param("lastCandidateId") long lastCandidateId, @Param("now") LocalDateTime now);

    /**
     * 在锁定数量与快照候选数量一致时进入结果固化阶段。
     * @param taskNo 待固化结果的手动结算任务号
     * @param version 乐观锁预期版本
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_manual_review_task
            SET task_status = 'FINALIZING', processing_owner = NULL, processing_deadline = NULL,
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING' AND version = #{version}
              AND locked_candidate_count = expected_candidate_count
            """)
    int markFinalizing(@Param("taskNo") String taskNo, @Param("version") long version,
                       @Param("now") LocalDateTime now);

    /**
     * 将完成结果固化的任务推进到完成终态。
     * @param taskNo 待完成的手动结算任务号
     * @param version 乐观锁预期版本
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_manual_review_task
            SET task_status = 'COMPLETED', completed_time = #{now}, processing_owner = NULL,
                processing_deadline = NULL, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'FINALIZING' AND version = #{version}
            """)
    int markCompleted(@Param("taskNo") String taskNo, @Param("version") long version,
                      @Param("now") LocalDateTime now);

    /**
     * 对没有候选锁定副作用的失败任务直接写入失败终态。
     * @param taskNo 待终止的手动结算任务号
     * @param version 乐观锁预期版本
     * @param failureCode 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param failureMessage 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_manual_review_task
            SET task_status = 'FAILED', retry_count = retry_count + 1,
                last_failure_code = #{failureCode}, last_failure_message = #{failureMessage},
                processing_owner = NULL, processing_deadline = NULL,
                next_retry_time = #{now}, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND version = #{version}
              AND task_status IN ('QUEUED', 'PROCESSING', 'FINALIZING')
            """)
    int markFailed(@Param("taskNo") String taskNo, @Param("version") long version,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("now") LocalDateTime now);

    /**
     * 记录可重试失败并将任务重新排入指定执行时间。
     * @param taskNo 待重新调度的手动结算任务号
     * @param version 乐观锁预期版本
     * @param failureCode 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param failureMessage 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param nextRetryTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_manual_review_task
            SET task_status = 'QUEUED', retry_count = retry_count + 1,
                last_failure_code = #{failureCode}, last_failure_message = #{failureMessage},
                processing_owner = NULL, processing_deadline = NULL,
                next_retry_time = #{nextRetryTime}, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND version = #{version}
              AND task_status IN ('PROCESSING', 'FINALIZING')
            """)
    int reschedule(@Param("taskNo") String taskNo, @Param("version") long version,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("now") LocalDateTime now);

    /**
     * 将已有候选锁定副作用的失败任务转入补偿状态，等待释放候选。
     * @param taskNo 待补偿的手动结算任务号
     * @param version 乐观锁预期版本
     * @param failureCode 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param failureMessage 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_manual_review_task
            SET task_status = 'CANCELLING', retry_count = retry_count + 1,
                last_failure_code = #{failureCode}, last_failure_message = #{failureMessage},
                processing_owner = NULL, processing_deadline = NULL,
                next_retry_time = #{now}, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND version = #{version}
              AND task_status IN ('QUEUED', 'PROCESSING', 'FINALIZING')
            """)
    int beginFailureCompensation(@Param("taskNo") String taskNo, @Param("version") long version,
                                 @Param("failureCode") String failureCode,
                                 @Param("failureMessage") String failureMessage,
                                 @Param("now") LocalDateTime now);

    /**
     * 在候选释放完成后将补偿任务推进到失败终态。
     * @param taskNo 已完成候选释放的手动结算任务号
     * @param version 乐观锁预期版本
     * @param owner 当前补偿任务租约持有者
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_manual_review_task
            SET task_status = 'FAILED', completed_time = #{now}, processing_owner = NULL,
                processing_deadline = NULL, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'CANCELLING'
              AND version = #{version} AND processing_owner = #{owner}
            """)
    int finishFailureCompensation(@Param("taskNo") String taskNo, @Param("version") long version,
                                  @Param("owner") String owner, @Param("now") LocalDateTime now);
}
