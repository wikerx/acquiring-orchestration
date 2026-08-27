package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.dto.SettlementClearingLocator;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementTransactionClearingDetailDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementClearingFactMapper
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 结算清分事实批量只读 Mapper；每个分片 OR 分支固定携带交易号、精确时间和清分修订号。
 * @status : create
 */
public interface SettlementClearingFactMapper {

    /** 按不可删除审计关系读取本批全部已认领候选。 */
    @Select("""
            SELECT candidate.*
            FROM settlement_batch_candidate relation
            INNER JOIN settlement_candidate candidate
                    ON candidate.id = relation.candidate_id
                   AND candidate.settlement_batch_no = relation.settlement_batch_no
                   AND candidate.candidate_status = 'CLAIMED'
            WHERE relation.settlement_batch_no = #{settlementBatchNo}
              AND relation.relation_status = 'CLAIMED'
            ORDER BY candidate.id ASC
            """)
    List<SettlementCandidateDO> selectClaimedCandidates(
            @Param("settlementBatchNo") String settlementBatchNo);

    /** 在一个 SQL 中精确读取全部交易清分修订，禁止按交易号逐条查询。 */
    @Select("""
            <script>
            SELECT *
            FROM transaction_clearing_detail
            WHERE record_status = 'ACTIVE'
              AND (
                <foreach collection="locators" item="locator" separator=" OR ">
                    (transaction_id = #{locator.transactionId}
                     AND transaction_date_time = #{locator.transactionDateTime}
                     AND clearing_revision = #{locator.clearingRevision})
                </foreach>
              )
            ORDER BY transaction_date_time ASC, transaction_id ASC, clearing_revision ASC, line_no ASC, id ASC
            </script>
            """)
    List<SettlementTransactionClearingDetailDO> selectTransactionDetails(
            @Param("locators") List<SettlementClearingLocator> locators);

    /** 在一个 SQL 中精确读取全部保证金清分修订，禁止按交易号逐条查询。 */
    @Select("""
            <script>
            SELECT *
            FROM transaction_reserve_clearing_detail
            WHERE record_status = 'ACTIVE'
              AND (
                <foreach collection="locators" item="locator" separator=" OR ">
                    (transaction_id = #{locator.transactionId}
                     AND transaction_date_time = #{locator.transactionDateTime}
                     AND clearing_revision = #{locator.clearingRevision})
                </foreach>
              )
            ORDER BY transaction_date_time ASC, transaction_id ASC, clearing_revision ASC, line_no ASC, id ASC
            </script>
            """)
    List<SettlementReserveClearingDetailDO> selectReserveDetails(
            @Param("locators") List<SettlementClearingLocator> locators);
}
