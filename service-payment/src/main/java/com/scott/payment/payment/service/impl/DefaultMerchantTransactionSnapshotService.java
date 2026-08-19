package com.scott.payment.payment.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionBillingInfoDO;
import com.scott.payment.payment.entity.TransactionAuthenticationInfoDO;
import com.scott.payment.payment.entity.TransactionMerchantSnapshotDO;
import com.scott.payment.payment.entity.TransactionPayerInfoDO;
import com.scott.payment.payment.entity.TransactionProductItemDO;
import com.scott.payment.payment.entity.TransactionShippingInfoDO;
import com.scott.payment.payment.mapper.TransactionBillingInfoMapper;
import com.scott.payment.payment.mapper.TransactionAuthenticationInfoMapper;
import com.scott.payment.payment.mapper.TransactionMerchantSnapshotMapper;
import com.scott.payment.payment.mapper.TransactionPayerInfoMapper;
import com.scott.payment.payment.mapper.TransactionProductItemMapper;
import com.scott.payment.payment.mapper.TransactionShippingInfoMapper;
import com.scott.payment.payment.service.MerchantTransactionSnapshotService;
import com.scott.payment.payment.service.dto.MerchantTransactionSnapshotDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantTransactionSnapshotService
 * @date : 2026-08-14 12:45
 * @email : scott_x@163.com
 * @description : 商户交易快照默认实现，明文保存商户可见的子商户、付款人、账单、收货和商品快照，并以交易分片键读取。
 * @status : create
 */
@Service
public class DefaultMerchantTransactionSnapshotService implements MerchantTransactionSnapshotService {

    private static final String TIME_ZONE = "Asia/Shanghai";
    private static final String BILLING_PREFIX = "TBI";
    private static final String MERCHANT_PREFIX = "TMS";
    private static final String PAYER_PREFIX = "TPI";
    private static final String SHIPPING_PREFIX = "TSI";
    private static final String PRODUCT_PREFIX = "TGI";

    private final TransactionBillingInfoMapper billingInfoMapper;
    private final TransactionAuthenticationInfoMapper authenticationInfoMapper;
    private final TransactionMerchantSnapshotMapper merchantSnapshotMapper;
    private final TransactionPayerInfoMapper payerInfoMapper;
    private final TransactionShippingInfoMapper shippingInfoMapper;
    private final TransactionProductItemMapper productItemMapper;

    /**
     * 创建商户交易快照服务。
     */
    public DefaultMerchantTransactionSnapshotService(TransactionBillingInfoMapper billingInfoMapper,
                                                     TransactionAuthenticationInfoMapper authenticationInfoMapper,
                                                     TransactionMerchantSnapshotMapper merchantSnapshotMapper,
                                                     TransactionPayerInfoMapper payerInfoMapper,
                                                     TransactionShippingInfoMapper shippingInfoMapper,
                                                     TransactionProductItemMapper productItemMapper) {
        this.billingInfoMapper = billingInfoMapper;
        this.authenticationInfoMapper = authenticationInfoMapper;
        this.merchantSnapshotMapper = merchantSnapshotMapper;
        this.payerInfoMapper = payerInfoMapper;
        this.shippingInfoMapper = shippingInfoMapper;
        this.productItemMapper = productItemMapper;
    }

    /**
     * 保存首次交易的商户可见请求快照。
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public void recordInitialSnapshots(PaymentCreateCommandDTO commandDTO,
                                       PaymentCreateResultDTO resultDTO,
                                       LocalDateTime now) {
        if (commandDTO == null || resultDTO == null
                || !StringUtils.hasText(resultDTO.getTransactionId())
                || commandDTO.getTransactionDateTime() == null) {
            return;
        }
        recordMerchant(commandDTO, resultDTO, now);
        recordBilling(commandDTO, resultDTO, now);
        recordPayer(commandDTO, resultDTO, now);
        recordShipping(commandDTO, resultDTO, now);
        recordGoods(commandDTO, resultDTO, now);
        recordMerchantThreeDs(commandDTO, resultDTO, now);
    }

    /**
     * 保存 Direct API 已提供的 3DS 安全摘要。CAVV 只用于当次渠道请求，不进入该商户查询事实表。
     */
    private void recordMerchantThreeDs(PaymentCreateCommandDTO commandDTO,
                                       PaymentCreateResultDTO resultDTO,
                                       LocalDateTime now) {
        PaymentCreateCommandDTO.ThreeDsInfoDTO source = commandDTO.getThreeDsInfo();
        if (source == null || (!StringUtils.hasText(source.getEci())
                && !StringUtils.hasText(source.getDsTransactionId())
                && !StringUtils.hasText(source.getThreeDsVersion()))) {
            return;
        }
        TransactionAuthenticationInfoDO target = new TransactionAuthenticationInfoDO();
        target.setAuthenticationInfoId(sha256(resultDTO.getTransactionId() + "|MERCHANT|3DS"));
        target.setTransactionId(resultDTO.getTransactionId());
        target.setOperationId(resultDTO.getOperationId());
        target.setAuthenticationType("3DS");
        target.setAuthenticationStatus("AUTHENTICATED");
        target.setAuthenticationSource("MERCHANT");
        target.setThreeDsVersion(source.getThreeDsVersion());
        target.setThreeDsTransactionId(source.getAuthenticationTransactionId());
        target.setDsTransactionId(source.getDsTransactionId());
        target.setEci(source.getEci());
        target.setCavv(null);
        target.setAuthenticationResultCode("MERCHANT_PROVIDED");
        target.setAuthenticationResultMessage("Merchant provided 3DS result");
        target.setAuthenticationTime(now);
        target.setTransactionDateTime(commandDTO.getTransactionDateTime());
        target.setTransactionUtcTime(toUtc(commandDTO.getTransactionDateTime()));
        target.setTransactionTimeZone(TIME_ZONE);
        target.setCreateTime(now);
        target.setUpdateTime(now);
        authenticationInfoMapper.upsertPhase(target);
    }

    /**
     * 读取生命周期根交易的商户可见请求快照。
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public MerchantTransactionSnapshotDTO loadSnapshots(String merchantId,
                                                         String rootTransactionId,
                                                         LocalDateTime rootTransactionDateTime) {
        MerchantTransactionSnapshotDTO snapshot = new MerchantTransactionSnapshotDTO();
        if (!StringUtils.hasText(merchantId)
                || !StringUtils.hasText(rootTransactionId)
                || rootTransactionDateTime == null) {
            return snapshot;
        }
        snapshot.setSubMerchantInfo(toSubMerchant(merchantSnapshotMapper.selectByTransaction(
                merchantId, rootTransactionId, rootTransactionDateTime)));
        snapshot.setBillingCardHolderInfo(toBilling(
                billingInfoMapper.selectByTransaction(rootTransactionId, rootTransactionDateTime)));
        snapshot.setShippingInfo(toShipping(
                shippingInfoMapper.selectByTransaction(rootTransactionId, rootTransactionDateTime)));
        snapshot.setPayerInfo(toPayer(
                payerInfoMapper.selectByTransaction(rootTransactionId, rootTransactionDateTime)));
        List<TransactionProductItemDO> items = productItemMapper.selectByTransaction(
                rootTransactionId, rootTransactionDateTime);
        snapshot.setGoodsInfo(items == null ? List.of() : items.stream().map(this::toGoods).toList());
        return snapshot;
    }

    private void recordMerchant(PaymentCreateCommandDTO commandDTO,
                                PaymentCreateResultDTO resultDTO,
                                LocalDateTime now) {
        TransactionMerchantSnapshotDO target = new TransactionMerchantSnapshotDO();
        target.setSnapshotId(PaymentOrderNoGenerator.nextOrderNo(
                MERCHANT_PREFIX, commandDTO.getTransactionDateTime()));
        target.setTransactionId(resultDTO.getTransactionId());
        target.setOperationId(resultDTO.getOperationId());
        target.setMerchantId(commandDTO.getMerchantId());
        if (commandDTO.getSubMerchantInfo() != null) {
            target.setSubMerchantInfoJson(JsonUtils.toJsonString(commandDTO.getSubMerchantInfo()));
        }
        target.setTransactionDateTime(commandDTO.getTransactionDateTime());
        target.setTransactionUtcTime(toUtc(commandDTO.getTransactionDateTime()));
        target.setTransactionTimeZone(TIME_ZONE);
        target.setCreateTime(now);
        target.setUpdateTime(now);
        merchantSnapshotMapper.insert(target);
    }

    private void recordBilling(PaymentCreateCommandDTO commandDTO,
                               PaymentCreateResultDTO resultDTO,
                               LocalDateTime now) {
        PaymentCreateCommandDTO.BillingCardHolderInfoDTO source = commandDTO.getBillingCardHolderInfo();
        if (source == null) {
            return;
        }
        TransactionBillingInfoDO target = new TransactionBillingInfoDO();
        fillIdentity(target, commandDTO, resultDTO, now);
        target.setBillingInfoId(PaymentOrderNoGenerator.nextOrderNo(BILLING_PREFIX, commandDTO.getTransactionDateTime()));
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        billingInfoMapper.insert(target);
    }

    private void recordPayer(PaymentCreateCommandDTO commandDTO,
                             PaymentCreateResultDTO resultDTO,
                             LocalDateTime now) {
        PaymentCreateCommandDTO.PayerInfoDTO source = commandDTO.getPayerInfo();
        if (source == null) {
            return;
        }
        TransactionPayerInfoDO target = new TransactionPayerInfoDO();
        fillIdentity(target, commandDTO, resultDTO, now);
        target.setPayerInfoId(PaymentOrderNoGenerator.nextOrderNo(PAYER_PREFIX, commandDTO.getTransactionDateTime()));
        target.setPayerId(source.getPayerId());
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setPhone(source.getPhone());
        target.setEmail(source.getEmail());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        target.setIpAddress(source.getIpAddress());
        target.setSessionId(source.getSessionId());
        target.setBrowserInfoJson(source.getBrowserInfo() == null
                ? null : JsonUtils.toJsonString(source.getBrowserInfo()));
        target.setUserAgent(source.getUserAgent());
        target.setPayerEmailHash(sha256(source.getEmail()));
        target.setPayerPhoneHash(sha256(source.getPhone()));
        target.setIpAddressHash(sha256(source.getIpAddress()));
        payerInfoMapper.insert(target);
    }

    private void recordShipping(PaymentCreateCommandDTO commandDTO,
                                PaymentCreateResultDTO resultDTO,
                                LocalDateTime now) {
        PaymentCreateCommandDTO.ShippingInfoDTO source = commandDTO.getShippingInfo();
        if (source == null) {
            return;
        }
        TransactionShippingInfoDO target = new TransactionShippingInfoDO();
        fillIdentity(target, commandDTO, resultDTO, now);
        target.setShippingInfoId(PaymentOrderNoGenerator.nextOrderNo(SHIPPING_PREFIX, commandDTO.getTransactionDateTime()));
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        shippingInfoMapper.insert(target);
    }

    private void recordGoods(PaymentCreateCommandDTO commandDTO,
                             PaymentCreateResultDTO resultDTO,
                             LocalDateTime now) {
        List<PaymentCreateCommandDTO.GoodsInfoDTO> goodsInfo = commandDTO.getGoodsInfo();
        if (goodsInfo == null || goodsInfo.isEmpty()) {
            return;
        }
        for (int index = 0; index < goodsInfo.size(); index++) {
            PaymentCreateCommandDTO.GoodsInfoDTO source = goodsInfo.get(index);
            TransactionProductItemDO target = new TransactionProductItemDO();
            fillIdentity(target, commandDTO, resultDTO, now);
            target.setProductItemId(PaymentOrderNoGenerator.nextOrderNo(PRODUCT_PREFIX, commandDTO.getTransactionDateTime()));
            target.setMerchantId(commandDTO.getMerchantId());
            target.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
            target.setItemSequence(index + 1);
            target.setProductName(source.getName());
            target.setQuantity(BigDecimal.valueOf(source.getQuantity()));
            target.setItemAmount(source.getAmount());
            target.setItemCurrency(source.getCurrency());
            productItemMapper.insert(target);
        }
    }

    private PaymentCreateCommandDTO.BillingCardHolderInfoDTO toBilling(TransactionBillingInfoDO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateCommandDTO.BillingCardHolderInfoDTO target =
                new PaymentCreateCommandDTO.BillingCardHolderInfoDTO();
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    private PaymentCreateCommandDTO.ShippingInfoDTO toShipping(TransactionShippingInfoDO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateCommandDTO.ShippingInfoDTO target = new PaymentCreateCommandDTO.ShippingInfoDTO();
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    private PaymentCreateCommandDTO.PayerInfoDTO toPayer(TransactionPayerInfoDO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateCommandDTO.PayerInfoDTO target = new PaymentCreateCommandDTO.PayerInfoDTO();
        target.setPayerId(source.getPayerId());
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setPhone(source.getPhone());
        target.setEmail(source.getEmail());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        target.setIpAddress(source.getIpAddress());
        target.setSessionId(source.getSessionId());
        if (StringUtils.hasText(source.getBrowserInfoJson())) {
            target.setBrowserInfo(JsonUtils.parseObject(source.getBrowserInfoJson(),
                    new TypeReference<Map<String, Object>>() {
                    }));
        }
        target.setUserAgent(source.getUserAgent());
        return target;
    }

    private PaymentCreateCommandDTO.SubMerchantInfoDTO toSubMerchant(TransactionMerchantSnapshotDO source) {
        if (source == null || !StringUtils.hasText(source.getSubMerchantInfoJson())) {
            return null;
        }
        return JsonUtils.parseObject(source.getSubMerchantInfoJson(), PaymentCreateCommandDTO.SubMerchantInfoDTO.class);
    }

    private PaymentCreateCommandDTO.GoodsInfoDTO toGoods(TransactionProductItemDO source) {
        PaymentCreateCommandDTO.GoodsInfoDTO target = new PaymentCreateCommandDTO.GoodsInfoDTO();
        target.setName(source.getProductName());
        target.setQuantity(source.getQuantity() == null ? null : source.getQuantity().intValueExact());
        target.setAmount(source.getItemAmount());
        target.setCurrency(source.getItemCurrency());
        return target;
    }

    private void fillIdentity(TransactionBillingInfoDO target,
                              PaymentCreateCommandDTO commandDTO,
                              PaymentCreateResultDTO resultDTO,
                              LocalDateTime now) {
        target.setTransactionId(resultDTO.getTransactionId());
        target.setOperationId(resultDTO.getOperationId());
        fillTimes(target, commandDTO.getTransactionDateTime(), now);
    }

    private void fillIdentity(TransactionPayerInfoDO target,
                              PaymentCreateCommandDTO commandDTO,
                              PaymentCreateResultDTO resultDTO,
                              LocalDateTime now) {
        target.setTransactionId(resultDTO.getTransactionId());
        target.setOperationId(resultDTO.getOperationId());
        target.setTransactionDateTime(commandDTO.getTransactionDateTime());
        target.setTransactionUtcTime(toUtc(commandDTO.getTransactionDateTime()));
        target.setTransactionTimeZone(TIME_ZONE);
        target.setCreateTime(now);
        target.setUpdateTime(now);
    }

    private void fillIdentity(TransactionShippingInfoDO target,
                              PaymentCreateCommandDTO commandDTO,
                              PaymentCreateResultDTO resultDTO,
                              LocalDateTime now) {
        target.setTransactionId(resultDTO.getTransactionId());
        target.setOperationId(resultDTO.getOperationId());
        target.setTransactionDateTime(commandDTO.getTransactionDateTime());
        target.setTransactionUtcTime(toUtc(commandDTO.getTransactionDateTime()));
        target.setTransactionTimeZone(TIME_ZONE);
        target.setCreateTime(now);
        target.setUpdateTime(now);
    }

    private void fillIdentity(TransactionProductItemDO target,
                              PaymentCreateCommandDTO commandDTO,
                              PaymentCreateResultDTO resultDTO,
                              LocalDateTime now) {
        target.setTransactionId(resultDTO.getTransactionId());
        target.setOperationId(resultDTO.getOperationId());
        target.setTransactionDateTime(commandDTO.getTransactionDateTime());
        target.setTransactionUtcTime(toUtc(commandDTO.getTransactionDateTime()));
        target.setTransactionTimeZone(TIME_ZONE);
        target.setCreateTime(now);
        target.setUpdateTime(now);
    }

    private void fillTimes(TransactionBillingInfoDO target,
                           LocalDateTime transactionDateTime,
                           LocalDateTime now) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(toUtc(transactionDateTime));
        target.setTransactionTimeZone(TIME_ZONE);
        target.setCreateTime(now);
        target.setUpdateTime(now);
    }

    private LocalDateTime toUtc(LocalDateTime value) {
        return value.atZone(ZoneId.of(TIME_ZONE)).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private String sha256(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
