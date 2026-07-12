package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsRequestMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 请求映射器骨架，位于 payment-channel-library 渠道实现层，后续按官方文档将平台统一请求映射为 MPGS 请求。
 * @status : create
 */
@Component
public class MpgsRequestMapper {

    /**
     * 构建 MPGS 请求体。
     *
     * @param request 渠道统一请求
     * @return MPGS 请求字段
     */
    public MpgsRequestPayload toMpgsRequest(ChannelPaymentRequest request) {
        validateCommonRequest(request);
        MpgsRequestPayload payload = new MpgsRequestPayload();
        String transactionType = normalizeType(request.getTransactionType());
        payload.setApiOperation(toApiOperation(transactionType));
        if (requiresOrderAmount(transactionType)) {
            payload.setOrder(order(request));
        } else if (ChannelCapability.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType)) {
            MpgsRequestPayload.Order order = new MpgsRequestPayload.Order();
            order.setReference(request.getMerchantOrderNo());
            payload.setOrder(order);
        }
        payload.setTransaction(transaction(request, transactionType));
        if (requiresCard(request, transactionType)) {
            payload.setSourceOfFunds(sourceOfFunds(request));
        }
        if (requiresCard(request, transactionType) && request.getThreeDsInfo() != null) {
            payload.setAuthentication(authentication(request.getThreeDsInfo()));
        }
        return payload;
    }

    private void validateCommonRequest(ChannelPaymentRequest request) {
        if (request == null) {
            throw new ChannelRequestException("MPGS请求不能为空");
        }
        requireText(request.getMerchantOrderNo(), "MPGS merchantOrderNo不能为空");
        requireText(request.getTransactionNo(), "MPGS transactionNo不能为空");
        requireText(request.getTransactionType(), "MPGS transactionType不能为空");
    }

    private String toApiOperation(String transactionType) {
        if (ChannelCapability.PAYMENT.getCode().equals(transactionType)) {
            return MpgsApiOperation.PAY;
        }
        if (ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType)) {
            return MpgsApiOperation.AUTHORIZE;
        }
        if (ChannelCapability.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType)) {
            return MpgsApiOperation.UPDATE_AUTHORIZATION;
        }
        if (ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)) {
            return MpgsApiOperation.CAPTURE;
        }
        if (ChannelCapability.REFUND.getCode().equals(transactionType)) {
            return MpgsApiOperation.REFUND;
        }
        if (ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            return MpgsApiOperation.VOID;
        }
        throw new ChannelRequestException("MPGS暂不支持交易类型：" + transactionType);
    }

    private boolean requiresOrderAmount(String transactionType) {
        return ChannelCapability.PAYMENT.getCode().equals(transactionType)
                || ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType);
    }

    private boolean requiresCard(ChannelPaymentRequest request, String transactionType) {
        if (!(ChannelCapability.PAYMENT.getCode().equals(transactionType)
                || ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType))) {
            return false;
        }
        requireText(request.getCardNo(), "MPGS卡号不能为空");
        requireText(request.getExpirationMonth(), "MPGS卡有效期月份不能为空");
        requireText(request.getExpirationYear(), "MPGS卡有效期年份不能为空");
        requireText(request.getSecurityCode(), "MPGS卡安全码不能为空");
        return true;
    }

    private MpgsRequestPayload.Order order(ChannelPaymentRequest request) {
        MpgsRequestPayload.Order order = new MpgsRequestPayload.Order();
        order.setAmount(amount(request.getAmount()));
        order.setCurrency(currency(request.getCurrency()));
        order.setReference(request.getMerchantOrderNo());
        return order;
    }

    private MpgsRequestPayload.Transaction transaction(ChannelPaymentRequest request, String transactionType) {
        MpgsRequestPayload.Transaction transaction = new MpgsRequestPayload.Transaction();
        if (requiresTransactionAmount(transactionType)) {
            transaction.setAmount(amount(request.getAmount()));
            transaction.setCurrency(currency(request.getCurrency()));
        }
        transaction.setReference(request.getTransactionNo());
        if (ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            transaction.setTargetTransactionId(requiredTargetTransactionId(request));
        }
        return transaction;
    }

    private boolean requiresTransactionAmount(String transactionType) {
        return ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)
                || ChannelCapability.REFUND.getCode().equals(transactionType)
                || ChannelCapability.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType);
    }

    private MpgsRequestPayload.SourceOfFunds sourceOfFunds(ChannelPaymentRequest request) {
        MpgsRequestPayload.Expiry expiry = new MpgsRequestPayload.Expiry();
        expiry.setMonth(normalizeMonth(request.getExpirationMonth()));
        expiry.setYear(normalizeYear(request.getExpirationYear()));

        MpgsRequestPayload.Card card = new MpgsRequestPayload.Card();
        card.setNumber(request.getCardNo());
        card.setExpiry(expiry);
        card.setSecurityCode(request.getSecurityCode());

        MpgsRequestPayload.Provided provided = new MpgsRequestPayload.Provided();
        provided.setCard(card);

        MpgsRequestPayload.SourceOfFunds sourceOfFunds = new MpgsRequestPayload.SourceOfFunds();
        sourceOfFunds.setType(MpgsApiOperation.CARD);
        sourceOfFunds.setProvided(provided);
        return sourceOfFunds;
    }

    private MpgsRequestPayload.Authentication authentication(ChannelPaymentRequest.ThreeDsInfo source) {
        MpgsRequestPayload.ThreeDs threeDs = new MpgsRequestPayload.ThreeDs();
        threeDs.setTransactionId(source.getDsTransactionId());
        threeDs.setAcsEci(source.getEci());
        threeDs.setAuthenticationToken(source.getCavv());

        MpgsRequestPayload.Authentication authentication = new MpgsRequestPayload.Authentication();
        authentication.setThreeDs(threeDs);
        if ("3DS1".equalsIgnoreCase(source.getThreeDsVersion())) {
            MpgsRequestPayload.ThreeDs1 threeDs1 = new MpgsRequestPayload.ThreeDs1();
            threeDs1.setPaResStatus("Y");
            threeDs1.setVeResEnrolled("Y");
            authentication.setThreeDs1(threeDs1);
        } else {
            MpgsRequestPayload.ThreeDs2 threeDs2 = new MpgsRequestPayload.ThreeDs2();
            threeDs2.setTransactionStatus("Y");
            authentication.setThreeDs2(threeDs2);
        }
        return authentication;
    }

    private String requiredTargetTransactionId(ChannelPaymentRequest request) {
        String targetTransactionId = request.getOriginalTransactionNo();
        if (!StringUtils.hasText(targetTransactionId) && request.getExtension() != null) {
            targetTransactionId = request.getExtension().get("targetTransactionId");
        }
        requireText(targetTransactionId, "MPGS撤销交易目标交易号不能为空");
        return targetTransactionId;
    }

    private String amount(BigDecimal amount) {
        if (amount == null) {
            throw new ChannelRequestException("MPGS交易金额不能为空");
        }
        if (amount.signum() <= 0) {
            throw new ChannelRequestException("MPGS交易金额必须大于0");
        }
        return amount.setScale(Math.max(amount.scale(), 2), RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString();
    }

    private String currency(String currency) {
        requireText(currency, "MPGS交易币种不能为空");
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeMonth(String month) {
        requireText(month, "MPGS卡有效期月份不能为空");
        String normalized = month.trim();
        return normalized.length() == 1 ? "0" + normalized : normalized;
    }

    private String normalizeYear(String year) {
        requireText(year, "MPGS卡有效期年份不能为空");
        String normalized = year.trim();
        if (normalized.length() == 4) {
            return normalized.substring(2);
        }
        return normalized;
    }

    private String normalizeType(String transactionType) {
        return transactionType.trim().toUpperCase(Locale.ROOT);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ChannelRequestException(message);
        }
    }
}
