package com.scott.payment.payment.security;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.component.security.crypto.CheckoutCardEnvelopeCipher;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.config.PaymentCheckoutProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * service-payment 卡数据安全边界，负责公钥元数据下发、一次性 nonce 原子消费、信封解密和解密后卡字段校验。
 */
@Service
@Slf4j
public class PaymentCheckoutCardEnvelopeService {

    /**
     * 密码学安全随机数生成器，用于生成一次性 AES 密钥和 GCM IV。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> CONSUME_NONCE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return 0 end
            if value ~= ARGV[1] then return -1 end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final PaymentCheckoutProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final PaymentRedisProperties redisProperties;
    private final CheckoutCardEnvelopeCipher cipher = new CheckoutCardEnvelopeCipher();
    private final Map<String, LocalNonce> localNonces = new ConcurrentHashMap<>();
    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    /**
     * 创建卡数据安全服务；生产缺少固定密钥时直接阻止应用启动。
     */
    @Autowired
    public PaymentCheckoutCardEnvelopeService(PaymentCheckoutProperties properties,
                                              ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                              PaymentRedisProperties redisProperties) {
        this(properties, redisTemplateProvider.getIfAvailable(), redisProperties);
    }

    /**
     * 创建卡数据安全服务并校验固定密钥或受控临时密钥配置。
     *
     * @param properties 收银台卡数据加密配置
     * @param redisTemplate 一次性随机数防重放存储；为空时仅允许非生产本地模式
     * @param redisProperties 环境隔离的 Redis Key 配置
     */
    PaymentCheckoutCardEnvelopeService(PaymentCheckoutProperties properties,
                                       StringRedisTemplate redisTemplate,
                                       PaymentRedisProperties redisProperties) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        PaymentCheckoutProperties.CardEncryption config = properties.getCardEncryption();
        if (StringUtils.hasText(config.getPublicKeyX509Base64())
                && StringUtils.hasText(config.getPrivateKeyPkcs8Base64())) {
            this.publicKey = cipher.readPublicKey(config.getPublicKeyX509Base64());
            this.privateKey = cipher.readPrivateKey(config.getPrivateKeyPkcs8Base64());
        } else if (config.isAllowEphemeralKey()) {
            KeyPair keyPair = cipher.generateRsaKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();
            log.warn("收银台启用临时卡数据密钥, event: CHECKOUT_CARD_EPHEMERAL_KEY_ENABLED, keyId: {}, productionAllowed: false",
                    config.getKeyId());
        } else {
            throw new IllegalStateException("checkout card encryption key pair is required");
        }
    }

    /**
     * 签发浏览器卡数据加密元数据，nonce 在 Redis 中只保存摘要键和绑定摘要。
     */
    public PaymentCheckoutSessionQueryResultDTO.CardEncryptionDTO issue(String checkoutSessionId) {
        if (!StringUtils.hasText(checkoutSessionId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "checkoutSessionId is required");
        }
        String nonce = newNonce();
        String key = nonceKey(checkoutSessionId, nonce);
        String expected = expectedValue(checkoutSessionId);
        storeNonce(key, expected);
        PaymentCheckoutSessionQueryResultDTO.CardEncryptionDTO result =
                new PaymentCheckoutSessionQueryResultDTO.CardEncryptionDTO();
        result.setAlgorithm(CheckoutCardEnvelopeCipher.ALGORITHM);
        result.setKeyId(config().getKeyId());
        result.setPublicKey(Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        result.setNonce(nonce);
        return result;
    }

    /**
     * 原子消费 nonce 后解密；消费失败、AAD 篡改或字段非法均不得进入支付创建。
     */
    public PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO decryptAndConsume(
            PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO envelope,
            String checkoutSessionId,
            String attemptRequestId) {
        validateEnvelopeMetadata(envelope);
        consumeNonce(nonceKey(checkoutSessionId, envelope.getNonce()), expectedValue(checkoutSessionId));
        String plainText = cipher.decrypt(
                envelope.getEncryptedKey(), envelope.getIv(), envelope.getCiphertext(), privateKey,
                aad(checkoutSessionId, attemptRequestId, envelope.getNonce()));
        CardPlaintext source;
        try {
            source = JsonUtils.parseObject(plainText, CardPlaintext.class);
        } catch (RuntimeException exception) {
            throw invalidEnvelope();
        }
        validateCard(source);
        PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO target =
                new PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO();
        target.setCardNo(source.cardNo);
        target.setExpirationMonth(source.expirationMonth);
        target.setExpirationYear(source.expirationYear);
        target.setSecurityCode(source.securityCode);
        target.setCardholderName(source.cardholderName);
        return target;
    }

    /**
     * 浏览器和服务端必须使用完全一致的 UTF-8 AAD 文本。
     */
    public static String aad(String checkoutSessionId, String attemptRequestId, String nonce) {
        return "checkout-card-v1|" + checkoutSessionId + "|" + attemptRequestId + "|" + nonce;
    }

    /**
     * 校验密文信封元数据。
     */
    private void validateEnvelopeMetadata(PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO envelope) {
        if (envelope == null
                || !CheckoutCardEnvelopeCipher.ALGORITHM.equals(envelope.getAlgorithm())
                || !config().getKeyId().equals(envelope.getKeyId())
                || !StringUtils.hasText(envelope.getNonce())) {
            throw invalidEnvelope();
        }
    }

    /**
     * 校验解密后的卡信息。
     */
    private void validateCard(CardPlaintext card) {
        if (card == null
                || card.cardNo == null || !card.cardNo.matches("\\d{12,19}") || !luhnValid(card.cardNo)
                || card.expirationMonth == null || !card.expirationMonth.matches("0[1-9]|1[0-2]")
                || card.expirationYear == null || !card.expirationYear.matches("20\\d{2}")
                || card.securityCode == null || !card.securityCode.matches("\\d{3,4}")
                || !StringUtils.hasText(card.cardholderName) || card.cardholderName.length() > 128) {
            throw invalidEnvelope();
        }
        YearMonth expiry = YearMonth.of(Integer.parseInt(card.expirationYear), Integer.parseInt(card.expirationMonth));
        if (expiry.isBefore(YearMonth.now())) {
            throw invalidEnvelope();
        }
    }

    /**
     * 校验卡号是否满足 Luhn 规则。
     */
    private boolean luhnValid(String value) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = value.length() - 1; index >= 0; index--) {
            int digit = value.charAt(index) - '0';
            if (doubleDigit && (digit *= 2) > 9) {
                digit -= 9;
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum > 0 && sum % 10 == 0;
    }

    /**
     * 以 SETNX 和 TTL 登记一次性卡密文 nonce，阻止相同信封被重复提交。
     * <p>
     * 生产配置要求重放存储时 Redis 故障必须关闭入口；仅在显式允许降级的环境使用进程内存储，
     * 该降级不提供跨实例重放保护。
     */
    private void storeNonce(String key, String expected) {
        Duration ttl = Duration.ofSeconds(Math.max(60, config().getNonceTtlSeconds()));
        if (redisTemplate != null) {
            try {
                if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, expected, ttl))) {
                    return;
                }
                throw new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(), "checkout card nonce collision");
            } catch (ServiceException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                if (config().isReplayStoreRequired()) {
                    throw replayStoreUnavailable();
                }
            }
        } else if (config().isReplayStoreRequired()) {
            throw replayStoreUnavailable();
        }
        cleanupLocalNonces();
        localNonces.put(key, new LocalNonce(expected, Instant.now().plus(ttl)));
    }

    /**
     * 原子校验并删除 nonce，确保成功解密只发生一次。
     * <p>
     * Redis Lua 脚本把比对和删除合并为单个原子操作；值不匹配、已过期或已消费统一按非法密文处理，
     * 避免向调用方泄露重放存储状态。
     */
    private void consumeNonce(String key, String expected) {
        if (redisTemplate != null) {
            try {
                Long result = redisTemplate.execute(CONSUME_NONCE_SCRIPT, java.util.List.of(key), expected);
                if (Long.valueOf(1L).equals(result)) {
                    return;
                }
                throw invalidEnvelope();
            } catch (ServiceException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                if (config().isReplayStoreRequired()) {
                    throw replayStoreUnavailable();
                }
            }
        } else if (config().isReplayStoreRequired()) {
            throw replayStoreUnavailable();
        }
        LocalNonce nonce = localNonces.remove(key);
        if (nonce == null || nonce.expireTime.isBefore(Instant.now()) || !nonce.expectedValue.equals(expected)) {
            throw invalidEnvelope();
        }
    }

    /**
     * 清理本地降级存储中过期的一次性随机数，避免长期占用进程内存。
     */
    private void cleanupLocalNonces() {
        Instant now = Instant.now();
        localNonces.entrySet().removeIf(entry -> entry.getValue().expireTime.isBefore(now));
    }

    /**
     * 对收银台会话号和一次性随机数取摘要，构造防重放 Redis Key。
     *
     * @param checkoutSessionId 收银台会话号
     * @param nonce 收银台会话标识或一次性随机数，用于构造防重放身份
     * @return 不包含会话号或随机数原文的环境隔离 Redis Key
     */
    private String nonceKey(String checkoutSessionId, String nonce) {
        return redisProperties.businessKey("checkout", "card-nonce",
                RedisKeyDigest.sha256(checkoutSessionId + "|" + nonce));
    }

    /**
     * 读取密文信封预期元数据。
     */
    private String expectedValue(String checkoutSessionId) {
        return RedisKeyDigest.sha256(checkoutSessionId + "|" + config().getKeyId());
    }

    /**
     * 读取并校验当前安全配置。
     */
    private PaymentCheckoutProperties.CardEncryption config() {
        return properties.getCardEncryption();
    }

    /**
     * 使用密码学安全随机数生成 URL-safe 的一次性 nonce。
     * @return 当前方法生成或规范化后的文本值
     */
    private String newNonce() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 构建卡密文信封非法异常。
     */
    private ServiceException invalidEnvelope() {
        return new ServiceException(ApiResultEnum.ENCRYPTED_DATA_INVALID.getCode(), "card data envelope is invalid");
    }

    /**
     * 构建防重存储不可用异常。
     */
    private ServiceException replayStoreUnavailable() {
        return new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(), "checkout card replay store is unavailable");
    }

    /**
     * 封装卡密文信封解密后的短生命周期明文。
     */
    private static final class CardPlaintext {
        /**
         * 卡编号，表示银行卡号或脱敏卡号字段。
         */
        public String cardNo;
        public String expirationMonth;
        public String expirationYear;
        public String securityCode;
        public String cardholderName;
    }

    /**
     * 保存本地防重随机数及其过期时间。
     */
    private record LocalNonce(String expectedValue, Instant expireTime) {
    }
}
