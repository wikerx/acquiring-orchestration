package com.scott.payment.component.nacos.encryption;

import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AcquiringAesGcmEncryptionPlugin
 * @date : 2026-09-02 00:00
 * @email : scott_x@163.com
 * @description : Nacos 配置加密 SPI 实现；每条 cipher-acqaesgcm 配置使用独立 AES-256-GCM 数据密钥和随机 IV，
 * 数据密钥再由部署环境的根密钥包装，避免配置正文与可直接解密它的明文数据密钥同时落库。
 * @status : create
 */
public final class AcquiringAesGcmEncryptionPlugin implements EncryptionPluginService {

    static {
        NacosConfigInfoEncryptedKeyCompatibility.installIfRequired();
    }

    /** DataId 使用的算法标识，例如 cipher-acqaesgcm-service-payment-dev.yaml。 */
    public static final String ALGORITHM = "acqaesgcm";

    /** 环境变量形式的配置加密主密钥名称。 */
    public static final String MASTER_KEY_ENV = "NACOS_ENCRYPTION_MASTER_KEY";

    /** JVM 系统属性形式的配置加密主密钥名称。 */
    public static final String MASTER_KEY_PROPERTY = "acquiring.nacos.encryption.master-key";

    /** 环境变量形式的配置加密主密钥文件路径。 */
    public static final String MASTER_KEY_FILE_ENV = "NACOS_ENCRYPTION_MASTER_KEY_FILE";

    /** JVM 系统属性形式的配置加密主密钥文件路径。 */
    public static final String MASTER_KEY_FILE_PROPERTY = "acquiring.nacos.encryption.master-key-file";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PAYLOAD_VERSION = "v1";
    private static final String SEPARATOR = ".";
    private static final int AES_KEY_BITS = 256;
    private static final int AES_KEY_BYTES = AES_KEY_BITS / 8;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_MASTER_KEY_FILE_BYTES = 16 * 1024;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 使用当前配置的数据密钥加密 Nacos 配置正文。 */
    @Override
    public String encrypt(String secretKey, String content) {
        return encryptWithKey(decodeKey(secretKey, "data key"), requireContent(content));
    }

    /** 使用当前配置的数据密钥解密 Nacos 配置正文。 */
    @Override
    public String decrypt(String secretKey, String content) {
        return decryptWithKey(decodeKey(secretKey, "data key"), requireContent(content));
    }

    /** 为单条 Nacos 配置生成随机 256 位数据密钥。 */
    @Override
    public String generateSecretKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(AES_KEY_BITS, SECURE_RANDOM);
            SecretKey key = generator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Nacos configuration data key generation failed", exception);
        }
    }

    /** 返回 DataId 中使用的算法名称。 */
    @Override
    public String algorithmName() {
        return ALGORITHM;
    }

    /** 使用环境级根密钥包装单条配置的数据密钥。 */
    @Override
    public String encryptSecretKey(String secretKey) {
        return encryptWithKey(resolveMasterKey(), requireContent(secretKey));
    }

    /** 使用环境级根密钥解包单条配置的数据密钥。 */
    @Override
    public String decryptSecretKey(String secretKey) {
        return decryptWithKey(resolveMasterKey(), requireContent(secretKey));
    }

    private byte[] resolveMasterKey() {
        String configured = System.getProperty(MASTER_KEY_PROPERTY);
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv(MASTER_KEY_ENV);
        }
        if (configured == null || configured.trim().isEmpty()) {
            String keyFile = System.getProperty(MASTER_KEY_FILE_PROPERTY);
            if (keyFile == null || keyFile.trim().isEmpty()) {
                keyFile = System.getenv(MASTER_KEY_FILE_ENV);
            }
            configured = readMasterKeyFile(keyFile);
        }
        return decodeKey(configured, "master key");
    }

    /**
     * 从权限受控的 Secret 文件读取根密钥。文件既可只包含 Base64 值，也可使用 dotenv 形式保存
     * {@code NACOS_ENCRYPTION_MASTER_KEY}，便于本地 IDEA 与 Nacos 容器复用同一受控文件。
     */
    private String readMasterKeyFile(String configuredPath) {
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            return null;
        }
        Path keyPath = Paths.get(configuredPath.trim()).toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(keyPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Nacos configuration master key file must be a regular file");
            }
            long size = Files.size(keyPath);
            if (size <= 0 || size > MAX_MASTER_KEY_FILE_BYTES) {
                throw new IllegalStateException("Nacos configuration master key file size is invalid");
            }
            validateMasterKeyFilePermissions(keyPath);
            String content = new String(Files.readAllBytes(keyPath), StandardCharsets.UTF_8).trim();
            return masterKeyFromFileContent(content);
        } catch (IOException exception) {
            throw new IllegalStateException("Nacos configuration master key file cannot be read", exception);
        }
    }

    /** POSIX 环境拒绝组或其他用户可访问的根密钥文件；不支持 POSIX 权限的平台由部署层保证 ACL。 */
    private void validateMasterKeyFilePermissions(Path keyPath) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(keyPath, LinkOption.NOFOLLOW_LINKS);
            if (permissions.contains(PosixFilePermission.GROUP_READ)
                    || permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
                    || permissions.contains(PosixFilePermission.OTHERS_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                throw new IllegalStateException(
                        "Nacos configuration master key file must not grant group or other permissions");
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows 等非 POSIX 文件系统由 Secret 挂载或主机 ACL 保证权限边界。
        }
    }

    private String masterKeyFromFileContent(String content) {
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Nacos configuration master key file is empty");
        }
        if (content.indexOf('\n') < 0 && content.indexOf('\r') < 0
                && !content.startsWith(MASTER_KEY_ENV + "=")
                && !content.startsWith("export " + MASTER_KEY_ENV + "=")) {
            return removeMatchingQuotes(content);
        }
        String resolved = null;
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            if (!line.startsWith(MASTER_KEY_ENV + "=")) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("Nacos configuration master key file contains duplicate entries");
            }
            resolved = removeMatchingQuotes(line.substring((MASTER_KEY_ENV + "=").length()).trim());
        }
        if (resolved == null || resolved.isEmpty()) {
            throw new IllegalStateException(
                    "Nacos configuration master key file does not define " + MASTER_KEY_ENV);
        }
        return resolved;
    }

    private String removeMatchingQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    private byte[] decodeKey(String encodedKey, String keyName) {
        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalStateException("Nacos configuration " + keyName + " is required");
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey.trim());
            if (key.length != AES_KEY_BYTES) {
                throw new IllegalStateException("Nacos configuration " + keyName + " must be 256 bits");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Nacos configuration " + keyName + " must be Base64", exception);
        }
    }

    private String encryptWithKey(byte[] key, String content) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return PAYLOAD_VERSION + SEPARATOR
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + SEPARATOR
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Nacos configuration encryption failed", exception);
        }
    }

    private String decryptWithKey(byte[] key, String payload) {
        String[] parts = payload.split("\\.", -1);
        if (parts.length != 3 || !PAYLOAD_VERSION.equals(parts[0])) {
            throw new IllegalStateException("Nacos encrypted configuration payload format is invalid");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
            if (iv.length != GCM_IV_BYTES) {
                throw new IllegalStateException("Nacos encrypted configuration IV is invalid");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Nacos configuration decryption failed", exception);
        }
    }

    private String requireContent(String content) {
        if (content == null) {
            throw new IllegalStateException("Nacos configuration encryption content is required");
        }
        return content;
    }
}
