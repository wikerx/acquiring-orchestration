package com.scott.payment.data.security;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.CheckoutCardVaultStoreMessage;
import com.scott.payment.component.security.crypto.CheckoutCardEnvelopeCipher;
import com.scott.payment.data.config.DataCardVaultProperties;
import com.scott.payment.data.entity.DataCheckoutCardVaultDO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * service-data 卡资料传输与静态信封加密测试。
 */
class DataCheckoutCardVaultSecurityTests {

    /**
     * 验证传输解密和静态加密可往返，且 PAN 索引使用 HMAC。
     */
    @Test
    void shouldDecryptTransferAndRoundTripAtRestWithoutRawSha256Index() throws Exception {
        Fixture fixture = fixture();
        CheckoutCardVaultStoreMessage message = encryptedMessage(fixture);

        DataCheckoutCardVaultTransferService.CardVaultPlaintext plaintext =
                fixture.transferService.decrypt(message);
        DataCardVaultCryptoService.EncryptedCardData encrypted = fixture.cryptoService.encrypt(
                message.getMerchantId(), message.getTransactionId(), plaintext);
        DataCheckoutCardVaultDO record = record(message, encrypted);
        DataCardVaultCryptoService.DecryptedCardData decrypted = fixture.cryptoService.decrypt(record);

        assertThat(decrypted.cardNo()).isEqualTo(plaintext.cardNo());
        assertThat(decrypted.expirationMonth()).isEqualTo("12");
        assertThat(decrypted.expirationYear()).isEqualTo("2030");
        assertThat(decrypted.cardholderName()).isEqualTo("Test User");
        assertThat(encrypted.pan().ciphertext()).doesNotContain(plaintext.cardNo());
        String rawSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(plaintext.cardNo().getBytes(StandardCharsets.UTF_8)));
        assertThat(encrypted.panHmac()).isNotEqualTo(rawSha256).hasSize(64);
    }

    /**
     * 验证密文移动到另一交易后因 AAD 不匹配而拒绝解密。
     */
    @Test
    void shouldRejectCiphertextMovedToAnotherTransaction() {
        Fixture fixture = fixture();
        CheckoutCardVaultStoreMessage message = encryptedMessage(fixture);
        message.setTransactionId("T-TAMPERED");

        assertThatThrownBy(() -> fixture.transferService.decrypt(message))
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * 验证卡资料传输正文包含 CVV 字段时直接拒绝，禁止把安全码作为未知字段静默接受。
     */
    @Test
    void shouldRejectTransferPayloadContainingSecurityCode() {
        Fixture fixture = fixture();
        Map<String, String> plaintext = Map.of(
                "cardNo", "4111111111111111",
                "expirationMonth", "12",
                "expirationYear", "2030",
                "cardholderName", "Test User",
                "cardBrand", "VISA",
                "securityCode", "123");
        CheckoutCardVaultStoreMessage message = encryptedMessage(fixture, plaintext);

        assertThatThrownBy(() -> fixture.transferService.decrypt(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("card vault transfer payload is invalid");
    }

    /**
     * 构造卡资料密码学测试所需的密钥、服务和明文夹具。
     */
    private Fixture fixture() {
        CheckoutCardEnvelopeCipher cipher = new CheckoutCardEnvelopeCipher();
        KeyPair keyPair = cipher.generateRsaKeyPair();
        DataCardVaultProperties properties = new DataCardVaultProperties();
        properties.setEnabled(true);
        properties.setTransferKeyId("vault-key-v1");
        properties.setTransferPrivateKeyPkcs8Base64(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        properties.setPanHmacKeyVersion("hmac-v1");
        properties.setPanHmacPepper("test-pan-hmac-pepper-with-at-least-32-bytes");
        properties.setKekVersion("kek-v1");
        properties.setKekBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new Fixture(cipher, keyPair,
                new DataCheckoutCardVaultTransferService(properties, cipher),
                new DataCardVaultCryptoService(properties));
    }

    /**
     * 构造绑定测试业务身份的卡资料传输信封消息。
     */
    private CheckoutCardVaultStoreMessage encryptedMessage(Fixture fixture) {
        Map<String, String> plaintext = Map.of(
                "cardNo", "4111111111111111",
                "expirationMonth", "12",
                "expirationYear", "2030",
                "cardholderName", "Test User",
                "cardBrand", "VISA");
        return encryptedMessage(fixture, plaintext);
    }

    /**
     * 使用指定明文字段构造绑定测试业务身份的卡资料传输信封消息。
     *
     * @param fixture 密码学测试夹具
     * @param plaintext 待加密卡资料字段
     * @return 绑定完整 AAD 的传输消息
     */
    private CheckoutCardVaultStoreMessage encryptedMessage(Fixture fixture, Map<String, String> plaintext) {
        CheckoutCardVaultStoreMessage message = new CheckoutCardVaultStoreMessage();
        message.setMessageId("CVA1001");
        message.setMerchantId("M1001");
        message.setTransactionId("T1001");
        message.setTransactionDateTime(LocalDateTime.of(2030, 1, 2, 3, 4, 5));
        message.setCheckoutAttemptId("A1001");
        message.setAlgorithm(CheckoutCardEnvelopeCipher.ALGORITHM);
        message.setKeyId("vault-key-v1");
        CheckoutCardEnvelopeCipher.EncryptedEnvelope envelope = fixture.cipher.encrypt(
                JsonUtils.toJsonString(plaintext), fixture.keyPair.getPublic(), message.transferAad());
        message.setEncryptedKey(envelope.encryptedKey());
        message.setIv(envelope.iv());
        message.setCiphertext(envelope.ciphertext());
        return message;
    }

    /**
     * 将字段信封组装为静态加密卡资料记录。
     */
    private DataCheckoutCardVaultDO record(CheckoutCardVaultStoreMessage message,
                                           DataCardVaultCryptoService.EncryptedCardData encrypted) {
        DataCheckoutCardVaultDO record = new DataCheckoutCardVaultDO();
        record.setMerchantId(message.getMerchantId());
        record.setTransactionId(message.getTransactionId());
        record.setKekVersion(encrypted.kekVersion());
        record.setPanCiphertext(encrypted.pan().ciphertext());
        record.setPanIv(encrypted.pan().iv());
        record.setPanAuthTag(encrypted.pan().authTag());
        record.setExpirationCiphertext(encrypted.expiration().ciphertext());
        record.setExpirationIv(encrypted.expiration().iv());
        record.setExpirationAuthTag(encrypted.expiration().authTag());
        record.setCardholderNameCiphertext(encrypted.cardholderName().ciphertext());
        record.setCardholderNameIv(encrypted.cardholderName().iv());
        record.setCardholderNameAuthTag(encrypted.cardholderName().authTag());
        record.setWrappedDekCiphertext(encrypted.wrappedDek().ciphertext());
        record.setWrappedDekIv(encrypted.wrappedDek().iv());
        record.setWrappedDekAuthTag(encrypted.wrappedDek().authTag());
        return record;
    }

    /**
     * 保存当前测试场景共用的依赖和业务对象。
     */
    private record Fixture(CheckoutCardEnvelopeCipher cipher,
                           KeyPair keyPair,
                           DataCheckoutCardVaultTransferService transferService,
                           DataCardVaultCryptoService cryptoService) {
    }
}
