package com.scott.payment.admin.application.disputealert;

import com.baomidou.dynamic.datasource.annotation.DS;
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
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.ManagedImageReference;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.web.upload.ManagedImageStore;
import com.scott.payment.component.web.upload.SafeImageProcessor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.scott.payment.admin.application.disputealert.DisputeAlertRules.*;

/** 管理端拒付预警配置编排。配置读取使用主库，避免保存后仍返回旧状态。 */
@Service
@DS(DataSourceName.MASTER)
public class DisputeAlertApplicationService {

    private final DisputeAlertRepository repository;
    private final DisputeAlertCredentialCrypto credentialCrypto;
    private final Environment deploymentEnvironment;
    private final AdminMerchantDataScopeResolver scopes;
    private final SafeImageProcessor images;
    private final ManagedImageStore imageStorage;
    private final AdminDictService dictionaries;

    public DisputeAlertApplicationService(DisputeAlertRepository repository, DisputeAlertCredentialCrypto credentialCrypto,
                                          org.springframework.core.env.Environment deployment, AdminMerchantDataScopeResolver scopes,
                                          SafeImageProcessor images, ManagedImageStore imageStorage, AdminDictService dictionaries) {
        this.repository = repository;
        this.credentialCrypto = credentialCrypto;
        this.deploymentEnvironment = resolveDeploymentEnvironment(deployment.getActiveProfiles());
        this.scopes = scopes;
        this.images = images;
        this.imageStorage = imageStorage;
        this.dictionaries = dictionaries;
    }

    /** 返回预警平台使用的真实卡品牌字典项及标准图标配置。 */
    public List<CardBrandOption> cardBrandOptions(String locale) {
        actor();
        return activeCardBrands(locale);
    }

    private List<CardBrandOption> activeCardBrands(String locale) {
        var query = new SysDictDataQueryRequest();
        query.setDictType("card_brand");
        query.setStatus(1);
        query.setLocale("en-US".equals(locale) ? locale : "zh-CN");
        return dictionaries.listDictData(query).stream()
                .filter(row -> isCardBrand(row.getDictValue()))
                .map(row -> new CardBrandOption(row.getDictValue(), row.getDictLabel(), row.getExtraJson()))
                .toList();
    }

    public PageResult<PlatformView> platforms(PageRequest page, String keyword, PlatformType type, Product product) {
        actor();
        PageResult<Platform> rows = repository.platforms(page, trim(keyword), type, product);
        return result(rows, platformViews(rows.getRecords()));
    }

    public PlatformView platform(long id) {
        actor();
        return platformViews(List.of(required(repository.platform(id, false)))).get(0);
    }

    /** 新增平台并保存可选 Logo，图片引用随平台配置提交。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public PlatformView createPlatform(PlatformInput input, MultipartFile logo) {
        long actor = actor();
        String code = trim(input.code());
        if (code == null || !code.toUpperCase(Locale.ROOT).matches("[A-Z][A-Z0-9_-]{1,31}")) {
            throw invalid("请填写有效的平台编码");
        }
        Map<Product, PlatformProductInput> products = productMap(input.products(), PlatformProductInput::product);
        try {
            long id = repository.insertPlatform(code.toUpperCase(Locale.ROOT), input, actor);
            savePlatformProducts(id, products, List.of());
            if (logo != null) {
                repository.updatePlatformLogo(id, imageStorage.write(platformLogoScope(id), images.process(logo, false)));
            }
            return platform(id);
        } catch (DuplicateKeyException duplicate) {
            throw invalid("该平台编码已有配置");
        }
    }

    /** 同事务维护平台资料与 Logo，提交后清理旧图片。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public PlatformView updatePlatform(long id, PlatformInput input, MultipartFile logo, boolean removeLogo) {
        long actor = actor();
        Platform platform = required(repository.platform(id, true));
        checkVersion(platform.version(), input.version());
        if (logo != null && removeLogo) {
            throw invalid("请单独上传或移除平台 Logo");
        }
        if (trim(input.code()) != null && !platform.code().equals(input.code())) {
            throw invalid("平台编码在创建后固定");
        }
        List<PlatformProduct> existing = repository.platformProducts(List.of(id));
        savePlatformProducts(id, productMap(input.products(), PlatformProductInput::product), existing);
        repository.updatePlatform(id, input, actor);
        if (logo != null || removeLogo) {
            String key = logo == null ? null : imageStorage.write(platformLogoScope(id), images.process(logo, false));
            repository.updatePlatformLogo(id, key);
            imageStorage.deleteAfterCommit(platform.logoObjectKey());
        }
        return platform(id);
    }

    /** 在运营数据范围内读取当前平台自己的受管 Logo。 */
    public byte[] platformLogo(long id) {
        actor();
        Platform platform = repository.platform(id, false).orElseThrow(ManagedImageStore::missing);
        return imageStorage.read(platform.logoObjectKey(), platformLogoScope(id));
    }

    private static String platformLogoScope(long id) {
        return "dispute-alert-platform/" + id;
    }

    public PageResult<AccountView> accounts(PageRequest page, Long platformId, Product product, String keyword) {
        actor();
        PageResult<Account> accounts = repository.accounts(page, platformId, deploymentEnvironment, product, trim(keyword));
        return result(accounts, accountViews(accounts.getRecords()));
    }

    public AccountView account(long id) {
        actor();
        return accountViews(List.of(required(repository.account(id)))).get(0);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AccountView createAccount(AccountCreate input) {
        long actor = actor();
        requireDeploymentEnvironment();
        Platform platform = required(repository.platform(input.platformId(), true));
        if (DisputeAlertProviderRules.supported(platform.code())) throw invalid("请使用该平台的专用接入账号表单");
        validateAccountStatus(platform, input.status(), false);
        Map<Product, AccountProductInput> products = productMap(input.products(), AccountProductInput::product);
        Map<Product, PlatformProduct> capabilities = productMap(repository.platformProducts(List.of(platform.id())), PlatformProduct::product);
        try {
            long id = repository.insertAccount(input, deploymentEnvironment, actor);
            Account account = required(repository.account(id));
            for (AccountProductInput row : products.values()) {
                saveAccountProduct(platform, account, capabilities.get(row.product()), null, row.status(), row.delivery());
            }
            return account(id);
        } catch (DuplicateKeyException duplicate) {
            throw invalid("此平台已有相同的上游账号");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AccountView updateAccount(long id, AccountUpdate input) {
        long actor = actor();
        Platform platform = lockAccountPlatform(id);
        Account account = required(repository.account(id));
        checkVersion(account.version(), input.version());
        validateAccountStatus(platform, input.status(), account.credentialsRegistered());
        requireProviderReadiness(platform, id, input.status());
        validateAccountIdentifier(account, input.merchantNumber(), DisputeAlertProviderRules.supported(platform.code())
                && !account.merchantNumber().equals(input.merchantNumber().trim()) && !repository.providerCredentialCiphers(id).isEmpty());
        try {
            repository.updateAccount(id, input, actor);
            return account(id);
        } catch (DuplicateKeyException duplicate) {
            throw invalid("此平台已有相同的上游账号");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AccountView updateAccountProduct(long id, Product product, AccountProductUpdate input) {
        long actor = actor();
        Platform platform = lockAccountPlatform(id);
        Account account = required(repository.account(id));
        checkVersion(account.version(), input.version());
        PlatformProduct capability = required(repository.platformProduct(platform.id(), product));
        AccountProduct current = repository.accountProducts(List.of(id)).stream().filter(row -> row.product() == product).findFirst().orElse(null);
        requireProviderReadiness(platform, id, input.status());
        Delivery requested = input.delivery() == null && current != null ? current.delivery() : input.delivery();
        if (DisputeAlertProviderRules.supported(platform.code()) && input.delivery() != null) {
            throw invalid("请在该平台的专用表单维护接入配置");
        }
        saveAccountProduct(platform, account, capability, current, input.status(), requested);
        repository.touchAccount(id, actor);
        return account(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AccountView createProviderAccount(ProviderAccountWrite input) {
        long actor = actor();
        requireDeploymentEnvironment();
        if (input.platformId() == null || input.version() != null || input.status() == Status.DISABLED) {
            throw invalid("请选择平台并以草稿或启用状态新增接入账号");
        }
        Platform platform = required(repository.platform(input.platformId(), true));
        validateAccountStatus(platform, input.status(), false);
        ProviderSave config = providerSave(platform.code(), input, Map.of());
        var products = checkProviderProducts(platform, input, Map.of());
        try {
            long id = repository.insertAccount(new AccountCreate(platform.id(), input.merchantNumber(), input.status(), List.of()),
                    deploymentEnvironment, actor);
            Account account = required(repository.account(id));
            writeProviderConfig(account, config, input.status(), actor);
            writeProviderProducts(account, products, input.products(), Map.of());
            return account(id);
        } catch (DuplicateKeyException duplicate) {
            throw invalid("此平台已有相同的上游账号");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AccountView updateProviderAccount(long id, ProviderAccountWrite input) {
        long actor = actor();
        Platform platform = lockAccountPlatform(id);
        Account account = required(repository.account(id));
        if (input.version() == null || input.platformId() != null && input.platformId() != platform.id()) {
            throw invalid("接入账号的平台或版本无效，请重新打开表单");
        }
        checkVersion(account.version(), input.version());
        validateAccountStatus(platform, input.status(), false);
        if (!DisputeAlertProviderRules.supported(platform.code())) throw invalid("该平台使用现有的接入账号维护方式");
        if (input.status() == Status.DRAFT && account.status() != Status.DRAFT) {
            throw invalid("已维护的接入账号请通过停用操作调整状态");
        }
        Map<String, String> savedCiphers = repository.providerCredentialCiphers(id);
        validateAccountIdentifier(account, input.merchantNumber(), !savedCiphers.isEmpty());
        ProviderSave config = providerSave(platform.code(), input, savedCiphers);
        Map<Product, AccountProduct> existing = productMap(repository.accountProducts(List.of(id)), AccountProduct::product);
        var products = checkProviderProducts(platform, input, existing);
        try {
            repository.updateAccount(id, new AccountUpdate(input.merchantNumber(), input.status(), input.version()), actor);
            writeProviderConfig(account, config, input.status(), actor);
            writeProviderProducts(account, products, input.products(), existing);
            return account(id);
        } catch (DuplicateKeyException duplicate) {
            throw invalid("此平台已有相同的上游账号");
        }
    }

    private void validateAccountIdentifier(Account account, String merchantNumber, boolean providerCredentialsRegistered) {
        if (!account.merchantNumber().equals(merchantNumber.trim())
                && (account.credentialVersion() != null || providerCredentialsRegistered || repository.accountHasBinding(account.id()))) {
            throw invalid("已有凭据或 MID 绑定的账号需保留原账号标识，请另行登记新账号");
        }
    }

    private void requireProviderReadiness(Platform platform, long id, Status status) {
        if (status == Status.ENABLED && DisputeAlertProviderRules.supported(platform.code())
                && !repository.providerActivationValidated(id)) {
            throw invalid("请在维护接入账号中补齐对接配置并校验启用");
        }
    }

    private ProviderSave providerSave(String code, ProviderAccountWrite input, Map<String, String> savedCiphers) {
        var config = DisputeAlertProviderRules.configuration(code, input.settings(), input.status() == Status.ENABLED);
        Map<String, String> updates = config.updates(input.credentials());
        if (!updates.isEmpty()) {
            var permissions = InternalAuthContextHolder.get().getPermissions();
            if (permissions == null || !(permissions.contains("channel:alert-platform:credential") || permissions.contains("*:*:*"))) {
                throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "当前账号没有维护凭据的权限");
            }
        }
        Set<String> registered = new HashSet<>(savedCiphers.keySet());
        registered.addAll(updates.keySet());
        config.validateCredentials(registered);
        return new ProviderSave(config.values(), savedCiphers, updates);
    }

    private void writeProviderConfig(Account account, ProviderSave config, Status status, long actor) {
        Long version = account.credentialVersion();
        if (!config.credentialUpdates().isEmpty()) {
            Map<String, String> ciphers = new HashMap<>(config.savedCiphers());
            config.credentialUpdates().forEach((field, value) -> ciphers.put(field,
                    credentialCrypto.encrypt(account.id(), "provider-account:" + account.platformCode(), field, value)));
            version = version == null ? 1L : version + 1;
            repository.insertProviderCredential(account.id(), version, ciphers, actor);
        }
        repository.saveProviderConfig(account.id(), config.settings(), version, status == Status.ENABLED);
    }

    private Map<Product, PlatformProduct> checkProviderProducts(Platform platform, ProviderAccountWrite input,
                                                                Map<Product, AccountProduct> existing) {
        Map<Product, ProviderProductInput> requested = productMap(input.products(), ProviderProductInput::product);
        if (!requested.keySet().containsAll(existing.keySet())) throw invalid("已登记的账号产品请通过停用操作管理");
        Map<Product, PlatformProduct> capabilities = productMap(repository.platformProducts(List.of(platform.id())), PlatformProduct::product);
        for (ProviderProductInput product : requested.values()) {
            AccountProduct current = existing.get(product.product());
            PlatformProduct capability = capabilities.get(product.product());
            if (capability == null || product.status() == Status.ENABLED && input.status() == Status.ENABLED && !capability.enabled()) {
                throw invalid("该平台未提供或未启用此产品");
            }
            if (product.status() == Status.ENABLED && input.status() != Status.ENABLED
                    && (current == null || current.status() != Status.ENABLED)) {
                throw invalid("请先校验并启用接入账号");
            }
        }
        return capabilities;
    }

    private void writeProviderProducts(Account account, Map<Product, PlatformProduct> capabilities,
                                       List<ProviderProductInput> requested, Map<Product, AccountProduct> existing) {
        for (ProviderProductInput product : requested) {
            AccountProduct current = existing.get(product.product());
            if (current == null || current.status() != product.status()) {
                repository.saveAccountProduct(account, capabilities.get(product.product()), current, product.status(),
                        current == null ? delivery(null) : current.delivery());
            }
        }
    }

    private record ProviderSave(Map<String, Object> settings, Map<String, String> savedCiphers,
                                Map<String, String> credentialUpdates) {
        @Override
        public String toString() {
            return "ProviderSave";
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AccountView replaceCredentials(long id, CredentialUpdate input) {
        long actor = actor();
        // 同一账号串行发布凭据，账号元数据写入亦共用该行的版本。
        required(repository.account(id));
        repository.lockAccount(id);
        Account account = required(repository.account(id));
        checkVersion(account.version(), input.version());
        if (!SHIELD.equals(account.platformCode())) throw invalid("此平台尚未登记共用公私钥的凭据模板");
        String publicKey = trim(input.publicKey());
        String privateKey = trim(input.privateKey());
        if (publicKey == null && privateKey == null) throw invalid("请填写至少一项需要更新的凭据");
        Credential existing = repository.credential(id, account.credentialVersion()).orElse(null);
        if ((existing == null || !existing.isRegistered()) && (publicKey == null || privateKey == null)) {
            throw invalid("首次登记或凭据需重新登记时，须同时填写公钥和私钥");
        }

        String reference = UUID.randomUUID().toString();
        String publicCipher = publicKey == null ? existing.publicKeyCipher() : credentialCrypto.encrypt(id, reference, "public", publicKey);
        String privateCipher = privateKey == null ? existing.privateKeyCipher() : credentialCrypto.encrypt(id, reference, "private", privateKey);
        long version = account.credentialVersion() == null ? 1 : account.credentialVersion() + 1;
        repository.insertCredential(new Credential(id, version,
                publicKey == null ? existing.publicSecretRef() : reference,
                privateKey == null ? existing.privateSecretRef() : reference,
                publicCipher, privateCipher, publicKey == null ? existing.publicKeyFingerprint() : fingerprint(publicKey), null), actor);
        return account(id);
    }

    public List<String> midChannels() {
        actor();
        return repository.midChannels();
    }

    public List<MidOption> mids(String channelCode, String keyword) {
        actor();
        return repository.mids(trim(channelCode), trim(keyword)).stream().filter(DisputeAlertRules::eligibleMid).map(Mid::option).toList();
    }

    public List<SourceOption> sources(long midId, Product product, String keyword) {
        actor();
        requireMidProduct(product);
        Mid mid = requireMid(midId);
        if (deploymentEnvironment == null) return List.of();
        List<Source> sources = repository.sources(deploymentEnvironment, product, trim(keyword));
        Map<Long, List<String>> brands = brands(sources.stream().map(Source::platformProductId).distinct().toList());
        return sources.stream()
                .filter(source -> sourceAvailable(source, deploymentEnvironment)
                        && supportsMid(mid, source, brands.getOrDefault(source.platformProductId(), List.of())))
                .limit(100).map(Source::option).toList();
    }

    public PageResult<BindingSummary> bindings(PageRequest page, Long midId, Product product, Status status, String keyword) {
        actor();
        PageResult<BindingContext> rows = repository.bindings(page, midId, product, status, trim(keyword));
        Map<Long, List<String>> brands = brands(rows.getRecords().stream().map(row -> row.source().platformProductId()).distinct().toList());
        return result(rows, rows.getRecords().stream()
                .map(row -> bindingSummary(row, brands.getOrDefault(row.source().platformProductId(), List.of()))).toList());
    }

    public BindingDetail binding(long id) {
        actor();
        BindingContext row = required(repository.binding(id));
        return new BindingDetail(bindingSummary(row, sourceBrands(row.source())), repository.identifiers(id),
                repository.descriptors(id), repository.overlappingMids(id));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public BindingDetail createBinding(BindingInput input) {
        long actor = actor();
        Mid mid = requireMid(input.midConfigId());
        Source source = lockSource(input.accountProductId());
        requireMidProduct(source.product());
        requireSource(mid, source);
        validateBinding(input);
        List<IdentityInput> identifiers = identifiers(source.product(), input.identifiers());
        try {
            long id = repository.insertBinding(input, externalScope(input), actor);
            repository.replaceMappings(id, identifiers, input.descriptors());
            return binding(id);
        } catch (DuplicateKeyException duplicate) {
            throw invalid("同一渠道 MID、账号产品和外部商户范围已有登记，或存在重复标识");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public BindingDetail updateBinding(long id, BindingInput input) {
        long actor = actor();
        BindingContext row = lockBinding(id);
        Binding existing = row.binding();
        checkVersion(existing.version(), input.version());
        if (existing.midConfigId() != input.midConfigId() || existing.accountProductId() != input.accountProductId()
                || !existing.externalMerchantId().equals(externalScope(input))) {
            throw invalid("变更 MID、来源账号产品或外部商户范围时，请重新登记上游开通");
        }
        validateBinding(input);
        List<IdentityInput> mappings = identifiers(row.source().product(), input.identifiers());
        Status status = existing.status();
        if (status == Status.ENABLED && (input.enrollment() != Enrollment.CONFIRMED
                || !(mappings.stream().anyMatch(IdentityInput::enabled) || input.descriptors().stream().anyMatch(DescriptorInput::enabled)))) {
            throw invalid("请先停用配置，再撤销开通确认或移除最后一条有效映射");
        }
        repository.updateBinding(id, input, status, actor);
        repository.replaceMappings(id, mappings, input.descriptors());
        return binding(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public BindingDetail setBindingStatus(long id, BindingStatusUpdate input) {
        long actor = actor();
        BindingContext row = lockBinding(id);
        checkVersion(row.binding().version(), input.version());
        if (input.status() == Status.DRAFT) throw invalid("启停接口只接受启用或停用");
        if (input.status() == Status.ENABLED) {
            requireSource(row.mid(), row.source());
            if (row.binding().enrollment() != Enrollment.CONFIRMED || !row.binding().hasMapping()) {
                throw invalid("启用前请确认上游开通并登记至少一条有效映射");
            }
        }
        if (row.binding().status() != input.status()) repository.setBindingStatus(id, input.status(), actor);
        return binding(id);
    }

    private long actor() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null || !"ADMIN".equals(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "请使用管理端账号登录");
        }
        AdminMerchantDataScope scope = scopes.resolve(account);
        if (scope == null || !scope.allMerchants()) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "拒付预警配置仅提供给具有平台全量数据范围的授权账号");
        }
        return account.getAccountId();
    }

    private Platform lockAccountPlatform(long id) {
        Account initial = required(repository.account(id));
        Platform platform = required(repository.platform(initial.platformId(), true));
        repository.lockAccount(id);
        return platform;
    }

    private Source lockSource(long id) {
        Source initial = required(repository.source(id));
        lockAccountPlatform(initial.accountId());
        return required(repository.source(id));
    }

    private BindingContext lockBinding(long id) {
        BindingContext initial = required(repository.binding(id));
        lockSource(initial.binding().accountProductId());
        repository.lockBinding(id);
        return required(repository.binding(id));
    }

    private Mid requireMid(long id) {
        Mid mid = required(repository.mid(id));
        if (!eligibleMid(mid)) throw invalid("请选择有效的银行卡收单渠道 MID");
        return mid;
    }

    private void requireDeploymentEnvironment() {
        if (deploymentEnvironment == null) {
            throw invalid("无法识别当前 Admin 的部署环境，请检查启动配置");
        }
    }

    private void requireSource(Mid mid, Source source) {
        requireDeploymentEnvironment();
        if (!sourceAvailable(source, deploymentEnvironment) || !supportsMid(mid, source, sourceBrands(source))) {
            throw invalid("请选择同环境、产品已启用且适用该 MID 的来源账号");
        }
    }

    private List<String> sourceBrands(Source source) {
        return brands(List.of(source.platformProductId())).getOrDefault(source.platformProductId(), List.of());
    }

    private void validateAccountStatus(Platform platform, Status status, boolean credentialsRegistered) {
        if (status != Status.ENABLED) return;
        if (!platform.enabled()) throw invalid("请先启用预警平台");
        if (SHIELD.equals(platform.code()) && !credentialsRegistered) throw invalid("请先登记 Verxa Shield 共用凭据");
    }

    private void saveAccountProduct(Platform platform, Account account, PlatformProduct product,
                                    AccountProduct existing, Status status, Delivery details) {
        if (product == null || (status == Status.ENABLED && (!platform.enabled() || !product.enabled()))) {
            throw invalid("该平台未提供或未启用此产品");
        }
        if (status == Status.ENABLED && SHIELD.equals(platform.code()) && !account.credentialsRegistered()) {
            throw invalid("请先登记 Verxa Shield 共用凭据");
        }
        repository.saveAccountProduct(account, product, existing, status, delivery(details));
    }

    private void savePlatformProducts(long id, Map<Product, PlatformProductInput> productInputs, List<PlatformProduct> existing) {
        Map<Product, PlatformProduct> previous = productMap(existing, PlatformProduct::product);
        for (PlatformProduct row : existing) {
            if (!productInputs.containsKey(row.product())) {
                if (repository.platformProductInUse(row.id())) throw invalid("此产品已有账号，保留产品记录并使用停用操作");
                repository.removeUnassignedProduct(row.id());
            }
        }
        Set<String> enabledBrands = productInputs.values().stream().anyMatch(row -> row.cardBrands() != null && !row.cardBrands().isEmpty())
                ? activeCardBrands("zh-CN").stream().map(CardBrandOption::value).collect(Collectors.toSet())
                : Set.of();
        for (PlatformProductInput input : productInputs.values()) {
            repository.savePlatformProduct(id, previous.get(input.product()), input, cardBrands(input, enabledBrands));
        }
    }

    private List<PlatformView> platformViews(List<Platform> platforms) {
        List<PlatformProduct> products = repository.platformProducts(platforms.stream().map(Platform::id).toList());
        Map<Long, List<PlatformProduct>> grouped = products.stream().collect(Collectors.groupingBy(PlatformProduct::platformId));
        Map<Long, List<String>> brands = brands(products.stream().map(PlatformProduct::id).toList());
        return platforms.stream().map(row -> new PlatformView(row.id(), row.code(), row.name(), row.type(), row.enabled(),
                row.evidence(), ManagedImageReference.version(row.logoObjectKey()), row.version(), row.updateTime(), grouped.getOrDefault(row.id(), List.of()).stream()
                .map(p -> new PlatformProductView(p.id(), p.product(), p.coverage(), brands.getOrDefault(p.id(), List.of()), p.enabled()))
                .toList())).toList();
    }

    private List<AccountView> accountViews(List<Account> accounts) {
        List<Long> ids = accounts.stream().map(Account::id).toList();
        Map<Long, List<AccountProduct>> products = repository.accountProducts(ids).stream().collect(Collectors.groupingBy(AccountProduct::accountId));
        Map<Long, CredentialMetadata> credentials = repository.currentCredentials(ids).stream()
                .collect(Collectors.toMap(CredentialMetadata::accountId, Function.identity()));
        List<Long> providerIds = accounts.stream().filter(row -> DisputeAlertProviderRules.supported(row.platformCode()))
                .map(Account::id).toList();
        Map<Long, ProviderConfigSummary> configs = providerIds.isEmpty() ? Map.of() : repository.providerConfigSummaries(providerIds).stream()
                .collect(Collectors.toMap(ProviderConfigSummary::accountId, Function.identity()));
        return accounts.stream().map(row -> {
            CredentialMetadata credential = credentials.get(row.id());
            CredentialSummary summary = credential == null ? new CredentialSummary(false, false, null, null, null)
                    : new CredentialSummary(true, true, credential.publicKeyFingerprint(), credential.version(), credential.createTime());
            ProviderConfigSummary config = configs.get(row.id());
            boolean managed = DisputeAlertProviderRules.supported(row.platformCode());
            ProviderConfigView providerConfig = config != null ? config.view()
                    : managed ? new ProviderConfigView(Map.of(), List.of(), false) : null;
            boolean ready = !managed || providerConfig.activationValidated();
            return new AccountView(row.id(), row.platformId(), row.platformCode(), row.platformName(),
                    row.merchantNumber(), row.status(), row.version(), row.updateTime(), summary,
                    products.getOrDefault(row.id(), List.of()).stream()
                            .map(p -> new AccountProductView(p.id(), p.product(), p.status(), p.delivery(),
                                    row.platformEnabled() && p.platformProductEnabled() && row.status() == Status.ENABLED
                                            && p.status() == Status.ENABLED && ready && (!SHIELD.equals(row.platformCode()) || credential != null)))
                            .toList(), providerConfig);
        }).toList();
    }

    private BindingSummary bindingSummary(BindingContext context, List<String> brands) {
        Binding row = context.binding();
        boolean source = sourceAvailable(context.source(), deploymentEnvironment) && supportsMid(context.mid(), context.source(), brands);
        boolean effective = source && row.status() == Status.ENABLED && row.enrollment() == Enrollment.CONFIRMED && row.hasMapping();
        return new BindingSummary(row.id(), context.mid().option(), context.source().option(), row.externalMerchantId(),
                row.upstreamMerchantId(), row.enrollment(), row.enrollmentEvidence(), row.status(), source, effective,
                row.identifierCount(), row.descriptorCount(), row.version(), row.updateTime());
    }

    private Map<Long, List<String>> brands(List<Long> ids) {
        return repository.brands(ids).stream().collect(Collectors.groupingBy(Brand::platformProductId,
                Collectors.mapping(Brand::cardBrand, Collectors.toList())));
    }

    private static String externalScope(BindingInput input) {
        return Objects.requireNonNullElse(trim(input.externalMerchantId()), "");
    }

    private static <T> Map<Product, T> productMap(List<T> values, Function<T, Product> key) {
        Map<Product, T> mapped = new EnumMap<>(Product.class);
        for (T value : values) {
            if (mapped.putIfAbsent(key.apply(value), value) != null) throw invalid("产品不能重复配置");
        }
        return mapped;
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static <T> T required(Optional<T> value) {
        return value.orElseThrow(() -> new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "未找到对应的拒付预警配置"));
    }

    private static <T> PageResult<T> result(PageResult<?> paging, List<T> rows) {
        return PageResult.of(paging.getTotal(), paging.getPageNo(), paging.getPageSize(), rows);
    }
}
