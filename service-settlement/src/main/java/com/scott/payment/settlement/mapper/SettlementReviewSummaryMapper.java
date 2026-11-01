package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewSummaryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 预审计算汇总只追加数据访问接口；金额按来源和目标币种维度保存，禁止数据库层跨币种聚合。
 */
public interface SettlementReviewSummaryMapper {

    /**
     * 批量追加预审不可变金额汇总，业务维度唯一冲突保持原行不变。
     * @param rows 已按支付、结果、方向和币种分组的汇总行
     * @return 实际插入行数，调用方必须与集合大小核对
     */
    @Insert("""
            <script>
            INSERT INTO settlement_review_summary
            (review_order_no, merchant_id, payment_type, payment_method, transaction_type,
             result_item_type, fee_category, direction, source_currency, target_currency,
             transaction_count, source_amount, target_amount, create_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.reviewOrderNo}, #{row.merchantId}, #{row.paymentType}, #{row.paymentMethod},
                 #{row.transactionType}, #{row.resultItemType}, #{row.feeCategory}, #{row.direction},
                 #{row.sourceCurrency}, #{row.targetCurrency}, #{row.transactionCount},
                 #{row.sourceAmount}, #{row.targetAmount}, #{row.createTime})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertBatchIdempotent(@Param("rows") List<SettlementReviewSummaryDO> rows);

    /**
     * 创建批次，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 结算服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Insert("""
            <script>
            INSERT INTO settlement_review_summary
            (review_order_no, merchant_id, payment_type, payment_method, transaction_type,
             result_item_type, fee_category, direction, source_currency, target_currency,
             transaction_count, source_amount, target_amount, create_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.reviewOrderNo}, #{row.merchantId}, #{row.paymentType}, #{row.paymentMethod},
                 #{row.transactionType}, #{row.resultItemType}, #{row.feeCategory}, #{row.direction},
                 #{row.sourceCurrency}, #{row.targetCurrency}, #{row.transactionCount},
                 #{row.sourceAmount}, #{row.targetAmount}, #{row.createTime})
            </foreach>
            ON DUPLICATE KEY UPDATE
                transaction_count = transaction_count + VALUES(transaction_count),
                source_amount = source_amount + VALUES(source_amount),
                target_amount = target_amount + VALUES(target_amount)
            </script>
            """)
    int addBatch(@Param("rows") List<SettlementReviewSummaryDO> rows);

    /**
     * 读取预审单全部金额汇总供审批展示和复核。
     * @param reviewOrderNo 结算预审单号
     * @return 不跨币种合并的汇总行集合
     */
    @Select("""
            SELECT *
            FROM settlement_review_summary
            WHERE review_order_no = #{reviewOrderNo}
            ORDER BY id ASC
            """)
    List<SettlementReviewSummaryDO> selectByOrderNo(@Param("reviewOrderNo") String reviewOrderNo);
}
