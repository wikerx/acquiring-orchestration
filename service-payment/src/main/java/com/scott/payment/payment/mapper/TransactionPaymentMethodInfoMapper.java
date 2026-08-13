package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionPaymentMethodInfoDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionPaymentMethodInfoMapper
 * @date : 2026-07-15 14:31
 * @email : scott_x@163.com
 * @description : 交易支付工具摘要 Mapper，位于 service-payment 数据访问层，仅访问 transaction_payment_method_info 逻辑表。
 * @status : create
 */
public interface TransactionPaymentMethodInfoMapper extends BaseMapper<TransactionPaymentMethodInfoDO> {

    /**
     * 写入支付工具摘要逻辑表。
     *
     * @param infoDO 支付工具摘要
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_payment_method_info
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
    int insertLogical(@Param("infoDO") TransactionPaymentMethodInfoDO infoDO);

    /**
     * 写入交易级 3DS 标识。REQUIRED 可在策略命中时写入，认证成功后的 ECI 可以覆盖 REQUIRED。
     */
    @Update("""
            UPDATE transaction_payment_method_info
            SET three_ds_indicator = #{indicator},
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND (three_ds_indicator IS NULL
                   OR three_ds_indicator = ''
                   OR three_ds_indicator = 'REQUIRED'
                   OR three_ds_indicator = #{indicator})
            """)
    int updateThreeDsIndicator(@Param("transactionId") String transactionId,
                               @Param("transactionDateTime") LocalDateTime transactionDateTime,
                               @Param("indicator") String indicator,
                               @Param("now") LocalDateTime now);

    /**
     * 按生命周期和半开时间范围查询支付工具摘要。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 支付工具摘要列表
     */
    @Select("""
            SELECT *
            FROM transaction_payment_method_info
            WHERE operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
            ORDER BY transaction_date_time ASC, id ASC
            LIMIT 50
            """)
    List<TransactionPaymentMethodInfoDO> selectByOperationId(@Param("operationId") String operationId,
                                                             @Param("beginTime") LocalDateTime beginTime,
                                                             @Param("endTimeExclusive") LocalDateTime endTimeExclusive);
}
