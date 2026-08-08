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
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutCardEnvelopeService
 * @date : 2026-08-08 15:35
 * @email : scott_x@163.com
 * @description : service-payment 卡数据安全边界，负责公钥元数据下发、一次性 nonce 原子消费、信封解密和解密后卡字段校验。
 * @status : create
 */
@Service
@Slf4j
public class PaymentCheckoutCardEnvelopeService {

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

    /** 创建卡数据安全服务；生产缺少固定密钥时直接阻止应用启动。 */
    public PaymentCheckoutCardEnvelopeService(PaymentCheckoutProperties properties,
                                              ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                              PaymentRedisProperties redisProperties) {
        this(properties, redisTemplateProvider.getIfAvailable(), redisProperties);
    }

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
            log.warn("event: CHECKOUT_CARD_EPHEMERAL_KEY_ENABLED keyId: {} productionAllowed: false",
                    config.getKeyId());
        } else {
            throw new IllegalStateException("checkout card encryption key pair is required");
        }
    }

    /** 签发浏览器卡数据加密元数据，nonce 在 Redis 中只保存摘要键和绑定摘要。 */
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

    /** 原子消费 nonce 后解密；消费失败、AAD 篡改或字段非法均不得进入支付创建。 */
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

    /** 浏览器和服务端必须使用完全一致的 UTF-8 AAD 文本。 */
    public static String aad(String checkoutSessionId, String attemptRequestId, String nonce) {
        return "checkout-card-v1|" + checkoutSessionId + "|" + attemptRequestId + "|" + nonce;
    }

    private void validateEnvelopeMetadata(PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO envelope) {
        if (envelope == null
                || !CheckoutCardEnvelopeCipher.ALGORITHM.equals(envelope.getAlgorithm())
                || !config().getKeyId().equals(envelope.getKeyId())
                || !StringUtils.hasText(envelope.getNonce())) {
            throw invalidEnvelope();
        }
    }

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

    private void cleanupLocalNonces() {
        Instant now = Instant.now();
        localNonces.entrySet().removeIf(entry -> entry.getValue().expireTime.isBefore(now));
    }

    private String nonceKey(String checkoutSessionId, String nonce) {
        return redisProperties.businessKey("checkout", "card-nonce",
                RedisKeyDigest.sha256(checkoutSessionId + "|" + nonce));
    }

    private String expectedValue(String checkoutSessionId) {
        return RedisKeyDigest.sha256(checkoutSessionId + "|" + config().getKeyId());
    }

    private PaymentCheckoutProperties.CardEncryption config() {
        return properties.getCardEncryption();
    }

    private String newNonce() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ServiceException invalidEnvelope() {
        return new ServiceException(ApiResultEnum.ENCRYPTED_DATA_INVALID.getCode(), "card data envelope is invalid");
    }

    private ServiceException replayStoreUnavailable() {
        return new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(), "checkout card replay store is unavailable");
    }

    private static final class CardPlaintext {
        public String cardNo;
        public String expirationMonth;
        public String expirationYear;
        public String securityCode;
        public String cardholderName;
    }

    private record LocalNonce(String expectedValue, Instant expireTime) {
    }
}
