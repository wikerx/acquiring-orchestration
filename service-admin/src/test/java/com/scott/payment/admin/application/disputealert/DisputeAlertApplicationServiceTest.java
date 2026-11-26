package com.scott.payment.admin.application.disputealert;

import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.risk.DisputeAlertDtos.*;
import com.scott.payment.admin.infrastructure.disputealert.DisputeAlertRepository;
import com.scott.payment.admin.infrastructure.disputealert.DisputeAlertRepository.*;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.support.risk.DisputeAlertCredentialCrypto;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.upload.ManagedImageStore;
import com.scott.payment.component.web.upload.SafeImageProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DisputeAlertApplicationServiceTest {

    private final DisputeAlertRepository repository = mock(DisputeAlertRepository.class);
    private final DisputeAlertCredentialCrypto credentialCrypto = mock(DisputeAlertCredentialCrypto.class);
    private final AdminMerchantDataScopeResolver scopes = mock(AdminMerchantDataScopeResolver.class);
    private final SafeImageProcessor images = mock(SafeImageProcessor.class);
    private final ManagedImageStore storage = mock(ManagedImageStore.class);
    private final AdminDictService dictionaries = mock(AdminDictService.class);
    private final DisputeAlertApplicationService service = serviceForProfiles("dev");
    private static final long ACTOR = 7L;
    private static final long ACCOUNT = 21L;
    private static final long PLATFORM = 11L;
    private static final String SHIELD = "VERXA_SHIELD";

    @BeforeEach
    void authorizeAdmin() {
        var auth = new InternalAuthAccount();
        auth.setAccountId(ACTOR);
        auth.setAppCode("ADMIN");
        InternalAuthContextHolder.set(auth);
        when(scopes.resolve(auth)).thenReturn(AdminMerchantDataScope.all());
    }

    @AfterEach
    void clearAuth() {
        InternalAuthContextHolder.clear();
    }

    @ParameterizedTest
    @CsvSource({"dev, TEST", "prod, PRODUCTION"})
    void oneShieldAccountOwnsTwoIndependentProducts(String profile, Environment environment) {
        Platform platform = platform(null, 1);
        Account account = account(null, 1);
        PlatformProduct ethoca = new PlatformProduct(12, PLATFORM, Product.ETHOCA, true, Coverage.PENDING);
        PlatformProduct rdr = new PlatformProduct(13, PLATFORM, Product.RDR, true, Coverage.CONFIRMED);
        var input = new AccountCreate(PLATFORM, "MER-TEST", Status.DRAFT,
                List.of(new AccountProductInput(Product.ETHOCA, Status.DRAFT, null),
                        new AccountProductInput(Product.RDR, Status.DRAFT, null)));
        when(repository.platform(PLATFORM, true)).thenReturn(Optional.of(platform));
        when(repository.platformProducts(List.of(PLATFORM))).thenReturn(List.of(ethoca, rdr));
        when(repository.insertAccount(input, environment, ACTOR)).thenReturn(ACCOUNT);
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(account));

        assertThat(serviceForProfiles(profile).createAccount(input).id()).isEqualTo(ACCOUNT);
        verify(repository).saveAccountProduct(eq(account), eq(ethoca), isNull(), eq(Status.DRAFT), any(Delivery.class));
        verify(repository).saveAccountProduct(eq(account), eq(rdr), isNull(), eq(Status.DRAFT), any(Delivery.class));
        verifyNoInteractions(credentialCrypto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUSTREME", "NUVEI", "CHARGEFLOW", "CHARGEBACKHIT"})
    void providerDraftAcceptsBasicDetailsWithoutCallingEncryption(String provider) {
        var account = setupProvider(provider, Status.DRAFT, 1L);
        when(repository.insertAccount(any(AccountCreate.class), eq(Environment.TEST), eq(ACTOR))).thenReturn(ACCOUNT);

        var result = service.createProviderAccount(providerWrite(PLATFORM, null, Status.DRAFT, Map.of(), Map.of()));

        assertThat(result.providerConfig()).isEqualTo(new ProviderConfigView(Map.of(), List.of(), false));
        verify(repository).saveProviderConfig(ACCOUNT, Map.of(), null, false);
        verify(repository).saveAccountProduct(eq(account), any(), isNull(), eq(Status.DRAFT), any(Delivery.class));
        verifyNoInteractions(credentialCrypto);
    }

    @Test
    void unconfirmedAustremeCredentialsAndDeliveryRemainOptionalOnActivation() {
        setupProvider("AUSTREME", Status.DRAFT, 1L);
        var settings = Map.<String, Object>of("deliveryMethod", "sftp", "authMethod", "sshPrivateKey");
        service.updateProviderAccount(ACCOUNT, providerWrite(null, 1L, Status.ENABLED, settings, Map.of()));

        verify(repository).saveProviderConfig(ACCOUNT, settings, null, true);
        verifyNoInteractions(credentialCrypto);
    }

    @Test
    void nuveiActivationRequiresDmnSecretAndOutcomeMerchantIdsBeforeWriting() {
        setupProvider("NUVEI", Status.DRAFT, 1L);
        var dmn = Map.<String, Object>of("usage", List.of("dmn"), "dmnUrl", "https://example.invalid/nuvei",
                "dmnEvents", List.of("externalEthoca"));
        assertThatThrownBy(() -> service.updateProviderAccount(ACCOUNT, providerWrite(null, 1L, Status.ENABLED, dmn, Map.of())))
                .hasMessageContaining("接入凭据");
        var outcome = Map.<String, Object>of("usage", List.of("ethocaOutcome"), "merchantId", "00123");
        assertThatThrownBy(() -> service.updateProviderAccount(ACCOUNT,
                providerWrite(null, 1L, Status.ENABLED, outcome, Map.of("merchantSecretKey", "sample-key"))))
                .hasMessageContaining("Merchant Site ID");
        verify(repository, never()).updateAccount(anyLong(), any(), anyLong());
        verifyNoInteractions(credentialCrypto);
    }

    @Test
    void newCredentialRequiresPermissionAndUnknownSecretCannotEnterPlainSettings() {
        setupProvider("CHARGEBACKHIT", Status.DRAFT, 1L);
        var settings = Map.<String, Object>of("usage", List.of("api"));
        assertThatThrownBy(() -> service.updateProviderAccount(ACCOUNT,
                providerWrite(null, 1L, Status.DRAFT, settings, Map.of("secretKey", "sample-key"))))
                .hasMessageContaining("权限");
        var unsafe = Map.<String, Object>of("usage", List.of("api"), "secretKey", "sample-key");
        assertThatThrownBy(() -> service.updateProviderAccount(ACCOUNT,
                providerWrite(null, 1L, Status.DRAFT, unsafe, Map.of())))
                .hasMessageContaining("无关").hasMessageNotContaining("sample-key");
        verify(repository, never()).saveProviderConfig(anyLong(), anyMap(), nullable(Long.class), anyBoolean());
        verifyNoInteractions(credentialCrypto);
    }

    @Test
    void chargebackhitRotationKeepsBlankCredentialAndReturnsOnlyRegistration() {
        setupProvider("CHARGEBACKHIT", Status.ENABLED, 3L);
        InternalAuthContextHolder.get().setPermissions(List.of("channel:alert-platform:credential"));
        var settings = Map.<String, Object>of("usage", List.of("api"));
        when(repository.providerCredentialCiphers(ACCOUNT)).thenReturn(Map.of("publicKey", "previous-public-cipher", "secretKey", "previous-cipher"));
        when(repository.providerConfigSummaries(List.of(ACCOUNT)))
                .thenReturn(List.of(new ProviderConfigSummary(ACCOUNT, "{\"usage\":[\"api\"]}", "[\"publicKey\",\"secretKey\"]", true)));
        when(credentialCrypto.encrypt(ACCOUNT, "provider-account:CHARGEBACKHIT", "secretKey", "replacement-sample"))
                .thenReturn("replacement-cipher");

        var saved = service.updateProviderAccount(ACCOUNT, providerWrite(null, 3L, Status.ENABLED, settings,
                Map.of("publicKey", " ", "secretKey", "replacement-sample")));

        verify(repository).insertProviderCredential(ACCOUNT, 3L, Map.of("publicKey", "previous-public-cipher", "secretKey", "replacement-cipher"), ACTOR);
        verify(repository).saveProviderConfig(ACCOUNT, settings, 3L, true);
        verify(credentialCrypto, times(1)).encrypt(anyLong(), anyString(), anyString(), anyString());
        assertThat(saved.providerConfig().registered()).containsExactly("publicKey", "secretKey");
        assertThat(saved.providerConfig().values()).isEqualTo(settings);
        assertThat(saved.toString()).doesNotContain("replacement", "cipher");
    }

    @Test
    void managedAccountsCannotBypassReadinessAndLegacySourcesRequireValidation() {
        setupProvider("NUVEI", Status.ENABLED, 1L);
        assertThatThrownBy(() -> service.updateAccount(ACCOUNT, new AccountUpdate("MER-FIXTURE", Status.ENABLED, 1L)))
                .hasMessageContaining("校验启用");
        verify(repository, never()).updateAccount(anyLong(), any(), anyLong());
        var mid = new Mid(39, "SCOTT_CHANNEL_A", "MID-DEMO", "Demo", "ACQUIRING", "VISA", true);
        var legacy = new Source(32, ACCOUNT, PLATFORM, "Nuvei", "NUVEI", Environment.TEST, "MER-FIXTURE",
                Status.ENABLED, Status.ENABLED, true, true, 13, Product.RDR, Coverage.CONFIRMED, null, false);
        var ready = new Source(33, ACCOUNT + 1, PLATFORM, "Nuvei", "NUVEI", Environment.TEST, "MER-READY",
                Status.ENABLED, Status.ENABLED, true, true, 13, Product.RDR, Coverage.CONFIRMED, null, true);
        when(repository.mid(39L)).thenReturn(Optional.of(mid));
        when(repository.sources(Environment.TEST, Product.RDR, null)).thenReturn(List.of(legacy, ready));
        assertThat(service.sources(39L, Product.RDR, null)).containsExactly(ready.option());
    }

    @Test
    void disablingOneProviderProductKeepsTheOtherAndLeavesSharedCipherUntouched() {
        var account = setupProvider("NUVEI", Status.ENABLED, 3L);
        var ethocaCapability = new PlatformProduct(12, PLATFORM, Product.ETHOCA, true, Coverage.CONFIRMED);
        var rdrCapability = new PlatformProduct(13, PLATFORM, Product.RDR, true, Coverage.CONFIRMED);
        var ethoca = product(31, 12, Product.ETHOCA, Status.ENABLED);
        var rdr = product(32, 13, Product.RDR, Status.ENABLED);
        var settings = Map.<String, Object>of("usage", List.of("dmn"), "dmnEvents", List.of("externalEthoca", "externalRdr"),
                "dmnUrl", "https://example.invalid/nuvei");
        var ciphers = Map.of("merchantSecretKey", "previous-cipher");
        when(repository.providerCredentialCiphers(ACCOUNT)).thenReturn(ciphers);
        when(repository.platformProducts(List.of(PLATFORM))).thenReturn(List.of(ethocaCapability, rdrCapability));
        when(repository.accountProducts(List.of(ACCOUNT))).thenReturn(List.of(ethoca, rdr));
        var input = new ProviderAccountWrite(null, 3L, "MER-FIXTURE", Status.ENABLED,
                List.of(new ProviderProductInput(Product.ETHOCA, Status.ENABLED), new ProviderProductInput(Product.RDR, Status.DISABLED)),
                settings, Map.of());

        service.updateProviderAccount(ACCOUNT, input);

        verify(repository).saveAccountProduct(account, rdrCapability, rdr, Status.DISABLED, rdr.delivery());
        verify(repository, times(1)).saveAccountProduct(any(), any(), any(), any(), any());
        verify(repository).saveProviderConfig(ACCOUNT, settings, 2L, true);
        verify(repository, never()).insertProviderCredential(anyLong(), anyLong(), anyMap(), anyLong());
        verifyNoInteractions(credentialCrypto);
    }

    @Test
    void chargeflowVersionsRequireDifferentCredentialSlots() {
        var legacy = DisputeAlertProviderRules.configuration("CHARGEFLOW", Map.of("apiVersion", "2025-04-01",
                "usage", List.of("signedApi")), true);
        assertThat(legacy.requiredCredentials()).containsExactlyInAnyOrder("apiAccessKey", "apiSecretKey");
        var current = DisputeAlertProviderRules.configuration("CHARGEFLOW", Map.of("apiVersion", "v3", "usage", List.of("api", "webhook"),
                "webhookUrl", "https://example.invalid/chargeflow", "webhookEvents", List.of("alerts.created")), true);
        assertThat(current.requiredCredentials()).containsExactlyInAnyOrder("bearerKey", "webhookSigningSecret");
        assertThatThrownBy(() -> current.validateCredentials(legacy.requiredCredentials())).hasMessageContaining("接入凭据");
    }

    private Account setupProvider(String provider, Status status, long version) {
        var platform = new Platform(PLATFORM, provider, provider, PlatformType.AGGREGATOR, true, null, null, 1, null);
        var row = new Account(ACCOUNT, PLATFORM, provider, provider, true, Environment.TEST, "MER-FIXTURE",
                status, status == Status.ENABLED ? 2L : null, false, version, null);
        when(repository.platform(PLATFORM, true)).thenReturn(Optional.of(platform));
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(row));
        when(repository.platformProducts(List.of(PLATFORM))).thenReturn(List.of(
                new PlatformProduct(13, PLATFORM, Product.ETHOCA, true, Coverage.CONFIRMED)));
        return row;
    }

    private ProviderAccountWrite providerWrite(Long platformId, Long version, Status status,
                                               Map<String, Object> settings, Map<String, String> credentials) {
        return new ProviderAccountWrite(platformId, version, "MER-FIXTURE", status,
                List.of(new ProviderProductInput(Product.ETHOCA, Status.DRAFT)), settings, credentials);
    }

    @Test
    void disabledRdrLeavesEthocaAvailableAndDoesNotTouchGroupCredentials() {
        Platform platform = platform(null, 1);
        Account account = account(2L, 3);
        PlatformProduct rdr = new PlatformProduct(13, PLATFORM, Product.RDR, true, Coverage.CONFIRMED);
        AccountProduct ethoca = product(31, 12, Product.ETHOCA, Status.ENABLED);
        AccountProduct previous = product(32, 13, Product.RDR, Status.ENABLED);
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(account));
        when(repository.platform(PLATFORM, true)).thenReturn(Optional.of(platform));
        when(repository.platformProduct(PLATFORM, Product.RDR)).thenReturn(Optional.of(rdr));
        when(repository.accountProducts(List.of(ACCOUNT))).thenReturn(List.of(ethoca, previous),
                List.of(ethoca, product(32, 13, Product.RDR, Status.DISABLED)));
        when(repository.currentCredentials(List.of(ACCOUNT))).thenReturn(List.of(new CredentialMetadata(ACCOUNT, 2, "public-fingerprint", null)));

        AccountView updated = service.updateAccountProduct(ACCOUNT, Product.RDR, new AccountProductUpdate(Status.DISABLED, null, 3L));
        assertThat(updated.products()).extracting(AccountProductView::available).containsExactly(true, false);
        assertThat(updated.credentials().version()).isEqualTo(2L);
        verify(repository).saveAccountProduct(account, rdr, previous, Status.DISABLED, previous.delivery());
        verifyNoInteractions(credentialCrypto);
    }

    @Test
    void absentKeyAndStaleVersionFailBeforeCallingSecretStore() {
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(account(null, 2)));
        assertThatThrownBy(() -> service.replaceCredentials(ACCOUNT, new CredentialUpdate(1L, "public-fixture", "private-fixture")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("刷新");
        assertThatThrownBy(() -> service.replaceCredentials(ACCOUNT, new CredentialUpdate(2L, "public-fixture", null)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("首次登记");
        verifyNoInteractions(credentialCrypto);
        verify(repository, never()).insertCredential(any(), anyLong());
    }

    @Test
    void privateKeyRotationKeepsPublicCipherAndReference() {
        Account account = account(2L, 3);
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(account));
        when(repository.credential(ACCOUNT, 2L)).thenReturn(Optional.of(new Credential(ACCOUNT, 2,
                "prior-public-reference", "prior-private-reference", "prior-public-cipher", "prior-private-cipher",
                "prior-fingerprint", null)));
        when(credentialCrypto.encrypt(eq(ACCOUNT), anyString(), eq("private"), eq("replacement-fixture")))
                .thenReturn("replacement-cipher");

        service.replaceCredentials(ACCOUNT, new CredentialUpdate(3L, null, "replacement-fixture"));
        var version = ArgumentCaptor.forClass(Credential.class);
        var reference = ArgumentCaptor.forClass(String.class);
        var order = inOrder(repository, credentialCrypto);
        order.verify(repository).lockAccount(ACCOUNT);
        order.verify(credentialCrypto).encrypt(eq(ACCOUNT), reference.capture(), eq("private"), eq("replacement-fixture"));
        order.verify(repository).insertCredential(version.capture(), eq(ACTOR));
        assertThat(version.getValue().version()).isEqualTo(3L);
        assertThat(version.getValue().publicSecretRef()).isEqualTo("prior-public-reference");
        assertThat(version.getValue().publicKeyCipher()).isEqualTo("prior-public-cipher");
        assertThat(version.getValue().privateSecretRef()).isEqualTo(reference.getValue());
        assertThat(version.getValue().privateKeyCipher()).isEqualTo("replacement-cipher");
        assertThat(version.getValue().publicKeyFingerprint()).isEqualTo("prior-fingerprint");
    }

    @Test
    void encryptionFailureNeverPublishesCredentialVersion() {
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(account(null, 2)));
        when(credentialCrypto.encrypt(eq(ACCOUNT), anyString(), eq("public"), eq("public-fixture"))).thenReturn("public-cipher");
        when(credentialCrypto.encrypt(eq(ACCOUNT), anyString(), eq("private"), eq("private-fixture")))
                .thenThrow(new IllegalStateException("fixture encryption unavailable"));

        assertThatThrownBy(() -> service.replaceCredentials(ACCOUNT, new CredentialUpdate(2L, "public-fixture", "private-fixture")))
                .hasMessage("fixture encryption unavailable");
        verify(repository, never()).insertCredential(any(), anyLong());
    }

    @Test
    void legacyReferencesRequireBothKeysAndPreserveVersionHistory() {
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(account(2L, 3)));
        when(repository.credential(ACCOUNT, 2L)).thenReturn(Optional.of(new Credential(ACCOUNT, 2,
                "old-public-ref", "old-private-ref", null, null, "old-fingerprint", null)));
        assertThatThrownBy(() -> service.replaceCredentials(ACCOUNT, new CredentialUpdate(3L, null, "private-fixture")))
                .hasMessageContaining("须同时填写公钥和私钥");
        verifyNoInteractions(credentialCrypto);

        when(credentialCrypto.encrypt(eq(ACCOUNT), anyString(), eq("public"), eq("public-fixture"))).thenReturn("public-cipher");
        when(credentialCrypto.encrypt(eq(ACCOUNT), anyString(), eq("private"), eq("private-fixture"))).thenReturn("private-cipher");
        service.replaceCredentials(ACCOUNT, new CredentialUpdate(3L, "public-fixture", "private-fixture"));

        var captured = ArgumentCaptor.forClass(Credential.class);
        verify(repository).insertCredential(captured.capture(), eq(ACTOR));
        assertThat(captured.getValue().version()).isEqualTo(3L);
        assertThat(captured.getValue().publicKeyCipher()).isEqualTo("public-cipher");
        assertThat(captured.getValue().privateKeyCipher()).isEqualTo("private-cipher");
    }

    @Test
    void bindingReferencesExistingAccountProductAndPreservesLeadingZeroIdentifiers() {
        var mid = new Mid(39, "SCOTT_CHANNEL_A", "MID-DEMO", "Demo", "ACQUIRING", "VISA", true);
        var source = new Source(32, ACCOUNT, PLATFORM, "Verxa Shield", SHIELD, Environment.TEST, "MER-TEST",
                Status.ENABLED, Status.ENABLED, true, true, 13, Product.RDR, Coverage.CONFIRMED, 2L, false);
        var mapping = new IdentityInput(IdentityType.BIN_CAID, "Bank", null, "001234", "000045", true);
        var descriptor = new DescriptorInput("MERCHANT*SUBSCRIPTION", true);
        var input = new BindingInput(39L, 32L, null, "MER-TEST", Enrollment.CONFIRMED, "DEMO-CONFIRMATION",
                List.of(mapping), List.of(descriptor), null);
        var activeBinding = new Binding(51, 39, 32, "", "MER-TEST", Enrollment.CONFIRMED, "DEMO-CONFIRMATION",
                Status.DRAFT, 1, null, 1, 1, true);
        when(repository.mid(39L)).thenReturn(Optional.of(mid));
        when(repository.source(32L)).thenReturn(Optional.of(source));
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(account(2L, 3)));
        when(repository.platform(PLATFORM, true)).thenReturn(Optional.of(platform(null, 1)));
        when(repository.insertBinding(input, "", ACTOR)).thenReturn(51L);
        when(repository.binding(51L)).thenReturn(Optional.of(new BindingContext(activeBinding, mid, source)));

        BindingDetail result = service.createBinding(input);
        assertThat(result.binding().source().accountProductId()).isEqualTo(32L);
        assertThat(result.binding().effective()).isFalse();
        verify(repository).replaceMappings(51L,
                List.of(new IdentityInput(IdentityType.BIN_CAID, "Bank", "", "001234", "000045", true)), List.of(descriptor));
        assertThatThrownBy(() -> service.setBindingStatus(51L, new BindingStatusUpdate(Status.ENABLED, 2L)))
                .hasMessageContaining("刷新");
        verify(repository, never()).setBindingStatus(anyLong(), any(), anyLong());
    }

    @ParameterizedTest
    @CsvSource({"dev, TEST", "test, TEST", "uat, TEST", "sample, TEST", "prod, PRODUCTION"})
    void sourceOptionsUseExistingDeploymentProfile(String profile, Environment environment) {
        var mid = new Mid(39, "SCOTT_CHANNEL_A", "MID-DEMO", "Demo", "ACQUIRING", "VISA", true);
        var source = new Source(32, ACCOUNT, PLATFORM, "Verxa Shield", SHIELD, environment, "MER-FIXTURE",
                Status.ENABLED, Status.ENABLED, true, true, 13, Product.RDR, Coverage.CONFIRMED, 2L, false);
        when(repository.mid(39L)).thenReturn(Optional.of(mid));
        when(repository.sources(environment, Product.RDR, null)).thenReturn(List.of(source));

        assertThat(serviceForProfiles(profile, "metrics").sources(39L, Product.RDR, null))
                .containsExactly(source.option());
        verify(repository).sources(environment, Product.RDR, null);
    }

    @Test
    void productionCannotCreateOrEnableBindingWithTestSource() {
        var mid = new Mid(39, "SCOTT_CHANNEL_A", "MID-DEMO", "Demo", "ACQUIRING", "VISA", true);
        var source = new Source(32, ACCOUNT, PLATFORM, "Verxa Shield", SHIELD, Environment.TEST, "MER-TEST",
                Status.ENABLED, Status.ENABLED, true, true, 13, Product.RDR, Coverage.CONFIRMED, 2L, false);
        var binding = new Binding(51, 39, 32, "", "MER-TEST", Enrollment.CONFIRMED, "DEMO-CONFIRMATION",
                Status.DRAFT, 1, null, 1, 0, true);
        var input = new BindingInput(39L, 32L, null, "MER-TEST", Enrollment.CONFIRMED, "DEMO-CONFIRMATION",
                List.of(), List.of(), null);
        when(repository.mid(39L)).thenReturn(Optional.of(mid));
        when(repository.source(32L)).thenReturn(Optional.of(source));
        when(repository.account(ACCOUNT)).thenReturn(Optional.of(account(2L, 3)));
        when(repository.platform(PLATFORM, true)).thenReturn(Optional.of(platform(null, 1)));
        when(repository.binding(51L)).thenReturn(Optional.of(new BindingContext(binding, mid, source)));
        var production = serviceForProfiles("prod");

        assertThat(production.binding(51L).binding().sourceAvailable()).isFalse();
        assertThatThrownBy(() -> production.createBinding(input)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("同环境");
        assertThatThrownBy(() -> production.setBindingStatus(51L, new BindingStatusUpdate(Status.ENABLED, 1L)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("同环境");
        verify(repository, never()).insertBinding(any(), anyString(), anyLong());
        verify(repository, never()).setBindingStatus(anyLong(), any(), anyLong());
    }

    @Test
    void unavailableOrConflictingDeploymentProfilesDoNotSelectSources() {
        when(repository.mid(39L)).thenReturn(Optional.of(
                new Mid(39, "SCOTT_CHANNEL_A", "MID-DEMO", "Demo", "ACQUIRING", "VISA", true)));

        for (String[] profiles : new String[][]{{}, {"metrics"}, {"prod", "uat"}}) {
            assertThat(serviceForProfiles(profiles).sources(39L, Product.RDR, null)).isEmpty();
        }
        verify(repository, never()).sources(any(), any(), any());
    }

    @Test
    void limitedDataScopeNeverReachesPlatformImages() {
        when(scopes.resolve(InternalAuthContextHolder.get())).thenReturn(AdminMerchantDataScope.limited(Set.of("ONE_MERCHANT")));

        assertThatThrownBy(() -> service.platformLogo(PLATFORM)).hasMessageContaining("全量数据范围");
        verifyNoInteractions(repository, images, storage);
    }

    @Test
    void replacementPublishesOpaqueLogoVersionAndRegistersOldImageCleanup() {
        String previous = "images/dispute-alert-platform/11/" + "a".repeat(32) + "-" + "b".repeat(64) + ".png";
        String current = "images/dispute-alert-platform/11/" + "c".repeat(32) + "-" + "d".repeat(64) + ".png";
        MultipartFile file = mock(MultipartFile.class);
        byte[] png = {1, 2};
        when(repository.platform(PLATFORM, true)).thenReturn(Optional.of(platform(previous, 2)));
        when(repository.platform(PLATFORM, false)).thenReturn(Optional.of(platform(current, 3)));
        when(images.process(file, false)).thenReturn(png);
        when(dictionaries.listDictData(any())).thenReturn(List.of(dictBrand("VISA", "Visa")));
        when(storage.write("dispute-alert-platform/11", png)).thenReturn(current);
        var input = new PlatformInput(SHIELD, "Verxa Shield", PlatformType.AGGREGATOR, true, null,
                List.of(new PlatformProductInput(Product.RDR, Coverage.CONFIRMED, List.of("VISA"), true)), 2L);

        assertThat(service.updatePlatform(PLATFORM, input, file, false).logoVersion()).isEqualTo("c".repeat(32));
        var order = inOrder(repository, images, storage);
        order.verify(images).process(file, false);
        order.verify(storage).write("dispute-alert-platform/11", png);
        order.verify(repository).updatePlatformLogo(PLATFORM, current);
        order.verify(storage).deleteAfterCommit(previous);
    }

    @Test
    void optionsUseEnabledLocalizedDictionaryAndExcludeUnknownBrand() {
        when(dictionaries.listDictData(any())).thenReturn(List.of(dictBrand("CARTES_BANCAIRES", "Cartes Bancaires"),
                dictBrand("UNKNOWN", "Unknown")));

        assertThat(service.cardBrandOptions("en-US")).extracting(CardBrandOption::value).containsExactly("CARTES_BANCAIRES");
        var query = ArgumentCaptor.forClass(SysDictDataQueryRequest.class);
        verify(dictionaries).listDictData(query.capture());
        assertThat(query.getValue().getDictType()).isEqualTo("card_brand");
        assertThat(query.getValue().getStatus()).isEqualTo(1);
        assertThat(query.getValue().getLocale()).isEqualTo("en-US");
    }

    @Test
    void configuredCardsMustBeEnabledInTheDictionaryAndAllowedByTheProduct() {
        when(repository.insertPlatform(anyString(), any(), anyLong())).thenReturn(PLATFORM);
        when(repository.platform(PLATFORM, false)).thenReturn(Optional.of(platform(null, 1)));
        when(dictionaries.listDictData(any())).thenReturn(List.of(dictBrand("CARTES_BANCAIRES", "Cartes Bancaires"),
                dictBrand("VISA", "Visa")));
        var ethoca = new PlatformProductInput(Product.ETHOCA, Coverage.CONFIRMED, List.of("CARTES_BANCAIRES"), true);
        var rdr = new PlatformProductInput(Product.RDR, Coverage.CONFIRMED, List.of("CARTES_BANCAIRES"), true);
        var unregistered = new PlatformProductInput(Product.ETHOCA, Coverage.CONFIRMED, List.of("UNREGISTERED"), true);

        service.createPlatform(new PlatformInput("BANK_DEMO", "Demo", PlatformType.AGGREGATOR, true, null, List.of(ethoca), null), null);
        verify(repository).savePlatformProduct(PLATFORM, null, ethoca, List.of("CARTES_BANCAIRES"));
        assertThatThrownBy(() -> service.createPlatform(new PlatformInput("BANK_DEMO", "Demo",
                PlatformType.AGGREGATOR, true, null, List.of(rdr), null), null)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.createPlatform(new PlatformInput("BANK_DEMO", "Demo",
                PlatformType.AGGREGATOR, true, null, List.of(unregistered), null), null)).isInstanceOf(ServiceException.class);
    }

    private DisputeAlertApplicationService serviceForProfiles(String... profiles) {
        var deployment = new MockEnvironment();
        deployment.setActiveProfiles(profiles);
        return new DisputeAlertApplicationService(repository, credentialCrypto, deployment, scopes, images, storage, dictionaries);
    }

    private static SysDictDataDTO dictBrand(String code, String label) {
        var row = new SysDictDataDTO();
        row.setDictValue(code);
        row.setDictLabel(label);
        return row;
    }

    private static Account account(Long credential, long version) {
        return new Account(ACCOUNT, PLATFORM, SHIELD, "Verxa Shield", true, Environment.TEST, "MER-TEST",
                credential == null ? Status.DRAFT : Status.ENABLED, credential, credential != null, version, null);
    }

    private static AccountProduct product(long id, long capabilityId, Product product, Status status) {
        return new AccountProduct(id, ACCOUNT, capabilityId, product, status, true, Transport.HTTPS,
                null, null, null, null, null);
    }

    private static Platform platform(String key, long version) {
        return new Platform(PLATFORM, SHIELD, "Verxa Shield", PlatformType.AGGREGATOR, true, null, key, version, null);
    }
}
