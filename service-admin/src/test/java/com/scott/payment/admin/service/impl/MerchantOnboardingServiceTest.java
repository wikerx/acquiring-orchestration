package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.MerchantOnboardingDTOs;
import com.scott.payment.admin.entity.channel.ChannelEntities.MerchantChannelMidBindingDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.BizDocumentDO;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.MerchantRelatedPersonDO;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.MerchantReviewRecordDO;
import com.scott.payment.admin.mapper.BizDocumentMapper;
import com.scott.payment.admin.mapper.FeePlanMapper;
import com.scott.payment.admin.mapper.MerchantChannelMidBindingMapper;
import com.scott.payment.admin.mapper.MerchantRelatedPersonMapper;
import com.scott.payment.admin.mapper.MerchantReviewRecordMapper;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOnboardingServiceTest
 * @date : 2026-09-14 19:15
 * @email : scott_x@163.com
 * @description : 商户开户状态机单元测试，覆盖草稿初始化、审核决策和激活门禁
 * @status : create
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class MerchantOnboardingServiceTest {

    /** 商户相关人员数据访问替身。 */
    @Mock
    private MerchantRelatedPersonMapper relatedPersonMapper;

    /** 合规资料元数据访问替身。 */
    @Mock
    private BizDocumentMapper documentMapper;

    /** 审核不可变记录数据访问替身。 */
    @Mock
    private MerchantReviewRecordMapper reviewRecordMapper;

    /** 商户费率方案数据访问替身。 */
    @Mock
    private FeePlanMapper feePlanMapper;

    /** 商户渠道绑定数据访问替身。 */
    @Mock
    private MerchantChannelMidBindingMapper channelMidBindingMapper;

    /** 相关人员敏感字段加密替身。 */
    @Mock
    private MerchantProfileSensitiveCrypto sensitiveCrypto;

    /** 商户主账号开户替身。 */
    @Mock
    private AdminMerchantPrimaryAccountProvisioningService primaryAccountProvisioningService;

    /** 商户资金账户开户替身。 */
    @Mock
    private AdminMerchantFundAccountProvisioningService fundAccountProvisioningService;

    /** 被测商户开户领域服务。 */
    private MerchantOnboardingService service;

    /** 初始化 MyBatis-Plus 元数据和被测服务。 */
    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, MerchantRelatedPersonDO.class);
        TableInfoHelper.initTableInfo(assistant, BizDocumentDO.class);
        TableInfoHelper.initTableInfo(assistant, MerchantReviewRecordDO.class);
        TableInfoHelper.initTableInfo(assistant, FeePlanDO.class);
        TableInfoHelper.initTableInfo(assistant, MerchantChannelMidBindingDO.class);
        service = new MerchantOnboardingService(
                relatedPersonMapper,
                documentMapper,
                reviewRecordMapper,
                feePlanMapper,
                channelMidBindingMapper,
                sensitiveCrypto,
                primaryAccountProvisioningService,
                fundAccountProvisioningService);
    }

    /** 新商户必须以冻结草稿开始，不能在创建时直接成为运行态。 */
    @Test
    void shouldInitializeMerchantAsFrozenDraft() {
        log.info("测试商户草稿初始化，关键输入: 后台新增商户");
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();

        service.initializeDraft(merchant);

        assertThat(merchant.getApplicationNo()).startsWith("APP");
        assertThat(merchant.getOnboardingSource()).isEqualTo("ADMIN");
        assertThat(merchant.getOnboardingStatus()).isEqualTo(MerchantOnboardingService.ONBOARDING_DRAFT);
        assertThat(merchant.getReviewStatus()).isEqualTo(MerchantOnboardingService.REVIEW_NOT_SUBMITTED);
        assertThat(merchant.getActivationStatus()).isEqualTo(MerchantOnboardingService.ACTIVATION_NOT_READY);
        assertThat(merchant.getMerchantStatus()).isEqualTo(2);
        log.info("商户草稿初始化完成，结果: 冻结、未提交审核、未满足激活条件");
    }

    /** 审核通过只改变审核状态，内部配置不完整时不能提前标记为可激活。 */
    @Test
    void shouldKeepActivationNotReadyWhenReviewPassesWithoutConfiguration() {
        log.info("测试商户审核通过状态，关键输入: decision=PASS, 未配置费率和渠道");
        BaseMerchantInfoDO merchant = pendingMerchant();
        MerchantOnboardingDTOs.ReviewRequest request = new MerchantOnboardingDTOs.ReviewRequest();
        request.setDecision("PASS");

        service.review(merchant, request);

        assertThat(merchant.getOnboardingStatus()).isEqualTo(MerchantOnboardingService.ONBOARDING_APPROVED);
        assertThat(merchant.getReviewStatus()).isEqualTo(MerchantOnboardingService.REVIEW_PASSED);
        assertThat(merchant.getActivationStatus()).isEqualTo(MerchantOnboardingService.ACTIVATION_NOT_READY);
        verify(reviewRecordMapper).insert(any(MerchantReviewRecordDO.class));
        verify(primaryAccountProvisioningService, never()).provision(any());
        verify(fundAccountProvisioningService, never()).provision(any());
        log.info("商户审核通过状态完成，结果: 审核通过但激活仍被内部配置门禁阻止");
    }

    /** 未通过审核时激活必须失败，且不得产生账号或资金账户副作用。 */
    @Test
    void shouldRejectActivationBeforeReviewPasses() {
        log.info("测试商户激活审核门禁，关键输入: reviewStatus=PENDING");
        BaseMerchantInfoDO merchant = pendingMerchant();

        assertThatThrownBy(() -> service.activate(merchant))
                .hasMessageContaining("审核尚未通过");

        verify(primaryAccountProvisioningService, never()).provision(any());
        verify(fundAccountProvisioningService, never()).provision(any());
        log.info("商户激活审核门禁完成，结果: 未通过审核不创建账号和资金账户");
    }

    /** 审核、MCC、结算币种、风险、费率和渠道均就绪后才能完成激活。 */
    @Test
    void shouldActivateMerchantAfterAllReadinessChecksPass() {
        log.info("测试商户完整激活，关键输入: 审核、费率和渠道均已就绪");
        BaseMerchantInfoDO merchant = pendingMerchant();
        merchant.setReviewStatus(MerchantOnboardingService.REVIEW_PASSED);
        merchant.setMerchantCategoryCode("5411");
        merchant.setSettlementCurrency("USD");
        merchant.setRiskLevel(2);
        when(feePlanMapper.selectOne(any())).thenReturn(new FeePlanDO());
        when(channelMidBindingMapper.selectCount(any())).thenReturn(1L);

        service.activate(merchant);

        InOrder order = inOrder(primaryAccountProvisioningService, fundAccountProvisioningService, reviewRecordMapper);
        order.verify(primaryAccountProvisioningService).provision(merchant);
        order.verify(fundAccountProvisioningService).provision(merchant);
        order.verify(reviewRecordMapper).insert(any(MerchantReviewRecordDO.class));
        assertThat(merchant.getMerchantStatus()).isEqualTo(1);
        assertThat(merchant.getOnboardingStatus()).isEqualTo(MerchantOnboardingService.ONBOARDING_ACTIVE);
        assertThat(merchant.getActivationStatus()).isEqualTo(MerchantOnboardingService.ACTIVATION_ACTIVE);
        log.info("商户完整激活完成，结果: 账号和资金账户按顺序幂等开户并进入运行态");
    }

    /** 已激活商户详情不应再次显示激活前配置问题。 */
    @Test
    void shouldNotRecalculateReadinessForActiveMerchant() {
        log.info("测试已激活商户详情就绪状态，关键输入: activationStatus=ACTIVE");
        BaseMerchantInfoDO merchant = pendingMerchant();
        merchant.setReviewStatus(MerchantOnboardingService.REVIEW_PASSED);
        merchant.setOnboardingStatus(MerchantOnboardingService.ONBOARDING_ACTIVE);
        merchant.setActivationStatus(MerchantOnboardingService.ACTIVATION_ACTIVE);
        AdminMerchantInfoDTO dto = new AdminMerchantInfoDTO();

        service.enrich(merchant, dto);

        assertThat(dto.getActivationReady()).isTrue();
        assertThat(dto.getReadinessIssues()).isEmpty();
        verify(feePlanMapper, never()).selectOne(any());
        verify(channelMidBindingMapper, never()).selectCount(any());
        log.info("已激活商户详情就绪状态完成，结果: 不再重复执行激活门禁或展示阻塞项");
    }

    /** 联系人资料未补齐时不得提交审核，草稿状态和审核记录均保持不变。 */
    @Test
    void shouldRejectReviewSubmissionWithoutContactDetails() {
        log.info("测试商户提交审核联系人门禁，关键输入: 联系人姓名和邮箱为空");
        BaseMerchantInfoDO merchant = pendingMerchant();
        merchant.setReviewStatus(MerchantOnboardingService.REVIEW_NOT_SUBMITTED);
        merchant.setOnboardingStatus(MerchantOnboardingService.ONBOARDING_DRAFT);

        assertThatThrownBy(() -> service.submitForReview(merchant))
                .hasMessageContaining("联系人姓名")
                .hasMessageContaining("联系邮箱");

        assertThat(merchant.getOnboardingStatus()).isEqualTo(MerchantOnboardingService.ONBOARDING_DRAFT);
        assertThat(merchant.getReviewStatus()).isEqualTo(MerchantOnboardingService.REVIEW_NOT_SUBMITTED);
        verify(reviewRecordMapper, never()).insert(any(MerchantReviewRecordDO.class));
        log.info("商户提交审核联系人门禁完成，结果: 不完整草稿未进入待审核状态");
    }

    /** 详情接口必须返回稳定问题编码，避免把中文展示文案固化到国际化接口契约。 */
    @Test
    void shouldExposeStableReadinessIssueCodes() {
        log.info("测试商户详情就绪问题编码，关键输入: 未补齐开户资料的冻结草稿");
        BaseMerchantInfoDO merchant = pendingMerchant();
        merchant.setReviewStatus(MerchantOnboardingService.REVIEW_NOT_SUBMITTED);
        merchant.setOnboardingStatus(MerchantOnboardingService.ONBOARDING_DRAFT);
        AdminMerchantInfoDTO dto = new AdminMerchantInfoDTO();

        service.enrich(merchant, dto);

        assertThat(dto.getReadinessIssues())
                .contains("OPERATING_COUNTRY", "CONTACT_NAME", "BUSINESS_REGISTRATION_DOCUMENT")
                .doesNotContain("实际经营国家", "联系人姓名", "公司注册证书/营业执照");
        log.info("商户详情就绪问题编码完成，结果: DTO 仅返回稳定编码供前端国际化展示");
    }

    /** 构造待审核商户，避免测试包含任何真实商户或联系信息。 */
    private BaseMerchantInfoDO pendingMerchant() {
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setMerchantId("MTEST0001");
        merchant.setMerchantName("Test Merchant");
        merchant.setReviewStatus(MerchantOnboardingService.REVIEW_PENDING);
        merchant.setOnboardingStatus(MerchantOnboardingService.ONBOARDING_PENDING_REVIEW);
        merchant.setActivationStatus(MerchantOnboardingService.ACTIVATION_NOT_READY);
        merchant.setMerchantStatus(2);
        return merchant;
    }
}
