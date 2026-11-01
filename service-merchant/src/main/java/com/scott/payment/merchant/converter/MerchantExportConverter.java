package com.scott.payment.merchant.converter;

import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundLedgerResponse;
import com.scott.payment.merchant.dto.export.MerchantFundLedgerExportRow;
import com.scott.payment.merchant.dto.export.MerchantRefundExportRow;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundRecord;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * Merchant Export Converter 转换组件，位于 商户后台服务，在接口模型、领域对象、数据库记录或消息载荷之间复制并规范化字段。
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MerchantExportConverter {

    MerchantExportConverter INSTANCE = Mappers.getMapper(MerchantExportConverter.class);

    /**
     * 构造{@code toLedgerExportRow}对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 转换过程不改变来源对象的业务状态；敏感字段仅保留目标模型所需的最小集合。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "ledgerNo", source = "ledgerNo")
    @Mapping(target = "businessType", source = "businessType")
    @Mapping(target = "summary", source = "summary")
    @Mapping(target = "businessNo", source = "businessNo")
    @Mapping(target = "balanceType", source = "balanceType")
    @Mapping(target = "direction", source = "direction")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "balanceBefore", source = "balanceBefore")
    @Mapping(target = "balanceAfter", source = "balanceAfter")
    @Mapping(target = "operatorName", source = "operatorName")
    @Mapping(target = "reviewerName", source = "reviewerName")
    @Mapping(target = "postedTime", source = "postedTime")
    MerchantFundLedgerExportRow toLedgerExportRow(FundLedgerResponse source);

    /**
     * 构造{@code toRefundExportRow}对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 转换过程不改变来源对象的业务状态；敏感字段仅保留目标模型所需的最小集合。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "refundTransactionId", source = "refundTransactionId")
    @Mapping(target = "sourceTransactionId", source = "sourceTransactionId")
    @Mapping(target = "merchantOrderNo", source = "merchantOrderNo")
    @Mapping(target = "transactionType", source = "transactionType")
    @Mapping(target = "refundScope", source = "refundScope")
    @Mapping(target = "transactionAmount", source = "transactionAmount")
    @Mapping(target = "transactionCurrency", source = "transactionCurrency")
    @Mapping(target = "transactionStatus", source = "transactionStatus")
    @Mapping(target = "approvalStatus", source = "approvalStatus")
    @Mapping(target = "merchantVisibleMessage", source = "merchantVisibleMessage")
    @Mapping(target = "paymentMethod", source = "paymentMethod")
    @Mapping(target = "transactionDateTime", source = "transactionDateTime")
    @Mapping(target = "completeTime", source = "completeTime")
    MerchantRefundExportRow toRefundExportRow(RefundRecord source);
}
