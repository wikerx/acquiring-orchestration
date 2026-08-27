package com.scott.payment.settlement.dto;

import com.scott.payment.settlement.domain.model.SettlementBatchType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchCreateCommand
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算批次创建内部命令，冻结商户、账户、目标币种、结算配置和候选截止窗口。
 * @status : create
 * @param createRequestKey 调度或人工请求全局幂等键，最大 128 字符
 * @param businessDate 结算业务日期，不从批次号反向解析
 * @param businessTimeZone 结算日历 IANA 时区
 * @param merchantId 平台商户号
 * @param settlementProfileId 冻结的商户结算配置 ID
 * @param settlementAccountId 目标结算资金账户 ID
 * @param targetCurrency 目标结算 ISO 币种
 * @param targetCurrencyExponent 目标币种 ISO 小数位
 * @param batchType 批次类型
 * @param originalBatchNo 冲正或调整引用的原批次号
 * @param cutoffBeginTime 候选窗口闭区间起点
 * @param cutoffEndTime 候选窗口开区间终点
 */
public record SettlementBatchCreateCommand(String createRequestKey,
                                           LocalDate businessDate,
                                           String businessTimeZone,
                                           String merchantId,
                                           Long settlementProfileId,
                                           Long settlementAccountId,
                                           String targetCurrency,
                                           int targetCurrencyExponent,
                                           SettlementBatchType batchType,
                                           String originalBatchNo,
                                           LocalDateTime cutoffBeginTime,
                                           LocalDateTime cutoffEndTime) {

    public SettlementBatchCreateCommand {
        createRequestKey = requireText(createRequestKey, "create request key", 128);
        Objects.requireNonNull(businessDate, "business date is required");
        businessTimeZone = requireText(businessTimeZone, "business time zone", 64);
        ZoneId.of(businessTimeZone);
        merchantId = requireText(merchantId, "merchant id", 64);
        requirePositive(settlementProfileId, "settlement profile id");
        requirePositive(settlementAccountId, "settlement account id");
        targetCurrency = requireText(targetCurrency, "target currency", 3).toUpperCase(Locale.ROOT);
        if (targetCurrency.length() != 3 || targetCurrencyExponent < 0 || targetCurrencyExponent > 8) {
            throw new IllegalArgumentException("target currency or exponent is invalid");
        }
        Objects.requireNonNull(batchType, "batch type is required");
        originalBatchNo = normalizeOptional(originalBatchNo);
        if (batchType.isOriginalBatchRequired() != (originalBatchNo != null)) {
            throw new IllegalArgumentException("original batch identity does not match batch type");
        }
        Objects.requireNonNull(cutoffBeginTime, "cutoff begin time is required");
        Objects.requireNonNull(cutoffEndTime, "cutoff end time is required");
        if (!cutoffEndTime.isAfter(cutoffBeginTime)) {
            throw new IllegalArgumentException("cutoff end time must be after begin time");
        }
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
