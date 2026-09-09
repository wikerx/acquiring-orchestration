package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.LocatorFacts;
import com.scott.payment.clearing.entity.ClearingPaymentMethodInfoDO;
import com.scott.payment.clearing.entity.ClearingTransactionLocatorDO;
import com.scott.payment.clearing.entity.ClearingTransactionOrderDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionContextMapper
 * @date : 2026-08-26 10:40
 * @email : scott_x@163.com
 * @description : 清分定位、支付维度和生命周期投影 Mapper；分表访问均使用真实 transaction_date_time 精确路由。
 * @status : create
 */
public interface ClearingTransactionContextMapper {

    /** 按商户和动作号读取非分表 locator。 */
    @Select("""
            SELECT id, transaction_id, operation_id, root_transaction_id, merchant_id,
                   merchant_order_no, transaction_type, transaction_date_time, root_transaction_date_time
            FROM transaction_locator
            WHERE merchant_id = #{merchantId}
              AND transaction_id = #{transactionId}
            LIMIT 1
            """)
    ClearingTransactionLocatorDO selectLocator(@Param("merchantId") String merchantId,
                                                @Param("transactionId") String transactionId);

    /** 读取生命周期中已建立定位记录的退款动作，供阶段B按每笔真实分片时间查询累计返还事实。 */
    @Select("""
            SELECT id, transaction_id, operation_id, root_transaction_id, merchant_id,
                   merchant_order_no, transaction_type, transaction_date_time, root_transaction_date_time
            FROM transaction_locator
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
              AND transaction_type = 'REFUND'
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<ClearingTransactionLocatorDO> selectRefundLocators(@Param("merchantId") String merchantId,
                                                            @Param("operationId") String operationId);

    /** 读取当前生命周期全部定位记录，供阶段 B 使用每笔真实分片时间重算主单清分投影。 */
    @Select("""
            SELECT id, transaction_id, operation_id, root_transaction_id, merchant_id,
                   merchant_order_no, transaction_type, transaction_date_time, root_transaction_date_time
            FROM transaction_locator
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<ClearingTransactionLocatorDO> selectOperationLocators(@Param("merchantId") String merchantId,
                                                               @Param("operationId") String operationId);

    /** 按根交易号和根分片时间读取支付方式、品牌与3DS标识，不读取卡号或有效期。 */
    @Select("""
            SELECT id, transaction_id, operation_id, payment_method, payment_brand,
                   three_ds_indicator, transaction_date_time
            FROM transaction_payment_method_info
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            ORDER BY id DESC
            LIMIT 1
            """)
    ClearingPaymentMethodInfoDO selectPaymentMethod(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /** 判断当前动作是否实际执行过内部风险检查。 */
    @Select("""
            SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END
            FROM transaction_flow_event
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND event_type = 'RISK_CHECKED'
            """)
    boolean existsInternalRiskCall(@Param("transactionId") String transactionId,
                                   @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /** 在阶段B锁定根主单，仅用于读取累计退款事实和更新清分聚合投影。 */
    @Select("""
            SELECT id, operation_id, merchant_id, transaction_currency, transaction_amount,
                   refunded_amount, clearing_status, transaction_date_time, version
            FROM transaction_order
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    ClearingTransactionOrderDO selectOrderForUpdate(@Param("operationId") String operationId,
                                                     @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /** 使用根分片时间和版本 CAS 更新生命周期清分聚合投影，禁止修改交易、结算或余额状态。 */
    @Update("""
            UPDATE transaction_order
            SET clearing_status = #{clearingStatus},
                clearing_complete_time = #{completeTime},
                version = version + 1,
                update_time = #{completeTime}
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int updateOrderClearingProjection(@Param("operationId") String operationId,
                                      @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                      @Param("expectedVersion") int expectedVersion,
                                      @Param("clearingStatus") String clearingStatus,
                                      @Param("completeTime") LocalDateTime completeTime);

    /** 查询生命周期每个已定位动作的清分投影；每个 OR 条件都包含动作真实分片时间。 */
    @Select("""
            <script>
            SELECT clearing_status
            FROM transaction_operation
            WHERE deleted = 0
              AND (
                <foreach collection='locators' item='locator' separator=' OR '>
                  (transaction_id = #{locator.transactionId}
                   AND transaction_date_time = #{locator.transactionDateTime})
                </foreach>
              )
            </script>
            """)
    List<String> selectOperationClearingStatuses(@Param("locators") List<LocatorFacts> locators);
}
