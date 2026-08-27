package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementOperationalStateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementManagementMapper
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 结算运营只读 Mapper；使用业务日期和主键游标限制扫描，不包含任何资金更新语句。
 * @status : create
 */
public interface SettlementManagementMapper {

    /** 按最多93天业务日期窗口倒序查询批次，使用主键游标避免深分页。 */
    @Select("""
            <script>
            SELECT *
            FROM settlement_batch
            WHERE business_date BETWEEN #{beginBusinessDate} AND #{endBusinessDate}
              <if test='settlementBatchNo != null and settlementBatchNo != ""'>
                AND settlement_batch_no = #{settlementBatchNo}
              </if>
              <if test='merchantId != null and merchantId != ""'>
                AND merchant_id = #{merchantId}
              </if>
              <if test='batchType != null and batchType != ""'>
                AND batch_type = #{batchType}
              </if>
              <if test='batchStatus != null and batchStatus != ""'>
                AND batch_status = #{batchStatus}
              </if>
              <if test='cursorId != null'>
                AND id &lt; #{cursorId}
              </if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<SettlementBatchDO> selectBatches(
            @Param("settlementBatchNo") String settlementBatchNo,
            @Param("merchantId") String merchantId,
            @Param("batchType") String batchType,
            @Param("batchStatus") String batchStatus,
            @Param("beginBusinessDate") LocalDate beginBusinessDate,
            @Param("endBusinessDate") LocalDate endBusinessDate,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /** 按全局唯一批次号读取详情基础行。 */
    @Select("""
            SELECT *
            FROM settlement_batch
            WHERE settlement_batch_no = #{settlementBatchNo}
            LIMIT 1
            """)
    SettlementBatchDO selectBatch(@Param("settlementBatchNo") String settlementBatchNo);

    /** 一次聚合投影和 Outbox 状态，避免详情接口逐项查询计数。 */
    @Select("""
            SELECT
                (SELECT COUNT(1) FROM settlement_projection_task
                 WHERE settlement_batch_no = #{settlementBatchNo}) AS projection_task_count,
                (SELECT COUNT(1) FROM settlement_projection_task
                 WHERE settlement_batch_no = #{settlementBatchNo} AND task_status = 'COMPLETED')
                    AS projection_completed_count,
                (SELECT COUNT(1) FROM settlement_projection_task
                 WHERE settlement_batch_no = #{settlementBatchNo} AND task_status = 'FAILED')
                    AS projection_failed_count,
                (SELECT COUNT(1) FROM settlement_event_outbox
                 WHERE settlement_batch_no = #{settlementBatchNo}) AS outbox_event_count,
                (SELECT COUNT(1) FROM settlement_event_outbox
                 WHERE settlement_batch_no = #{settlementBatchNo} AND event_status = 'SENT')
                    AS outbox_sent_count,
                (SELECT COUNT(1) FROM settlement_event_outbox
                 WHERE settlement_batch_no = #{settlementBatchNo} AND event_status = 'FAILED')
                    AS outbox_failed_count
            """)
    SettlementOperationalStateDO selectOperationalState(
            @Param("settlementBatchNo") String settlementBatchNo);
}
