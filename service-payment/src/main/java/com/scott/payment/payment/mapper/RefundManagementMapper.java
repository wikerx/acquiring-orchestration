package com.scott.payment.payment.mapper;

import com.scott.payment.payment.mapper.provider.RefundManagementSqlProvider;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundCurrencySummary;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundQuery;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundRecord;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundStatusSummaryRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundManagementMapper
 * @date : 2026-08-06 15:40
 * @email : scott_x@163.com
 * @description : 退款管理只读 Mapper，以 REFUND/VOID 动作为事实源并关联普通审批表派生审批状态。
 * @status : create
 */
public interface RefundManagementMapper {

    /** 查询退款列表总数。 */
    @SelectProvider(type = RefundManagementSqlProvider.class, method = "countSql")
    long count(@Param("query") RefundQuery query,
               @Param("beginTime") LocalDateTime beginTime,
               @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /** 分页查询退款/撤销动作。 */
    @SelectProvider(type = RefundManagementSqlProvider.class, method = "pageSql")
    List<RefundRecord> selectPage(@Param("query") RefundQuery query,
                                  @Param("beginTime") LocalDateTime beginTime,
                                  @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
                                  @Param("offset") long offset,
                                  @Param("limit") long limit);

    /** 查询退款状态统计。 */
    @SelectProvider(type = RefundManagementSqlProvider.class, method = "statusSummarySql")
    RefundStatusSummaryRow selectStatusSummary(@Param("query") RefundQuery query,
                                               @Param("beginTime") LocalDateTime beginTime,
                                               @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /** 按交易币种统计成功金额和非终态占用金额。 */
    @SelectProvider(type = RefundManagementSqlProvider.class, method = "currencySummarySql")
    List<RefundCurrencySummary> selectCurrencySummary(@Param("query") RefundQuery query,
                                                      @Param("beginTime") LocalDateTime beginTime,
                                                      @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /** 使用真实退款分片时间查询单笔退款。 */
    @SelectProvider(type = RefundManagementSqlProvider.class, method = "detailSql")
    RefundRecord selectOne(@Param("transactionId") String transactionId,
                           @Param("transactionDateTime") LocalDateTime transactionDateTime,
                           @Param("merchantId") String merchantId);
}
