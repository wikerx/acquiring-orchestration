package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementBatchCandidateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchCandidateMapper
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 批次候选审计关系 Mapper；只追加、锁读和受控状态迁移，不提供物理删除或任意覆盖接口。
 * @status : create
 */
public interface SettlementBatchCandidateMapper {

    /** 批量追加自动认领关系；候选 CAS 首次成功时每行都必须实际插入。 */
    @Insert("""
            <script>
            INSERT INTO settlement_batch_candidate
            (batch_candidate_no, settlement_batch_no, candidate_id, source_type,
             source_business_id, source_revision, relation_status, claimed_time,
             version, create_time, update_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.batchCandidateNo}, #{row.settlementBatchNo}, #{row.candidateId}, #{row.sourceType},
                 #{row.sourceBusinessId}, #{row.sourceRevision}, #{row.relationStatus}, #{row.claimedTime},
                 #{row.version}, #{row.createTime}, #{row.updateTime})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertBatchIdempotent(@Param("rows") List<SettlementBatchCandidateDO> rows);

    /** 按关系号和批次候选唯一键幂等追加，调用方随后必须回读校验身份。 */
    @Insert("""
            INSERT INTO settlement_batch_candidate
            (batch_candidate_no, settlement_batch_no, candidate_id, source_type,
             source_business_id, source_revision, relation_status, claimed_time,
             version, create_time, update_time)
            VALUES
            (#{row.batchCandidateNo}, #{row.settlementBatchNo}, #{row.candidateId}, #{row.sourceType},
             #{row.sourceBusinessId}, #{row.sourceRevision}, #{row.relationStatus}, #{row.claimedTime},
             #{row.version}, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") SettlementBatchCandidateDO row);

    /** 锁定指定批次和候选的唯一审计关系。 */
    @Select("""
            SELECT *
            FROM settlement_batch_candidate
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND candidate_id = #{candidateId}
            LIMIT 1
            FOR UPDATE
            """)
    SettlementBatchCandidateDO selectByBatchAndCandidateForUpdate(
            @Param("settlementBatchNo") String settlementBatchNo,
            @Param("candidateId") Long candidateId);

    /** 统计批次冻结关系中的真实交易候选；保证金释放和调整不参与交易状态投影。 */
    @Select("""
            SELECT COUNT(1)
            FROM settlement_batch_candidate
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND source_type = 'CLEARING_REVISION'
            """)
    int countProjectableCandidates(@Param("settlementBatchNo") String settlementBatchNo);

    /** 批次人工复核时迁移仍为 CLAIMED 的审计关系，禁止覆盖 POSTED 或 RELEASED。 */
    @Update("""
            UPDATE settlement_batch_candidate
            SET relation_status = 'MANUAL_REVIEW',
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND relation_status = 'CLAIMED'
            """)
    int markBatchManualReview(@Param("settlementBatchNo") String settlementBatchNo,
                              @Param("now") java.time.LocalDateTime now);

    /** 候选入账后迁移仍为 CLAIMED 的审计关系，禁止覆盖已释放或人工复核关系。 */
    @Update("""
            UPDATE settlement_batch_candidate
            SET relation_status = 'POSTED',
                posted_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND relation_status = 'CLAIMED'
            """)
    int markBatchPosted(@Param("settlementBatchNo") String settlementBatchNo,
                        @Param("now") java.time.LocalDateTime now);

    /** 入账前取消后迁移关系为 RELEASED，不删除认领审计。 */
    @Update("""
            UPDATE settlement_batch_candidate
            SET relation_status = 'RELEASED',
                released_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND relation_status = 'CLAIMED'
            """)
    int releaseCancelledBatch(@Param("settlementBatchNo") String settlementBatchNo,
                              @Param("now") java.time.LocalDateTime now);
}
