package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewSegmentDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大批量预审单的有界处理分段数据访问。
 */
public interface SettlementReviewSegmentMapper {

    /**
     * 创建{@code insertIdempotent}，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 结算服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 写入、更新或删除后的处理结果
     */
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

    /**
     * 查询按订单编号；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param reviewOrderNo 结算预审单号或冲正单号，用于定位唯一业务单据
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT * FROM settlement_review_segment
            WHERE review_order_no = #{reviewOrderNo}
            ORDER BY sequence_no ASC, id ASC
            """)
    List<SettlementReviewSegmentDO> selectByOrderNo(@Param("reviewOrderNo") String reviewOrderNo);

    /**
     * 查询{@code selectNextLockedForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param reviewOrderNo 结算预审单号或冲正单号，用于定位唯一业务单据
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT * FROM settlement_review_segment
            WHERE review_order_no = #{reviewOrderNo} AND segment_status = 'LOCKED'
            ORDER BY sequence_no ASC, id ASC LIMIT 1 FOR UPDATE
            """)
    SettlementReviewSegmentDO selectNextLockedForUpdate(@Param("reviewOrderNo") String reviewOrderNo);

    /**
     * 统计按订单编号，返回分页、扫描或报表汇总所需的数量结果。
     * @param reviewOrderNo 结算预审单号或冲正单号，用于定位唯一业务单据
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Select("SELECT COUNT(1) FROM settlement_review_segment WHERE review_order_no = #{reviewOrderNo}")
    int countByOrderNo(@Param("reviewOrderNo") String reviewOrderNo);

    /**
     * 统计{@code countByOrderNoAndStatus}，返回分页、扫描或报表汇总所需的数量结果。
     * @param reviewOrderNo 结算预审单号或冲正单号，用于定位唯一业务单据
     * @param segmentStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Select("""
            SELECT COUNT(1) FROM settlement_review_segment
            WHERE review_order_no = #{reviewOrderNo} AND segment_status = #{segmentStatus}
            """)
    int countByOrderNoAndStatus(@Param("reviewOrderNo") String reviewOrderNo,
                                @Param("segmentStatus") String segmentStatus);

    /**
     * 以乐观锁将预审分段标记为已释放，记录拒绝或取消决策事实。
     * @param segmentNo 待释放的稳定预审分段号
     * @param version 乐观锁预期版本，防止重复释放覆盖
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_review_segment
            SET segment_status = 'RELEASED', processed_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE segment_no = #{segmentNo} AND segment_status = 'LOCKED' AND version = #{version}
            """)
    int markReleased(@Param("segmentNo") String segmentNo,
                     @Param("version") long version,
                     @Param("now") LocalDateTime now);

    /**
     * 以乐观锁将预审分段标记为已消费，并绑定唯一正式结算批次。
     * @param segmentNo 待消费的稳定预审分段号
     * @param version 乐观锁预期版本，防止重复转换批次
     * @param settlementBatchNo 该分段生成的正式结算批次号
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
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
