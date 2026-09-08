package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewSegmentDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 大批量预审单的有界处理分段数据访问。 */
public interface SettlementReviewSegmentMapper {

    @Insert("""
            INSERT INTO settlement_review_segment
            (segment_no, review_order_no, sequence_no, first_candidate_id, last_candidate_id,
             candidate_count, projectable_candidate_count, source_fingerprint,
             result_fingerprint, net_signed_amount, settlement_batch_no, segment_status, processed_time,
             version, create_time, update_time)
            VALUES
            (#{row.segmentNo}, #{row.reviewOrderNo}, #{row.sequenceNo}, #{row.firstCandidateId},
             #{row.lastCandidateId}, #{row.candidateCount}, #{row.projectableCandidateCount},
             #{row.sourceFingerprint}, #{row.resultFingerprint}, #{row.netSignedAmount},
             #{row.settlementBatchNo}, #{row.segmentStatus}, #{row.processedTime}, #{row.version},
             #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") SettlementReviewSegmentDO row);

    @Select("""
            SELECT * FROM settlement_review_segment
            WHERE review_order_no = #{reviewOrderNo}
            ORDER BY sequence_no ASC, id ASC
            """)
    List<SettlementReviewSegmentDO> selectByOrderNo(@Param("reviewOrderNo") String reviewOrderNo);

    @Select("""
            SELECT * FROM settlement_review_segment
            WHERE review_order_no = #{reviewOrderNo} AND segment_status = 'LOCKED'
            ORDER BY sequence_no ASC, id ASC LIMIT 1 FOR UPDATE
            """)
    SettlementReviewSegmentDO selectNextLockedForUpdate(@Param("reviewOrderNo") String reviewOrderNo);

    @Select("SELECT COUNT(1) FROM settlement_review_segment WHERE review_order_no = #{reviewOrderNo}")
    int countByOrderNo(@Param("reviewOrderNo") String reviewOrderNo);

    @Select("""
            SELECT COUNT(1) FROM settlement_review_segment
            WHERE review_order_no = #{reviewOrderNo} AND segment_status = #{segmentStatus}
            """)
    int countByOrderNoAndStatus(@Param("reviewOrderNo") String reviewOrderNo,
                                @Param("segmentStatus") String segmentStatus);

    @Update("""
            UPDATE settlement_review_segment
            SET segment_status = 'RELEASED', processed_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE segment_no = #{segmentNo} AND segment_status = 'LOCKED' AND version = #{version}
            """)
    int markReleased(@Param("segmentNo") String segmentNo,
                     @Param("version") long version,
                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE settlement_review_segment
            SET segment_status = 'CONSUMED', settlement_batch_no = #{settlementBatchNo},
                processed_time = #{now}, version = version + 1, update_time = #{now}
            WHERE segment_no = #{segmentNo} AND segment_status = 'LOCKED' AND version = #{version}
            """)
    int markConsumed(@Param("segmentNo") String segmentNo,
                     @Param("version") long version,
                     @Param("settlementBatchNo") String settlementBatchNo,
                     @Param("now") LocalDateTime now);
}
