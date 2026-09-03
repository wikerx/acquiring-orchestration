package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewCandidateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewCandidateMapper
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 预审候选不可删除关系数据访问接口；冻结候选身份和版本，批准时标记消费，拒绝、取消或过期时标记释放。
 * @status : create
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
}
