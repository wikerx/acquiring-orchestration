package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.disputealert.DisputeAlertApplicationService;
import com.scott.payment.admin.dto.risk.DisputeAlertDtos.*;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.web.upload.ImageResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 运营管理端的拒付预警配置接口，依次校验页面及各项操作权限。 */
@RestController
@RequestMapping("/admin/risk/dispute-alert")
@Validated
public class AdminDisputeAlertConfigurationController {

    private static final String MODULE = "收单风控-拒付预警管理";
    private final DisputeAlertApplicationService application;

    public AdminDisputeAlertConfigurationController(DisputeAlertApplicationService application) {
        this.application = application;
    }

    @GetMapping("/platforms")
    @RequiresPermission("channel:alert-platform:list")
    public CommonResult<PageResult<PlatformView>> platforms(PageRequest page,
                                                             @RequestParam(name = "keyword", required = false) @Size(max = 128) String keyword,
                                                             @RequestParam(name = "type", required = false) PlatformType type,
                                                             @RequestParam(name = "product", required = false) Product product) {
        return success(application.platforms(page, keyword, type, product));
    }

    /** 提供预警平台表单使用的启用卡品牌，无需数据字典后台权限。 */
    @GetMapping("/platforms/card-brand-options")
    @RequiresPermission("channel:alert-platform:list")
    public CommonResult<List<CardBrandOption>> cardBrandOptions(
            @RequestParam(name = "locale", defaultValue = "zh-CN") @Size(max = 16) String locale) {
        return success(application.cardBrandOptions(locale));
    }

    @GetMapping("/platforms/{id}")
    @RequiresPermission("channel:alert-platform:list")
    public CommonResult<PlatformView> platform(@PathVariable("id") @Positive long id) {
        return success(application.platform(id));
    }

    @PostMapping("/platforms")
    @RequiresPermission("channel:alert-platform:add")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.CREATE, operation = "新增预警平台",
            recordRequest = false, recordResponse = false)
    public CommonResult<PlatformView> createPlatform(@RequestBody @Valid PlatformInput input) {
        return success(application.createPlatform(input, null));
    }

    @PutMapping("/platforms/{id}")
    @RequiresPermission("channel:alert-platform:edit")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.UPDATE, operation = "维护预警平台及产品",
            recordRequest = false, recordResponse = false)
    public CommonResult<PlatformView> updatePlatform(@PathVariable("id") @Positive long id, @RequestBody @Valid PlatformInput input) {
        return success(application.updatePlatform(id, input, null, false));
    }

    /** 接收平台资料以及可选 Logo 的新增请求。 */
    @PostMapping(value = "/platforms/with-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("channel:alert-platform:add")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.CREATE, operation = "新增预警平台及 Logo",
            recordRequest = false, recordResponse = false)
    public CommonResult<PlatformView> createPlatformWithLogo(@RequestPart("metadata") @Valid PlatformInput input,
                                                            @RequestPart(value = "file", required = false) MultipartFile file) {
        return success(application.createPlatform(input, file));
    }

    /** 与平台资料一并替换或移除 Logo。 */
    @PutMapping(value = "/platforms/{id}/with-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("channel:alert-platform:edit")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.UPDATE, operation = "修改预警平台及 Logo",
            recordRequest = false, recordResponse = false)
    public CommonResult<PlatformView> updatePlatformWithLogo(@PathVariable("id") @Positive long id,
                                                            @RequestPart("metadata") @Valid PlatformInput input,
                                                            @RequestPart(value = "file", required = false) MultipartFile file,
                                                            @RequestParam(value = "removeLogo", defaultValue = "false") boolean removeLogo) {
        return success(application.updatePlatform(id, input, file, removeLogo));
    }

    /** 返回授权范围内的安全 PNG 响应。 */
    @GetMapping("/platforms/{id}/logo")
    @RequiresPermission("channel:alert-platform:list")
    public ResponseEntity<byte[]> platformLogo(@PathVariable("id") @Positive long id) {
        return ImageResponses.png(application.platformLogo(id));
    }

    @GetMapping("/accounts")
    @RequiresPermission("channel:alert-platform:list")
    public CommonResult<PageResult<AccountView>> accounts(PageRequest page,
                                                           @RequestParam(name = "platformId", required = false) @Positive Long platformId,
                                                           @RequestParam(name = "product", required = false) Product product,
                                                           @RequestParam(name = "keyword", required = false) @Size(max = 128) String keyword) {
        return success(application.accounts(page, platformId, product, keyword));
    }

    @GetMapping("/accounts/{id}")
    @RequiresPermission("channel:alert-platform:list")
    public CommonResult<AccountView> account(@PathVariable("id") @Positive long id) {
        return success(application.account(id));
    }

    @PostMapping("/accounts")
    @RequiresPermission("channel:alert-platform:account")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.CREATE, operation = "新增预警接入账号",
            recordRequest = false, recordResponse = false)
    public CommonResult<AccountView> createAccount(@RequestBody @Valid AccountCreate input) {
        return success(application.createAccount(input));
    }

    @PutMapping("/accounts/{id}")
    @RequiresPermission("channel:alert-platform:account")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.UPDATE, operation = "维护预警接入账号",
            recordRequest = false, recordResponse = false)
    public CommonResult<AccountView> updateAccount(@PathVariable("id") @Positive long id, @RequestBody @Valid AccountUpdate input) {
        return success(application.updateAccount(id, input));
    }

    @PutMapping("/accounts/{id}/products/{product}")
    @RequiresPermission("channel:alert-platform:account")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.UPDATE, operation = "维护预警账号产品及启停",
            recordRequest = false, recordResponse = false)
    public CommonResult<AccountView> updateAccountProduct(@PathVariable("id") @Positive long id, @PathVariable("product") Product product,
                                                           @Valid @RequestBody AccountProductUpdate input) {
        return success(application.updateAccountProduct(id, product, input));
    }

    @PostMapping("/accounts/provider")
    @RequiresPermission("channel:alert-platform:account")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.CREATE, operation = "新增平台接入配置",
            recordRequest = false, recordResponse = false)
    public CommonResult<AccountView> createProviderAccount(@RequestBody @Valid ProviderAccountWrite input) {
        return success(application.createProviderAccount(input));
    }

    @PutMapping("/accounts/{id}/provider")
    @RequiresPermission("channel:alert-platform:account")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.UPDATE, operation = "维护平台接入配置",
            recordRequest = false, recordResponse = false)
    public CommonResult<AccountView> updateProviderAccount(@PathVariable("id") @Positive long id,
                                                          @RequestBody @Valid ProviderAccountWrite input) {
        return success(application.updateProviderAccount(id, input));
    }

    @PutMapping("/accounts/{id}/credentials")
    @RequiresPermission("channel:alert-platform:credential")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.UPDATE, operation = "登记预警账号共用凭据",
            recordRequest = false, recordResponse = false)
    public CommonResult<CredentialSummary> replaceCredentials(@PathVariable("id") @Positive long id,
                                                               @RequestBody @Valid CredentialUpdate input) {
        return success(application.replaceCredentials(id, input).credentials());
    }

    @GetMapping("/mids")
    @RequiresPermission("channel:dispute-alert:list")
    public CommonResult<List<MidOption>> mids(@RequestParam(name = "channelCode", required = false) @Size(max = 64) String channelCode,
                                              @RequestParam(name = "keyword", required = false) @Size(max = 128) String keyword) {
        return success(application.mids(channelCode, keyword));
    }

    @GetMapping("/mids/channels")
    @RequiresPermission("channel:dispute-alert:list")
    public CommonResult<List<String>> midChannels() {
        return success(application.midChannels());
    }

    @GetMapping("/source-options")
    @RequiresPermission("channel:dispute-alert:list")
    public CommonResult<List<SourceOption>> sources(@RequestParam("midConfigId") @Positive long midConfigId,
                                                    @RequestParam("product") Product product,
                                                    @RequestParam(name = "keyword", required = false) @Size(max = 128) String keyword) {
        return success(application.sources(midConfigId, product, keyword));
    }

    @GetMapping("/mid-bindings")
    @RequiresPermission("channel:dispute-alert:list")
    public CommonResult<PageResult<BindingSummary>> bindings(PageRequest page,
                                                              @RequestParam(name = "midConfigId", required = false) @Positive Long midConfigId,
                                                              @RequestParam(name = "product", required = false) Product product,
                                                              @RequestParam(name = "status", required = false) Status status,
                                                              @RequestParam(name = "keyword", required = false) @Size(max = 128) String keyword) {
        return success(application.bindings(page, midConfigId, product, status, keyword));
    }

    @GetMapping("/mid-bindings/{id}")
    @RequiresPermission("channel:dispute-alert:list")
    public CommonResult<BindingDetail> binding(@PathVariable("id") @Positive long id) {
        return success(application.binding(id));
    }

    @PostMapping("/mid-bindings")
    @RequiresPermission("channel:dispute-alert:add")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.CREATE, operation = "登记渠道 MID 预警开通及绑定",
            recordRequest = false, recordResponse = false)
    public CommonResult<BindingDetail> createBinding(@RequestBody @Valid BindingInput input) {
        return success(application.createBinding(input));
    }

    @PutMapping("/mid-bindings/{id}")
    @RequiresPermission("channel:dispute-alert:edit")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.UPDATE, operation = "维护渠道 MID 预警绑定",
            recordRequest = false, recordResponse = false)
    public CommonResult<BindingDetail> updateBinding(@PathVariable("id") @Positive long id, @RequestBody @Valid BindingInput input) {
        return success(application.updateBinding(id, input));
    }

    @PutMapping("/mid-bindings/{id}/status")
    @RequiresPermission("channel:dispute-alert:status")
    @OperationLog(moduleName = MODULE, businessType = OperationTypeConstants.UPDATE, operation = "启停渠道 MID 预警配置",
            recordRequest = false, recordResponse = false)
    public CommonResult<BindingDetail> setBindingStatus(@PathVariable("id") @Positive long id,
                                                         @RequestBody @Valid BindingStatusUpdate input) {
        return success(application.setBindingStatus(id, input));
    }
}
