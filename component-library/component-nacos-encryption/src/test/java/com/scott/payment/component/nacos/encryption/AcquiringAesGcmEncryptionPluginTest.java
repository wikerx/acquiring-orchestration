package com.scott.payment.component.nacos.encryption;

import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** AES-GCM Nacos 配置插件测试，覆盖正文、数据密钥包装和缺失主密钥门禁。 */
class AcquiringAesGcmEncryptionPluginTest {

    private static final String MASTER_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @AfterEach
    void clearMasterKey() {
        System.clearProperty(AcquiringAesGcmEncryptionPlugin.MASTER_KEY_PROPERTY);
        System.clearProperty(AcquiringAesGcmEncryptionPlugin.MASTER_KEY_FILE_PROPERTY);
    }

    @Test
    void shouldEncryptAndDecryptConfigurationContent() {
        System.setProperty(AcquiringAesGcmEncryptionPlugin.MASTER_KEY_PROPERTY, MASTER_KEY);
        AcquiringAesGcmEncryptionPlugin plugin = new AcquiringAesGcmEncryptionPlugin();
        String dataKey = plugin.generateSecretKey();

        String encrypted = plugin.encrypt(dataKey, "internal-secret-value");

        assertThat(encrypted).startsWith("v1.").doesNotContain("internal-secret-value");
        assertThat(plugin.decrypt(dataKey, encrypted)).isEqualTo("internal-secret-value");
    }

    @Test
    void shouldWrapDataKeyWithExternalMasterKey() {
        System.setProperty(AcquiringAesGcmEncryptionPlugin.MASTER_KEY_PROPERTY, MASTER_KEY);
        AcquiringAesGcmEncryptionPlugin plugin = new AcquiringAesGcmEncryptionPlugin();
        String dataKey = plugin.generateSecretKey();

        String wrapped = plugin.encryptSecretKey(dataKey);

        assertThat(wrapped).startsWith("v1.").isNotEqualTo(dataKey);
        assertThat(plugin.decryptSecretKey(wrapped)).isEqualTo(dataKey);
    }

    @Test
    void shouldUseRandomIvForEachEncryption() {
        AcquiringAesGcmEncryptionPlugin plugin = new AcquiringAesGcmEncryptionPlugin();
        String dataKey = plugin.generateSecretKey();

        assertThat(plugin.encrypt(dataKey, "same-content"))
                .isNotEqualTo(plugin.encrypt(dataKey, "same-content"));
    }

    @Test
    void shouldRejectSecretKeyWrappingWhenMasterKeyIsMissing() {
        AcquiringAesGcmEncryptionPlugin plugin = new AcquiringAesGcmEncryptionPlugin();

        assertThatThrownBy(() -> plugin.encryptSecretKey(plugin.generateSecretKey()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master key is required");
    }

    @Test
    void shouldReadMasterKeyFromRestrictedDotenvFile(@TempDir Path tempDirectory) throws IOException {
        Path keyFile = tempDirectory.resolve("nacos-runtime.env");
        String dotenv = "NACOS_AUTH_ENABLE=true\n"
                + AcquiringAesGcmEncryptionPlugin.MASTER_KEY_ENV + "=" + MASTER_KEY + "\n";
        Files.write(keyFile, dotenv.getBytes(StandardCharsets.UTF_8));
        restrictOwnerOnlyWhenSupported(keyFile);
        System.setProperty(AcquiringAesGcmEncryptionPlugin.MASTER_KEY_FILE_PROPERTY, keyFile.toString());
        AcquiringAesGcmEncryptionPlugin plugin = new AcquiringAesGcmEncryptionPlugin();
        String dataKey = plugin.generateSecretKey();

        String wrapped = plugin.encryptSecretKey(dataKey);

        assertThat(plugin.decryptSecretKey(wrapped)).isEqualTo(dataKey);
    }

    @Test
    void shouldReadMasterKeyFromRestrictedRawSecretFile(@TempDir Path tempDirectory) throws IOException {
        Path keyFile = tempDirectory.resolve("nacos-master-key");
        Files.write(keyFile, MASTER_KEY.getBytes(StandardCharsets.UTF_8));
        restrictOwnerOnlyWhenSupported(keyFile);
        System.setProperty(AcquiringAesGcmEncryptionPlugin.MASTER_KEY_FILE_PROPERTY, keyFile.toString());
        AcquiringAesGcmEncryptionPlugin plugin = new AcquiringAesGcmEncryptionPlugin();
        String dataKey = plugin.generateSecretKey();

        String wrapped = plugin.encryptSecretKey(dataKey);

        assertThat(plugin.decryptSecretKey(wrapped)).isEqualTo(dataKey);
    }

    @Test
    void shouldRejectMasterKeyFileReadableByGroup(@TempDir Path tempDirectory) throws IOException {
        Path keyFile = tempDirectory.resolve("nacos-master-key");
        Files.write(keyFile, MASTER_KEY.getBytes(StandardCharsets.UTF_8));
        FileStore fileStore = Files.getFileStore(keyFile);
        assumeTrue(fileStore.supportsFileAttributeView(PosixFileAttributeView.class));
        Files.setPosixFilePermissions(keyFile, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ));
        System.setProperty(AcquiringAesGcmEncryptionPlugin.MASTER_KEY_FILE_PROPERTY, keyFile.toString());
        AcquiringAesGcmEncryptionPlugin plugin = new AcquiringAesGcmEncryptionPlugin();

        assertThatThrownBy(() -> plugin.encryptSecretKey(plugin.generateSecretKey()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not grant group or other permissions");
    }

    @Test
    void shouldLoadInClientWithoutNacosDatasourceClasses() throws Exception {
        URL componentClasses = AcquiringAesGcmEncryptionPlugin.class.getProtectionDomain()
                .getCodeSource().getLocation();
        URL encryptionApi = EncryptionPluginService.class.getProtectionDomain().getCodeSource().getLocation();

        try (URLClassLoader clientClassLoader = new URLClassLoader(
                new URL[]{componentClasses, encryptionApi}, null)) {
            Class<?> pluginClass = Class.forName(AcquiringAesGcmEncryptionPlugin.class.getName(), true,
                    clientClassLoader);

            assertThat(pluginClass.getDeclaredConstructor().newInstance()).isNotNull();
            assertThatThrownBy(() -> clientClassLoader.loadClass(
                    "com.alibaba.nacos.plugin.datasource.model.MapperResult"))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    private void restrictOwnerOnlyWhenSupported(Path keyFile) throws IOException {
        FileStore fileStore = Files.getFileStore(keyFile);
        if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
            Files.setPosixFilePermissions(keyFile, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        }
    }
}
