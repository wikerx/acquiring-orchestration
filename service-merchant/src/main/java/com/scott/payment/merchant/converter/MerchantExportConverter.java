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

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MerchantExportConverter {

    MerchantExportConverter INSTANCE = Mappers.getMapper(MerchantExportConverter.class);

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
