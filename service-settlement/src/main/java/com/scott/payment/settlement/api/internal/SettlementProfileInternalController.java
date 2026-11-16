package com.scott.payment.settlement.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileCommandResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileCreateRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileReadinessResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ProfileVersionCreateRequest;
import com.scott.payment.settlement.application.SettlementProfileApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 仅供 service-admin 通过 HMAC 调用的结算档案命令和 readiness 接口。
 */
@RestController
@RequestMapping("/internal/settlement/v1/profiles")
public class SettlementProfileInternalController {

    private final SettlementProfileApplicationService profileApplicationService;

    /**
     * 创建结算档案内部接口。
     *
     * @param profileApplicationService 结算档案命令应用服务
     */
    public SettlementProfileInternalController(SettlementProfileApplicationService profileApplicationService) {
        this.profileApplicationService = profileApplicationService;
    }

    /**
     * 创建商户首个活动结算档案。
     *
     * @param request 账户、币种、日切配置和可信操作审计
     * @return 新建或幂等重放的活动档案
     */
    @PostMapping
    public CommonResult<ProfileCommandResponse> create(@RequestBody ProfileCreateRequest request) {
        return success(profileApplicationService.create(request));
    }

    /**
     * 从当前活动档案创建新版本，旧档案退役但继续服务历史候选。
     *
     * @param settlementProfileNo 当前活动档案号
     * @param request 新调度参数、期望版本和可信操作审计
     * @return 新建或幂等重放的活动档案版本
     */
    @PostMapping("/{settlementProfileNo}/versions")
    public CommonResult<ProfileCommandResponse> createVersion(
            @PathVariable("settlementProfileNo") String settlementProfileNo,
            @RequestBody ProfileVersionCreateRequest request) {
        return success(profileApplicationService.createVersion(settlementProfileNo, request));
    }

    /**
     * 查询商户结算档案和目标账户是否满足最终开通条件。
     *
     * @param merchantId 平台商户号
     * @return 稳定问题编码组成的 readiness
     */
    @GetMapping("/merchants/{merchantId}/readiness")
    public CommonResult<ProfileReadinessResponse> readiness(
            @PathVariable("merchantId") String merchantId) {
        return success(profileApplicationService.readiness(merchantId));
    }
}
