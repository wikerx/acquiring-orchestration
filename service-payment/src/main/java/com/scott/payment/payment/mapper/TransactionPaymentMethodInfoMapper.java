package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionPaymentMethodInfoDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionPaymentMethodInfoMapper
 * @date : 2026-07-15 14:31
 * @email : scott_x@163.com
 * @description : 交易支付工具摘要 Mapper，位于 service-payment 数据访问层，仅负责 transaction_payment_method_info 逻辑表及物理分表访问。
 * @status : create
 */
public interface TransactionPaymentMethodInfoMapper extends BaseMapper<TransactionPaymentMethodInfoDO> {

    /**
     * 写入交易支付工具摘要物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param infoDO            支付工具摘要
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
            (
              payment_info_id, transaction_id, operation_id, payment_method, payment_brand,
              card_bin, card_last4, card_number_masked, cardholder_name_masked, expiry_month,
              expiry_year, token_id, wallet_type, payment_account_hash, issuer_country,
              funding_method, three_ds_indicator, csc_result, avs_result, transaction_date_time,
              transaction_utc_time, transaction_time_zone, create_time, update_time
            )
            VALUES
            (
              #{infoDO.paymentInfoId}, #{infoDO.transactionId}, #{infoDO.operationId},
              #{infoDO.paymentMethod}, #{infoDO.paymentBrand}, #{infoDO.cardBin},
              #{infoDO.cardLast4}, #{infoDO.cardNumberMasked}, #{infoDO.cardholderNameMasked},
              #{infoDO.expiryMonth}, #{infoDO.expiryYear}, #{infoDO.tokenId}, #{infoDO.walletType},
              #{infoDO.paymentAccountHash}, #{infoDO.issuerCountry}, #{infoDO.fundingMethod},
              #{infoDO.threeDsIndicator}, #{infoDO.cscResult}, #{infoDO.avsResult},
              #{infoDO.transactionDateTime}, #{infoDO.transactionUtcTime}, #{infoDO.transactionTimeZone},
              #{infoDO.createTime}, #{infoDO.updateTime}
            )
            """)
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       @Param("infoDO") TransactionPaymentMethodInfoDO infoDO);

    /**
     * 按一组平台交易 ID 批量查询支付工具摘要。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionIds    平台交易 ID 集合
     * @return 支付工具摘要列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id IN
            <foreach collection="transactionIds" item="transactionId" open="(" separator="," close=")">
              #{transactionId}
            </foreach>
            </script>
            """)
    List<TransactionPaymentMethodInfoDO> selectByTransactionIdsPhysical(@Param("physicalTableName") String physicalTableName,
                                                                        @Param("transactionIds") Collection<String> transactionIds);

    /**
     * 按内部生命周期关联标识查询支付工具摘要。
     * <p>
     * 后续动作可能不再携带卡号，后台列表需要回退到同一生命周期首笔交易的支付工具摘要展示卡品牌和 BIN。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 支付工具摘要列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
            ORDER BY transaction_date_time ASC, id ASC
            LIMIT 50
            """)
    List<TransactionPaymentMethodInfoDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                     @Param("operationId") String operationId);
}
