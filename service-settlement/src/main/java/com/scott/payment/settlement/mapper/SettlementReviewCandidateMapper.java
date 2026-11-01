package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewCandidateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预审候选不可删除关系数据访问接口；冻结候选身份和版本，批准时标记消费，拒绝、取消或过期时标记释放。
 */
public interface SettlementReviewCandidateMapper {

    /**
     * 批量追加预审候选不可变快照，唯一冲突保持原行不变。
     * @param rows 已锁候选身份、版本和清分指纹集合
     * @return 实际插入行数，调用方必须与集合大小核对
     */
    @Insert("""
            <script>
            INSERT INTO settlement_review_candidate
            (review_candidate_no, review_order_no, candidate_id, candidate_no, source_type,
             source_business_id, source_revision, source_transaction_id,
             source_transaction_date_time, locked_candidate_version, clearing_fingerprint,
             relation_status, locked_time, version, create_time, update_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.reviewCandidateNo}, #{row.reviewOrderNo}, #{row.candidateId}, #{row.candidateNo},
                 #{row.sourceType}, #{row.sourceBusinessId}, #{row.sourceRevision},
                 #{row.sourceTransactionId}, #{row.sourceTransactionDateTime},
                 #{row.lockedCandidateVersion}, #{row.clearingFingerprint}, #{row.relationStatus},
                 #{row.lockedTime}, #{row.version}, #{row.createTime}, #{row.updateTime})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertBatchIdempotent(@Param("rows") List<SettlementReviewCandidateDO> rows);

    /**
     * 按候选 ID 顺序锁读预审单全部关系，建立稳定锁顺序。
     * @param reviewOrderNo 结算预审单号
     * @return 已加行锁的候选关系集合
     */
    @Select("""
            SELECT *
            FROM settlement_review_candidate
            WHERE review_order_no = #{reviewOrderNo}
            ORDER BY candidate_id ASC, id ASC
            FOR UPDATE
            """)
    List<SettlementReviewCandidateDO> selectByOrderNoForUpdate(
            @Param("reviewOrderNo") String reviewOrderNo);

    /**
     * 查询{@code selectRangeForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param reviewOrderNo 结算预审单号或冲正单号，用于定位唯一业务单据
     * @param firstCandidateId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param lastCandidateId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT *
            FROM settlement_review_candidate
            WHERE review_order_no = #{reviewOrderNo}
              AND candidate_id BETWEEN #{firstCandidateId} AND #{lastCandidateId}
              AND relation_status = 'LOCKED'
            ORDER BY candidate_id ASC, id ASC
            FOR UPDATE
            """)
    List<SettlementReviewCandidateDO> selectRangeForUpdate(
            @Param("reviewOrderNo") String reviewOrderNo,
            @Param("firstCandidateId") long firstCandidateId,
            @Param("lastCandidateId") long lastCandidateId);

    /**
     * 将预审单全部 LOCKED 关系标记为已被正式批次消费。
     * @param reviewOrderNo 已批准预审单号
     * @param now 消费时间
     * @return 更新关系数，必须等于预审候选数
     */
    @Update("""
            UPDATE settlement_review_candidate
            SET relation_status = 'CONSUMED',
                consumed_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE review_order_no = #{reviewOrderNo}
              AND relation_status = 'LOCKED'
            """)
    int markConsumed(@Param("reviewOrderNo") String reviewOrderNo,
                     @Param("now") LocalDateTime now);

    /**
     * 将拒绝、取消或过期预审单的全部 LOCKED 关系标记释放。
     * @param reviewOrderNo 终止预审单号
     * @param now 释放时间
     * @return 更新关系数，必须等于预审候选数
     */
    @Update("""
            UPDATE settlement_review_candidate
            SET relation_status = 'RELEASED',
                released_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE review_order_no = #{reviewOrderNo}
              AND relation_status = 'LOCKED'
            """)
    int markReleased(@Param("reviewOrderNo") String reviewOrderNo,
                     @Param("now") LocalDateTime now);

    /**
     * 按分段主键边界释放仍属于该预审单的候选，避免影响已被其他流程消费的记录。
     * @param reviewOrderNo 结算预审单号或冲正单号，用于定位唯一业务单据
     * @param firstCandidateId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param lastCandidateId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_review_candidate
            SET relation_status = 'RELEASED', released_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE review_order_no = #{reviewOrderNo}
              AND candidate_id BETWEEN #{firstCandidateId} AND #{lastCandidateId}
              AND relation_status = 'LOCKED'
            """)
    int markRangeReleased(@Param("reviewOrderNo") String reviewOrderNo,
                          @Param("firstCandidateId") long firstCandidateId,
                          @Param("lastCandidateId") long lastCandidateId,
                          @Param("now") LocalDateTime now);

    /**
     * 按分段主键边界将预审候选绑定到正式结算批次，只更新仍处于锁定状态的记录。
     * @param reviewOrderNo 结算预审单号或冲正单号，用于定位唯一业务单据
     * @param firstCandidateId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param lastCandidateId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_review_candidate
            SET relation_status = 'CONSUMED', consumed_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE review_order_no = #{reviewOrderNo}
              AND candidate_id BETWEEN #{firstCandidateId} AND #{lastCandidateId}
              AND relation_status = 'LOCKED'
            """)
    int markRangeConsumed(@Param("reviewOrderNo") String reviewOrderNo,
                          @Param("firstCandidateId") long firstCandidateId,
                          @Param("lastCandidateId") long lastCandidateId,
                          @Param("now") LocalDateTime now);
}
