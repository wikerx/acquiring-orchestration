package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.TransactionLocatorDO;
import com.scott.payment.payment.mapper.TransactionLocatorMapper;
import com.scott.payment.payment.service.TransactionLocatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 分片交易的固定表定位服务。
 *
 * <p>后续资金动作和查询先按 merchantId 访问固定定位表，再恢复源交易与根交易分片时间。
 * 商户不匹配和记录不存在统一映射为订单不存在，避免通过交易号枚举其他商户数据。Redis 仅缓存
 * 非敏感定位字段，异常时直接回源固定表。</p>
 */
@Service
@Slf4j
public class DefaultTransactionLocatorService implements TransactionLocatorService {

    private final TransactionLocatorMapper transactionLocatorMapper;

    /** 可选 Redis 读缓存；不可用时保持固定表查询行为。 */
    private final Optional<RedisStringService> redisStringService;

    /** Redis Key 规范配置。 */
    private final PaymentRedisProperties redisProperties;

    /**
     * 创建交易定位服务。
     *
     * @param transactionLocatorMapper 交易定位 Mapper
     */
    @Autowired
    public DefaultTransactionLocatorService(TransactionLocatorMapper transactionLocatorMapper,
                                            Optional<RedisStringService> redisStringService,
                                            PaymentRedisProperties redisProperties) {
        this.transactionLocatorMapper = transactionLocatorMapper;
        this.redisStringService = redisStringService;
        this.redisProperties = redisProperties;
    }

    /** 纯 Mapper 单元测试和无 Redis 独立环境使用的兼容构造器。 */
    DefaultTransactionLocatorService(TransactionLocatorMapper transactionLocatorMapper) {
        this(transactionLocatorMapper, Optional.empty(), new PaymentRedisProperties());
    }

    /**
     * 根据源交易 ID 补齐后续资金动作的分片路由时间。
     *
     * @param commandDTO 后续动作命令
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public void enrichFollowUpRoute(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getMerchantId())
                || commandDTO.getTransactionInfo() == null
                || !StringUtils.hasText(commandDTO.getTransactionInfo().getSourceTransactionId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo = commandDTO.getTransactionInfo();
        // 定位查询始终携带 merchantId，不能仅凭全局交易号跨商户查找。
        TransactionLocatorDO locator = findByTransactionId(
                commandDTO.getMerchantId(), transactionInfo.getSourceTransactionId());
        validateMerchantOrder(commandDTO, locator);
        transactionInfo.setRootTransactionId(locator.getRootTransactionId());
        transactionInfo.setSourceTransactionDateTime(locator.getTransactionDateTime());
        transactionInfo.setRootTransactionDateTime(locator.getRootTransactionDateTime());
    }

    /**
     * 根据平台交易 ID 或商户订单号补齐查询路由时间。
     *
     * @param commandDTO 商户交易查询命令
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public void enrichQueryRoute(PaymentCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getMerchantId())
                || !StringUtils.hasText(commandDTO.getMerchantOrderNo())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo = commandDTO.getTransactionInfo();
        if (transactionInfo == null) {
            transactionInfo = new PaymentCreateCommandDTO.TransactionInfoDTO();
            commandDTO.setTransactionInfo(transactionInfo);
        }
        TransactionLocatorDO locator = StringUtils.hasText(transactionInfo.getTransactionId())
                ? findByTransactionId(commandDTO.getMerchantId(), transactionInfo.getTransactionId())
                : findRootByMerchantOrder(commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
        validateMerchantOrder(commandDTO, locator);
        transactionInfo.setRootTransactionId(locator.getRootTransactionId());
        transactionInfo.setSourceTransactionDateTime(locator.getTransactionDateTime());
        transactionInfo.setRootTransactionDateTime(locator.getRootTransactionDateTime());
    }

    /** 商户不匹配和记录不存在统一返回订单不存在，避免交易标识枚举。 */
    private void validateMerchantOrder(PaymentCreateCommandDTO commandDTO, TransactionLocatorDO locator) {
        if (locator == null
                || !commandDTO.getMerchantId().equals(locator.getMerchantId())
                || !commandDTO.getMerchantOrderNo().equals(locator.getMerchantOrderNo())
                || locator.getTransactionDateTime() == null
                || locator.getRootTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
    }

    private TransactionLocatorDO findByTransactionId(String merchantId, String transactionId) {
        String key = locatorKey("transaction", merchantId, transactionId);
        TransactionLocatorDO cached = read(key);
        if (cached != null) {
            return cached;
        }
        TransactionLocatorDO loaded = transactionLocatorMapper.selectByTransactionId(merchantId, transactionId);
        write(key, loaded);
        return loaded;
    }

    private TransactionLocatorDO findRootByMerchantOrder(String merchantId, String merchantOrderNo) {
        String key = locatorKey("merchant-order", merchantId, merchantOrderNo);
        TransactionLocatorDO cached = read(key);
        if (cached != null) {
            return cached;
        }
        TransactionLocatorDO loaded = transactionLocatorMapper.selectRootByMerchantOrder(merchantId, merchantOrderNo);
        write(key, loaded);
        return loaded;
    }

    /** 缓存只保存定位字段 JSON，读取失败时删除坏值并回源固定表。 */
    private TransactionLocatorDO read(String key) {
        if (redisStringService.isEmpty()) {
            return null;
        }
        try {
            Object value = redisStringService.get().get(key);
            if (value == null) {
                return null;
            }
            if (!(value instanceof String json) || !StringUtils.hasText(json)) {
                redisStringService.get().delete(key);
                return null;
            }
            return JsonUtils.parseObject(json, TransactionLocatorDO.class);
        } catch (RuntimeException exception) {
            log.warn("交易定位缓存读取降级, event: TRANSACTION_LOCATOR_CACHE_READ_FALLBACK, exceptionType: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    /** 不缓存未命中定位，避免交易创建与立即查询竞态。 */
    private void write(String key, TransactionLocatorDO locator) {
        if (locator == null || redisStringService.isEmpty()) {
            return;
        }
        try {
            redisStringService.get().set(
                    key,
                    JsonUtils.toJsonString(locator),
                    DefaultTransactionQueryCacheService.jitteredTransactionTtl());
        } catch (RuntimeException exception) {
            // Locator Redis 只用于减压，数据库固定表始终是事实源。
            log.warn("交易定位缓存写入降级, event: TRANSACTION_LOCATOR_CACHE_WRITE_FALLBACK, exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private String locatorKey(String variant, String merchantId, String businessId) {
        return redisProperties.businessKey(
                "payment",
                "transaction-locator",
                "v1",
                variant,
                RedisKeyDigest.sha256(merchantId.trim()),
                RedisKeyDigest.sha256(businessId.trim()));
    }
}
