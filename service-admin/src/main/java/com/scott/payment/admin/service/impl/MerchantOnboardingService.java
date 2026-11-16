package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantActivationReadinessDTOs.PaymentCapability;
import com.scott.payment.admin.dto.merchant.AdminMerchantActivationReadinessDTOs.PaymentCapabilityReadiness;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.dto.merchant.MerchantOnboardingDTOs;
import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileReadinessResponse;
import com.scott.payment.admin.entity.channel.ChannelEntities.MerchantChannelMidBindingDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanVersionDO;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.BizDocumentDO;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.MerchantRelatedPersonDO;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.MerchantReviewRecordDO;
import com.scott.payment.admin.mapper.BizDocumentMapper;
import com.scott.payment.admin.mapper.FeePlanMapper;
import com.scott.payment.admin.mapper.FeePlanVersionMapper;
import com.scott.payment.admin.mapper.MerchantChannelMidBindingMapper;
import com.scott.payment.admin.mapper.MerchantRelatedPersonMapper;
import com.scott.payment.admin.mapper.MerchantReviewRecordMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商户开户领域服务，承载资料完整性、审核状态流转、激活门禁和相关人员敏感信息处理
 */
@Service
public class MerchantOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(MerchantOnboardingService.class);

    /**
     * 开户草稿状态；允许继续编辑和提交审核。
     */
    public static final String ONBOARDING_DRAFT = "DRAFT";
    /**
     * 开户待审核状态；禁止再次提交。
     */
    public static final String ONBOARDING_PENDING_REVIEW = "PENDING_REVIEW";
    /**
     * 开户待补件状态；允许补充资料后再次提交。
     */
    public static final String ONBOARDING_SUPPLEMENT_REQUIRED = "SUPPLEMENT_REQUIRED";
    /**
     * 开户审核通过状态；等待费率、渠道和风险配置。
     */
    public static final String ONBOARDING_APPROVED = "APPROVED";
    /**
     * 开户驳回状态；当前流程不允许继续激活。
     */
    public static final String ONBOARDING_REJECTED = "REJECTED";
    /**
     * 开户已激活状态；商户账号和资金账户已经初始化。
     */
    public static final String ONBOARDING_ACTIVE = "ACTIVE";
    /**
     * 尚未提交审核。
     */
    public static final String REVIEW_NOT_SUBMITTED = "NOT_SUBMITTED";
    /**
     * 审核待处理。
     */
    public static final String REVIEW_PENDING = "PENDING";
    /**
     * 审核要求补件。
     */
    public static final String REVIEW_SUPPLEMENT = "SUPPLEMENT";
    /**
     * 审核通过。
     */
    public static final String REVIEW_PASSED = "PASSED";
    /**
     * 审核驳回。
     */
    public static final String REVIEW_REJECTED = "REJECTED";
    /**
     * 激活条件尚未满足。
     */
    public static final String ACTIVATION_NOT_READY = "NOT_READY";
    /**
     * 激活条件已经满足。
     */
    public static final String ACTIVATION_READY = "READY";
    /**
     * 商户已完成激活。
     */
    public static final String ACTIVATION_ACTIVE = "ACTIVE";

    /**
     * 未删除标识；固定为 0。
     */
    private static final int NOT_DELETED = 0;
    /**
     * 商户 KYB 资料业务类型；与 biz_document 归属查询保持一致。
     */
    private static final String DOCUMENT_BIZ_TYPE = "MERCHANT_KYB";
    /**
     * 允许的人工审核决定集合。
     */
    private static final Set<String> REVIEW_DECISIONS = Set.of("PASS", "SUPPLEMENT", "REJECT");
    /**
     * 可满足个人身份证明要求的资料类型集合。
     */
    private static final Set<String> ID_DOCUMENT_TYPES = Set.of(
            "DIRECTOR_ID", "UBO_ID", "LEGAL_REPRESENTATIVE_ID", "AUTHORIZED_SIGNER_ID");
    /**
     * 就绪问题稳定编码与中文业务标签映射；接口返回编码，操作异常保留可读中文。
     */
    private static final Map<String, String> READINESS_ISSUE_LABELS = Map.ofEntries(
            Map.entry("OPERATING_COUNTRY", "实际经营国家"),
            Map.entry("MERCHANT_DESCRIPTION", "商户描述"),
            Map.entry("REGISTRATION_NUMBER", "公司注册号"),
            Map.entry("LEGAL_ENTITY_TYPE", "企业注册类型"),
            Map.entry("INCORPORATION_DATE", "成立日期"),
            Map.entry("INCORPORATION_COUNTRY", "注册证书签发国家"),
            Map.entry("REGISTERED_CITY", "注册地址城市"),
            Map.entry("REGISTERED_ADDRESS", "注册地址"),
            Map.entry("CONTACT_NAME", "联系人姓名"),
            Map.entry("CONTACT_EMAIL", "联系邮箱"),
            Map.entry("CONTACT_PHONE", "联系电话"),
            Map.entry("BUSINESS_MODEL", "业务模式"),
            Map.entry("SALES_CHANNELS", "销售渠道"),
            Map.entry("PRODUCTS_SERVICES", "主营产品或服务"),
            Map.entry("TARGET_MARKETS", "目标市场"),
            Map.entry("CUSTOMER_TYPE", "客户类型"),
            Map.entry("TRANSACTION_CURRENCIES", "交易币种"),
            Map.entry("EXPECTED_MONTHLY_VOLUME", "预计月交易金额"),
            Map.entry("EXPECTED_VOLUME_CURRENCY", "预计月交易金额币种"),
            Map.entry("AVERAGE_TICKET", "平均单笔金额"),
            Map.entry("RECURRING_PAYMENT_FLAG", "是否循环扣款"),
            Map.entry("PRESALE_FLAG", "是否预售"),
            Map.entry("DIGITAL_GOODS_FLAG", "是否涉及数字商品"),
            Map.entry("RESTRICTED_BUSINESS_FLAG", "是否涉及受限行业"),
            Map.entry("WEBSITE_LIVE_FLAG", "网站是否上线"),
            Map.entry("WEBSITE_URL", "官方网站"),
            Map.entry("PRIVACY_POLICY_URL", "隐私政策 URL"),
            Map.entry("REFUND_POLICY_URL", "退款政策 URL"),
            Map.entry("TERMS_URL", "服务条款 URL"),
            Map.entry("OTHER_SALES_URL_WHEN_WEBSITE_OFFLINE", "未上线网站时的其他销售页面"),
            Map.entry("RELATED_PERSONS", "法定代表人/董事/UBO 信息"),
            Map.entry("LEGAL_REPRESENTATIVE", "法定代表人"),
            Map.entry("UBO", "最终受益人 UBO"),
            Map.entry("RELATED_PERSON_ID_NUMBER", "相关人员证件号"),
            Map.entry("BUSINESS_REGISTRATION_DOCUMENT", "公司注册证书/营业执照"),
            Map.entry("RELATED_PERSON_ID_DOCUMENT", "董事、法人或 UBO 身份证明"),
            Map.entry("MERCHANT_REVIEW_NOT_PASSED", "商户审核尚未通过"),
            Map.entry("MCC_NOT_CONFIRMED", "MCC 尚未确认"),
            Map.entry("SETTLEMENT_CURRENCY_NOT_CONFIRMED", "结算币种尚未确认"),
            Map.entry("RISK_LEVEL_NOT_CONFIGURED", "风险等级尚未配置"),
            Map.entry("FEE_PLAN_NOT_ACTIVE", "尚无已生效商户费率版本"),
            Map.entry("FEE_SETTLEMENT_CYCLE_INCOMPLETE", "当前费率版本的结算周期不完整"),
            Map.entry("CHANNEL_MID_NOT_ACTIVE", "尚无当前生效的渠道 MID 绑定"),
            Map.entry("CHANNEL_MID_UNAVAILABLE", "生效的渠道 MID 绑定存在不可用配置"),
            Map.entry("CHANNEL_PAYMENT_CAPABILITY_EMPTY", "渠道 MID 未形成可用支付能力"),
            Map.entry("CHANNEL_ROUTE_CONFLICT", "同一支付能力存在最高优先级路由冲突"),
            Map.entry("FEE_CAPABILITY_NOT_COVERED", "生效费率未覆盖全部支付能力"),
            Map.entry("FEE_CAPABILITY_AMBIGUOUS", "同一支付能力命中多条费率规则"),
            Map.entry("PAYMENT_CAPABILITY_READINESS_UNAVAILABLE", "支付能力检查服务暂不可用"),
            Map.entry("RISK_MERCHANT_LIMIT_NOT_ENABLED", "商户交易限额功能尚未启用"),
            Map.entry("RISK_MERCHANT_LIMIT_INCOMPLETE", "商户交易限额未覆盖全部能力币种"),
            Map.entry("CHANNEL_LIMIT_INCOMPLETE", "渠道限额未覆盖全部支付能力"),
            Map.entry("THREE_DS_POLICY_NOT_COVERED", "银行卡能力缺少完整的 3DS 兜底策略"),
            Map.entry("RISK_READINESS_UNAVAILABLE", "风控配置检查服务暂不可用"),
            Map.entry("OPENAPI_CREDENTIALS_NOT_READY", "OpenAPI 凭证或加解密材料尚未就绪"),
            Map.entry("OPENAPI_IP_WHITELIST_NOT_READY", "尚无审核通过且启用的 OpenAPI IP 白名单"),
            Map.entry("SOURCE_URL_NOT_READY", "尚无审核通过且启用的店铺网址"),
            Map.entry("TECHNICAL_ACCESS_READINESS_UNAVAILABLE", "技术接入检查服务暂不可用"),
            Map.entry("SETTLEMENT_PROFILE_NOT_ACTIVE", "尚无活动结算档案"),
            Map.entry("SETTLEMENT_ACCOUNT_NOT_NORMAL", "结算资金账户不存在或状态异常"),
            Map.entry("SETTLEMENT_ACCOUNT_CURRENCY_MISMATCH", "结算档案与资金账户币种不一致"),
            Map.entry("SETTLEMENT_CURRENCY_NOT_SUPPORTED", "结算币种或币种精度未启用"),
            Map.entry("SETTLEMENT_BUSINESS_TIME_ZONE_INVALID", "结算业务时区无效"),
            Map.entry("SETTLEMENT_DAILY_CUTOFF_NOT_CONFIGURED", "结算日切时间未配置"),
            Map.entry("SETTLEMENT_PROCESSING_MODE_INVALID", "结算处理模式无效"),
            Map.entry("SETTLEMENT_READINESS_UNAVAILABLE", "结算配置检查服务暂不可用"));

    /**
     * 商户相关人员 Mapper；负责人员列表和软删除替换。
     */
    private final MerchantRelatedPersonMapper relatedPersonMapper;
    /**
     * 合规资料元数据 Mapper；仅查询有效 KYB 资料。
     */
    private final BizDocumentMapper documentMapper;
    /**
     * 审核记录 Mapper；保存不可变审核轨迹。
     */
    private final MerchantReviewRecordMapper reviewRecordMapper;
    /**
     * 商户费率方案 Mapper；用于激活前确认当前版本已生效。
     */
    private final FeePlanMapper feePlanMapper;
    /**
     * 生效费率版本 Mapper；用于校验结算周期和结算币种快照。
     */
    private final FeePlanVersionMapper feePlanVersionMapper;
    /**
     * 商户渠道 MID 绑定 Mapper；用于激活前确认存在有效渠道能力。
     */
    private final MerchantChannelMidBindingMapper channelMidBindingMapper;
    /**
     * 商户实际支付能力、费率覆盖和路由唯一性检查服务。
     */
    private final MerchantPaymentCapabilityReadinessService paymentCapabilityReadinessService;
    /**
     * 商户限额、渠道限额和 3DS 配置就绪检查服务。
     */
    private final MerchantRiskConfigurationReadinessService riskConfigurationReadinessService;
    /**
     * OpenAPI 凭证、IP 白名单和必需店铺网址门禁服务。
     */
    private final MerchantTechnicalAccessReadinessService technicalAccessReadinessService;
    /**
     * 相关人员证件号加密与脱敏边界；不允许为空。
     */
    private final MerchantProfileSensitiveCrypto sensitiveCrypto;
    /**
     * 商户主账号幂等开户服务；只在激活时调用。
     */
    private final AdminMerchantPrimaryAccountProvisioningService primaryAccountProvisioningService;
    /**
     * 商户资金账户幂等开户服务；只在激活时调用。
     */
    private final AdminMerchantFundAccountProvisioningService fundAccountProvisioningService;
    /**
     * service-settlement 内部客户端；最终开通前读取权威结算档案 readiness。
     */
    private final SettlementInternalClient settlementInternalClient;

    /**
     * 创建商户开户领域服务。
     *
     * @param relatedPersonMapper 商户相关人员数据访问
     * @param documentMapper 合规资料元数据访问
     * @param reviewRecordMapper 审核记录数据访问
     * @param feePlanMapper 商户费率方案数据访问
     * @param feePlanVersionMapper 生效费率版本数据访问
     * @param channelMidBindingMapper 渠道 MID 绑定数据访问
     * @param sensitiveCrypto 证件号加密与脱敏组件
     * @param primaryAccountProvisioningService 商户主账号开户服务
     * @param fundAccountProvisioningService 商户资金账户开户服务
     * @param settlementInternalClient 结算档案 readiness 内部客户端
     */
    public MerchantOnboardingService(MerchantRelatedPersonMapper relatedPersonMapper,
                                     BizDocumentMapper documentMapper,
                                     MerchantReviewRecordMapper reviewRecordMapper,
                                     FeePlanMapper feePlanMapper,
                                     FeePlanVersionMapper feePlanVersionMapper,
                                     MerchantChannelMidBindingMapper channelMidBindingMapper,
                                     MerchantPaymentCapabilityReadinessService paymentCapabilityReadinessService,
                                     MerchantRiskConfigurationReadinessService riskConfigurationReadinessService,
                                     MerchantTechnicalAccessReadinessService technicalAccessReadinessService,
                                     MerchantProfileSensitiveCrypto sensitiveCrypto,
                                     AdminMerchantPrimaryAccountProvisioningService primaryAccountProvisioningService,
                                     AdminMerchantFundAccountProvisioningService fundAccountProvisioningService,
                                     SettlementInternalClient settlementInternalClient) {
        this.relatedPersonMapper = relatedPersonMapper;
        this.documentMapper = documentMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.feePlanMapper = feePlanMapper;
        this.feePlanVersionMapper = feePlanVersionMapper;
        this.channelMidBindingMapper = channelMidBindingMapper;
        this.paymentCapabilityReadinessService = paymentCapabilityReadinessService;
        this.riskConfigurationReadinessService = riskConfigurationReadinessService;
        this.technicalAccessReadinessService = technicalAccessReadinessService;
        this.sensitiveCrypto = sensitiveCrypto;
        this.primaryAccountProvisioningService = primaryAccountProvisioningService;
        this.fundAccountProvisioningService = fundAccountProvisioningService;
        this.settlementInternalClient = settlementInternalClient;
    }

    /**
     * 初始化后台新增商户，始终从草稿开始且运行状态保持冻结。
     *
     * @param merchant 待初始化的商户主档，不允许为空
     */
    public void initializeDraft(BaseMerchantInfoDO merchant) {
        merchant.setApplicationNo(PaymentOrderNoGenerator.nextOrderNo("APP"));
        merchant.setOnboardingSource("ADMIN");
        merchant.setOnboardingStatus(ONBOARDING_DRAFT);
        merchant.setReviewStatus(REVIEW_NOT_SUBMITTED);
        merchant.setActivationStatus(ACTIVATION_NOT_READY);
        merchant.setMerchantStatus(2);
    }

    /**
     * 在商户主档已落库且联系人邮箱可用时创建进件门户账号。
     *
     * <p>草稿允许先不填写联系人，因此该动作允许无邮箱时跳过；管理端后续补齐邮箱后会再次调用。
     * 开户服务自身保证幂等，重复保存资料不会重发激活邮件或重复创建账号。</p>
     *
     * @param merchant 已持久化的商户主档
     */
    public void provisionOnboardingPortalAccessIfPossible(BaseMerchantInfoDO merchant) {
        if (merchant == null || !StringUtils.hasText(merchant.getContactEmail())) {
            return;
        }
        primaryAccountProvisioningService.provisionOnboardingAccess(merchant);
    }

    /**
     * 替换商户相关人员；未重新输入证件号且人员 ID 匹配时保留原密文。
     *
     * @param merchantId 平台商户号，不允许为空
     * @param persons 页面提交的人员列表，可为空表示清空有效人员
     * @param now 本次资料修改时间，不允许为空
     */
    public void replaceRelatedPersons(String merchantId,
                                      List<MerchantOnboardingDTOs.RelatedPerson> persons,
                                      LocalDateTime now) {
        List<MerchantRelatedPersonDO> existing = relatedPersonMapper.selectList(
                Wrappers.<MerchantRelatedPersonDO>lambdaQuery()
                        .eq(MerchantRelatedPersonDO::getMerchantId, merchantId)
                        .eq(MerchantRelatedPersonDO::getDeleted, NOT_DELETED));
        Map<Long, MerchantRelatedPersonDO> existingById = existing.stream()
                .filter(row -> row.getId() != null)
                .collect(Collectors.toMap(MerchantRelatedPersonDO::getId, row -> row));
        if (!existing.isEmpty()) {
            relatedPersonMapper.update(null, Wrappers.<MerchantRelatedPersonDO>lambdaUpdate()
                    .set(MerchantRelatedPersonDO::getDeleted, 1)
                    .set(MerchantRelatedPersonDO::getGmtModified, now)
                    .eq(MerchantRelatedPersonDO::getMerchantId, merchantId)
                    .eq(MerchantRelatedPersonDO::getDeleted, NOT_DELETED));
        }
        List<MerchantOnboardingDTOs.RelatedPerson> normalized = persons == null
                ? Collections.emptyList() : persons;
        for (int index = 0; index < normalized.size(); index++) {
            MerchantOnboardingDTOs.RelatedPerson source = normalized.get(index);
            if (source == null || !StringUtils.hasText(source.getFullName())) {
                continue;
            }
            MerchantRelatedPersonDO previous = source.getId() == null ? null : existingById.get(source.getId());
            MerchantRelatedPersonDO row = toEntity(merchantId, source, previous, index, now);
            relatedPersonMapper.insert(row);
        }
    }

    /**
     * 为单商户详情补充相关人员、资料、审核历史与当前就绪状态。
     *
     * @param merchant 商户主档，不允许为空
     * @param dto 待补充的管理端详情 DTO，不允许为空
     */
    public void enrich(BaseMerchantInfoDO merchant, AdminMerchantInfoDTO dto) {
        dto.setRelatedPersons(loadRelatedPersons(merchant.getMerchantId()));
        dto.setDocuments(loadDocuments(merchant.getMerchantId()));
        dto.setReviewRecords(loadReviewRecords(merchant.getMerchantId()));
        List<String> submitIssues = reviewSubmissionIssues(merchant, dto.getRelatedPersons(), dto.getDocuments());
        dto.setReviewSubmittable(canSubmit(merchant) && submitIssues.isEmpty());
        if (ACTIVATION_ACTIVE.equals(merchant.getActivationStatus())) {
            dto.setActivationReady(true);
            dto.setReadinessIssues(Collections.emptyList());
            return;
        }
        if (!REVIEW_PASSED.equals(merchant.getReviewStatus())) {
            dto.setActivationReady(false);
            dto.setReadinessIssues(submitIssues);
            return;
        }
        ActivationAssessment activationAssessment = activationAssessment(merchant);
        dto.setActivationReady(activationAssessment.issues().isEmpty());
        dto.setReadinessIssues(activationAssessment.issues());
        dto.setPaymentCapabilityReadiness(toPaymentCapabilityReadiness(
                activationAssessment.paymentReadiness()));
    }

    /**
     * 校验资料完整性并提交人工审核，同时写入不可变审核记录。
     *
     * @param merchant 已持久化商户主档，不允许为空
     * @throws ServiceException 当前状态或资料完整性不满足提交要求时抛出
     */
    public void submitForReview(BaseMerchantInfoDO merchant) {
        if (!canSubmit(merchant)) {
            throw invalid("当前开户状态不允许提交审核");
        }
        List<String> issues = reviewSubmissionIssues(
                merchant, loadRelatedPersons(merchant.getMerchantId()), loadDocuments(merchant.getMerchantId()));
        requireNoIssues(issues, "资料未满足提交审核要求：");
        String fromStatus = defaultReviewStatus(merchant.getReviewStatus());
        merchant.setOnboardingStatus(ONBOARDING_PENDING_REVIEW);
        merchant.setReviewStatus(REVIEW_PENDING);
        merchant.setActivationStatus(ACTIVATION_NOT_READY);
        appendReviewRecord(merchant.getMerchantId(), "SUBMIT", fromStatus, REVIEW_PENDING, null);
    }

    /**
     * 执行通过、补件或驳回审核决定，并同步开户与激活状态。
     *
     * @param merchant 当前待审核商户，不允许为空
     * @param request 审核决定与意见；补件和驳回时意见必填
     * @throws ServiceException 商户不在待审核状态或审核决定非法时抛出
     */
    public void review(BaseMerchantInfoDO merchant, MerchantOnboardingDTOs.ReviewRequest request) {
        if (!REVIEW_PENDING.equals(merchant.getReviewStatus())) {
            throw invalid("只有待审核商户可以执行审核操作");
        }
        String decision = request == null ? null : upper(request.getDecision());
        if (!REVIEW_DECISIONS.contains(decision)) {
            throw invalid("审核决定仅支持 PASS、SUPPLEMENT 或 REJECT");
        }
        String comment = trimToNull(request.getComment());
        if (!"PASS".equals(decision) && comment == null) {
            throw invalid("要求补件或驳回时必须填写审核意见");
        }
        String targetReviewStatus;
        if ("PASS".equals(decision)) {
            merchant.setOnboardingStatus(ONBOARDING_APPROVED);
            targetReviewStatus = REVIEW_PASSED;
            // 审核通过且已确认结算币种后准备零余额账户，后续档案必须绑定真实账户。
            if (StringUtils.hasText(merchant.getSettlementCurrency())) {
                fundAccountProvisioningService.provision(merchant);
            }
            merchant.setActivationStatus(activationAssessmentIgnoringReview(merchant).issues().isEmpty()
                    ? ACTIVATION_READY : ACTIVATION_NOT_READY);
        } else if ("SUPPLEMENT".equals(decision)) {
            merchant.setOnboardingStatus(ONBOARDING_SUPPLEMENT_REQUIRED);
            targetReviewStatus = REVIEW_SUPPLEMENT;
            merchant.setActivationStatus(ACTIVATION_NOT_READY);
        } else {
            merchant.setOnboardingStatus(ONBOARDING_REJECTED);
            targetReviewStatus = REVIEW_REJECTED;
            merchant.setActivationStatus(ACTIVATION_NOT_READY);
        }
        merchant.setReviewStatus(targetReviewStatus);
        appendReviewRecord(merchant.getMerchantId(), decision, REVIEW_PENDING, targetReviewStatus, comment);
    }

    /**
     * 审核、费率、渠道和关键内部字段就绪后，幂等创建账号及资金账户并激活商户。
     *
     * @param merchant 已持久化且审核通过的商户主档，不允许为空
     * @throws ServiceException 任一激活条件未满足时抛出，且不会创建账号或资金账户
     */
    public void activate(BaseMerchantInfoDO merchant) {
        List<String> issues = activationIssues(merchant);
        requireNoIssues(issues, "商户尚未满足激活要求：");
        primaryAccountProvisioningService.activateFullAccess(merchant);
        fundAccountProvisioningService.provision(merchant);
        merchant.setMerchantStatus(1);
        merchant.setOnboardingStatus(ONBOARDING_ACTIVE);
        merchant.setActivationStatus(ACTIVATION_ACTIVE);
        appendReviewRecord(merchant.getMerchantId(), "ACTIVATE", REVIEW_PASSED, REVIEW_PASSED, null);
    }

    /**
     * 判断当前审核状态是否允许提交或补件后重新提交。
     *
     * @param merchant 商户主档，不允许为空
     * @return true 表示允许执行提交审核状态迁移
     */
    private boolean canSubmit(BaseMerchantInfoDO merchant) {
        return REVIEW_NOT_SUBMITTED.equals(defaultReviewStatus(merchant.getReviewStatus()))
                || REVIEW_SUPPLEMENT.equals(merchant.getReviewStatus());
    }

    /**
     * 汇总提交审核前的资料完整性问题，不改变商户或关联记录状态。
     *
     * @param merchant 商户主档，不允许为空
     * @param persons 当前有效相关人员列表，不允许为空
     * @param documents 当前有效资料元数据列表，不允许为空
     * @return 阻塞提交审核的业务问题列表
     */
    private List<String> reviewSubmissionIssues(BaseMerchantInfoDO merchant,
                                                List<MerchantOnboardingDTOs.RelatedPerson> persons,
                                                List<MerchantOnboardingDTOs.Document> documents) {
        List<String> issues = new ArrayList<>();
        required(issues, merchant.getOperatingCountry(), "OPERATING_COUNTRY");
        required(issues, merchant.getMerchantDescription(), "MERCHANT_DESCRIPTION");
        required(issues, merchant.getRegistrationNumber(), "REGISTRATION_NUMBER");
        required(issues, merchant.getLegalEntityType(), "LEGAL_ENTITY_TYPE");
        if (merchant.getIncorporationDate() == null) issues.add("INCORPORATION_DATE");
        required(issues, merchant.getIncorporationCountry(), "INCORPORATION_COUNTRY");
        required(issues, merchant.getRegisteredCity(), "REGISTERED_CITY");
        required(issues, merchant.getRegisteredAddress(), "REGISTERED_ADDRESS");
        required(issues, merchant.getContactName(), "CONTACT_NAME");
        required(issues, merchant.getContactEmail(), "CONTACT_EMAIL");
        required(issues, merchant.getContactPhone(), "CONTACT_PHONE");
        required(issues, merchant.getBusinessModel(), "BUSINESS_MODEL");
        required(issues, merchant.getSalesChannels(), "SALES_CHANNELS");
        required(issues, merchant.getProductsServices(), "PRODUCTS_SERVICES");
        required(issues, merchant.getTargetMarkets(), "TARGET_MARKETS");
        required(issues, merchant.getCustomerType(), "CUSTOMER_TYPE");
        required(issues, merchant.getTransactionCurrencies(), "TRANSACTION_CURRENCIES");
        if (merchant.getExpectedMonthlyVolume() == null) issues.add("EXPECTED_MONTHLY_VOLUME");
        required(issues, merchant.getExpectedVolumeCurrency(), "EXPECTED_VOLUME_CURRENCY");
        if (merchant.getAverageTicket() == null) issues.add("AVERAGE_TICKET");
        if (merchant.getRecurringPaymentFlag() == null) issues.add("RECURRING_PAYMENT_FLAG");
        if (merchant.getPresaleFlag() == null) issues.add("PRESALE_FLAG");
        if (merchant.getDigitalGoodsFlag() == null) issues.add("DIGITAL_GOODS_FLAG");
        if (merchant.getRestrictedBusinessFlag() == null) issues.add("RESTRICTED_BUSINESS_FLAG");
        if (merchant.getWebsiteLiveFlag() == null) issues.add("WEBSITE_LIVE_FLAG");
        if (Integer.valueOf(1).equals(merchant.getWebsiteLiveFlag())) {
            required(issues, merchant.getWebsiteUrl(), "WEBSITE_URL");
            required(issues, merchant.getPrivacyPolicyUrl(), "PRIVACY_POLICY_URL");
            required(issues, merchant.getRefundPolicyUrl(), "REFUND_POLICY_URL");
            required(issues, merchant.getTermsUrl(), "TERMS_URL");
        } else if (!StringUtils.hasText(merchant.getOtherSalesUrl())) {
            issues.add("OTHER_SALES_URL_WHEN_WEBSITE_OFFLINE");
        }
        validatePersons(issues, merchant, persons);
        validateDocuments(issues, documents);
        return issues;
    }

    /**
     * 按主体类型校验法人、UBO 和证件号完整性，问题追加到统一列表而不提前中断。
     *
     * @param issues 待追加的问题列表，不允许为空
     * @param merchant 商户主档，不允许为空
     * @param persons 当前有效相关人员列表，不允许为空
     */
    private void validatePersons(List<String> issues,
                                 BaseMerchantInfoDO merchant,
                                 List<MerchantOnboardingDTOs.RelatedPerson> persons) {
        if (persons.isEmpty()) {
            issues.add("RELATED_PERSONS");
            return;
        }
        boolean hasLegalRepresentative = persons.stream().anyMatch(person -> hasRole(person, "LEGAL_REPRESENTATIVE"));
        if (!hasLegalRepresentative) {
            issues.add("LEGAL_REPRESENTATIVE");
        }
        if (!"SOLE_TRADER".equalsIgnoreCase(merchant.getMerchantType())) {
            boolean hasUbo = persons.stream().anyMatch(person -> hasRole(person, "UBO"));
            if (!hasUbo) {
                issues.add("UBO");
            }
        }
        if (persons.stream().anyMatch(person -> !StringUtils.hasText(person.getIdNumberMasked()))) {
            issues.add("RELATED_PERSON_ID_NUMBER");
        }
    }

    /**
     * 校验注册证明和个人身份证明是否至少各存在一份有效资料元数据。
     *
     * @param issues 待追加的问题列表，不允许为空
     * @param documents 当前有效资料元数据列表，不允许为空
     */
    private void validateDocuments(List<String> issues, List<MerchantOnboardingDTOs.Document> documents) {
        Set<String> types = documents.stream()
                .map(MerchantOnboardingDTOs.Document::getDocumentType)
                .filter(StringUtils::hasText)
                .map(this::upper)
                .collect(Collectors.toSet());
        if (!types.contains("BUSINESS_LICENSE") && !types.contains("INCORPORATION")) {
            issues.add("BUSINESS_REGISTRATION_DOCUMENT");
        }
        if (types.stream().noneMatch(ID_DOCUMENT_TYPES::contains)) {
            issues.add("RELATED_PERSON_ID_DOCUMENT");
        }
    }

    /**
     * 汇总激活商户的全部门禁问题，包括审核状态和内部业务配置。
     *
     * @param merchant 商户主档，不允许为空
     * @return 阻塞激活的问题列表
     */
    private List<String> activationIssues(BaseMerchantInfoDO merchant) {
        return activationAssessment(merchant).issues();
    }

    /**
     * 计算包含审核状态的完整激活评估，供详情展示和最终激活动作复用同一份事实。
     */
    private ActivationAssessment activationAssessment(BaseMerchantInfoDO merchant) {
        List<String> issues = new ArrayList<>();
        if (!REVIEW_PASSED.equals(merchant.getReviewStatus())) {
            issues.add("MERCHANT_REVIEW_NOT_PASSED");
        }
        ActivationAssessment operational = activationAssessmentIgnoringReview(merchant);
        issues.addAll(operational.issues());
        return new ActivationAssessment(List.copyOf(new LinkedHashSet<>(issues)), operational.paymentReadiness());
    }

    /**
     * 计算审核之外的激活条件，并保留支付能力明细供管理端工作台定位问题。
     */
    private ActivationAssessment activationAssessmentIgnoringReview(BaseMerchantInfoDO merchant) {
        List<String> issues = new ArrayList<>();
        MerchantPaymentCapabilityReadinessService.ReadinessResult paymentReadiness = null;
        required(issues, merchant.getMerchantCategoryCode(), "MCC_NOT_CONFIRMED");
        required(issues, merchant.getSettlementCurrency(), "SETTLEMENT_CURRENCY_NOT_CONFIRMED");
        if (merchant.getRiskLevel() == null) issues.add("RISK_LEVEL_NOT_CONFIGURED");

        FeePlanDO feePlan = feePlanMapper.selectOne(Wrappers.<FeePlanDO>lambdaQuery()
                .eq(FeePlanDO::getPlanType, "MERCHANT")
                .eq(FeePlanDO::getMerchantId, merchant.getMerchantId())
                .eq(FeePlanDO::getStatus, "ENABLED")
                .eq(FeePlanDO::getDeleted, 0L)
                .isNotNull(FeePlanDO::getCurrentVersionId)
                .last("LIMIT 1"));
        FeePlanVersionDO version = null;
        if (feePlan == null) {
            issues.add("FEE_PLAN_NOT_ACTIVE");
        } else {
            version = feePlanVersionMapper.selectById(feePlan.getCurrentVersionId());
            if (!validSettlementCycle(version, merchant.getSettlementCurrency())) {
                issues.add("FEE_SETTLEMENT_CYCLE_INCOMPLETE");
            }
        }

        appendSettlementReadinessIssues(issues, merchant.getMerchantId());

        LocalDateTime now = LocalDateTime.now();
        appendTechnicalAccessReadinessIssues(issues, merchant.getMerchantId(), now);
        Long channelCount = channelMidBindingMapper.selectCount(
                Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                        .eq(MerchantChannelMidBindingDO::getMerchantId, merchant.getMerchantId())
                        .eq(MerchantChannelMidBindingDO::getBindingStatus, 1)
                        .eq(MerchantChannelMidBindingDO::getDeleted, 0L)
                        .and(wrapper -> wrapper.isNull(MerchantChannelMidBindingDO::getEffectiveTime)
                                .or().le(MerchantChannelMidBindingDO::getEffectiveTime, now))
                        .and(wrapper -> wrapper.isNull(MerchantChannelMidBindingDO::getExpireTime)
                                .or().gt(MerchantChannelMidBindingDO::getExpireTime, now)));
        if (channelCount == null || channelCount == 0) {
            issues.add("CHANNEL_MID_NOT_ACTIVE");
        } else {
            Long feePlanVersionId = version == null || !"ACTIVE".equals(version.getVersionStatus())
                    ? null : feePlan.getCurrentVersionId();
            paymentReadiness = appendPaymentCapabilityReadinessIssues(
                    issues, merchant.getMerchantId(), feePlanVersionId, now);
            appendRiskConfigurationReadinessIssues(
                    issues, merchant.getMerchantId(), paymentReadiness, now);
        }
        return new ActivationAssessment(
                List.copyOf(new LinkedHashSet<>(issues)), paymentReadiness);
    }

    /**
     * 读取支付能力、路由和费率覆盖检查结果。
     *
     * <p>数据库或配置读取异常不能使管理端商户详情不可用，但必须失败关闭：详情返回稳定
     * 阻塞编码，最终激活动作也会因为该编码存在而被拒绝。</p>
     */
    private MerchantPaymentCapabilityReadinessService.ReadinessResult appendPaymentCapabilityReadinessIssues(
            List<String> issues,
            String merchantId,
            Long feePlanVersionId,
            LocalDateTime now) {
        try {
            MerchantPaymentCapabilityReadinessService.ReadinessResult readiness =
                    paymentCapabilityReadinessService.evaluate(merchantId, feePlanVersionId, now);
            issues.addAll(readiness.issueCodes());
            return readiness;
        } catch (RuntimeException exception) {
            issues.add("PAYMENT_CAPABILITY_READINESS_UNAVAILABLE");
            log.warn("支付能力就绪检查暂不可用, event: merchant_payment_capability_readiness_unavailable, merchantId: {}",
                    merchantId, exception);
            return null;
        }
    }

    /**
     * 校验商户和渠道限额及 3DS 策略；查询异常时返回稳定阻塞码并失败关闭。
     */
    private void appendRiskConfigurationReadinessIssues(
            List<String> issues,
            String merchantId,
            MerchantPaymentCapabilityReadinessService.ReadinessResult paymentReadiness,
            LocalDateTime now) {
        if (paymentReadiness == null || paymentReadiness.capabilities().isEmpty()) {
            return;
        }
        try {
            issues.addAll(riskConfigurationReadinessService.evaluate(merchantId, paymentReadiness, now));
        } catch (RuntimeException exception) {
            issues.add("RISK_READINESS_UNAVAILABLE");
            log.warn("风控配置就绪检查暂不可用, event: merchant_risk_readiness_unavailable, merchantId: {}",
                    merchantId, exception);
        }
    }

    /**
     * 校验真实交易入口依赖的技术接入条件；读取异常时返回稳定阻塞码并失败关闭。
     */
    private void appendTechnicalAccessReadinessIssues(
            List<String> issues,
            String merchantId,
            LocalDateTime now) {
        try {
            issues.addAll(technicalAccessReadinessService.evaluate(merchantId, now));
        } catch (RuntimeException exception) {
            issues.add("TECHNICAL_ACCESS_READINESS_UNAVAILABLE");
            log.warn("技术接入就绪检查暂不可用, event: merchant_technical_access_readiness_unavailable, merchantId: {}",
                    merchantId, exception);
        }
    }

    /**
     * 将领域检查结果转换为管理端非敏感投影。
     */
    private PaymentCapabilityReadiness toPaymentCapabilityReadiness(
            MerchantPaymentCapabilityReadinessService.ReadinessResult source) {
        if (source == null) {
            return null;
        }
        PaymentCapabilityReadiness target = new PaymentCapabilityReadiness();
        target.setActiveBindingCount(source.activeBindingCount());
        target.setUnavailableBindingCount(source.unavailableBindingCount());
        target.setCapabilities(toPaymentCapabilities(source.capabilities()));
        target.setRouteConflicts(toPaymentCapabilities(source.routeConflicts()));
        target.setFeeGaps(toPaymentCapabilities(source.feeGaps()));
        target.setFeeAmbiguities(toPaymentCapabilities(source.feeAmbiguities()));
        return target;
    }

    /**
     * 转换原子能力列表，固定输出顺序并避免暴露内部路由候选。
     */
    private List<PaymentCapability> toPaymentCapabilities(
            List<MerchantPaymentCapabilityReadinessService.CapabilityKey> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        return capabilities.stream().map(source -> {
            PaymentCapability target = new PaymentCapability();
            target.setTransactionType(source.transactionType());
            target.setPaymentType(source.paymentType());
            target.setPaymentMethod(source.paymentMethod());
            target.setCurrency(source.currency());
            return target;
        }).toList();
    }

    /**
     * 单次开通评估结果；问题列表和支付能力必须来自同一评估时点。
     */
    private record ActivationAssessment(
            List<String> issues,
            MerchantPaymentCapabilityReadinessService.ReadinessResult paymentReadiness) {
    }

    /**
     * 校验生效费率版本中的结算周期和结算币种快照。
     */
    private boolean validSettlementCycle(FeePlanVersionDO version, String settlementCurrency) {
        if (version == null || !"ACTIVE".equals(version.getVersionStatus())
                || !StringUtils.hasText(settlementCurrency)
                || !settlementCurrency.equalsIgnoreCase(version.getSettlementCurrency())
                || !("T".equals(version.getInitialDelayUnit()) || "D".equals(version.getInitialDelayUnit()))
                || version.getInitialDelayDays() == null || version.getInitialDelayDays() < 1
                || version.getRegularDelayDays() == null || version.getRegularDelayDays() < 1
                || !StringUtils.hasText(version.getSettlementFrequency())) {
            return false;
        }
        return switch (version.getSettlementFrequency()) {
            case "DAILY" -> version.getFrequencyDay() == null;
            case "WEEKLY", "BIWEEKLY" -> version.getFrequencyDay() != null
                    && version.getFrequencyDay() >= 1 && version.getFrequencyDay() <= 7;
            case "MONTHLY" -> version.getFrequencyDay() != null
                    && version.getFrequencyDay() >= 1 && version.getFrequencyDay() <= 28;
            default -> false;
        };
    }

    /**
     * 读取结算领域 readiness；内部服务不可用时失败关闭，但不阻断商户详情查询。
     */
    private void appendSettlementReadinessIssues(List<String> issues, String merchantId) {
        try {
            ProfileReadinessResponse readiness = settlementInternalClient.getProfileReadiness(merchantId);
            if (readiness == null || !Boolean.TRUE.equals(readiness.getReady())) {
                List<String> readinessIssues = readiness == null ? List.of() : readiness.getIssues();
                if (readinessIssues == null || readinessIssues.isEmpty()) {
                    issues.add("SETTLEMENT_PROFILE_NOT_ACTIVE");
                } else {
                    issues.addAll(readinessIssues);
                }
            }
        } catch (ServiceException exception) {
            issues.add("SETTLEMENT_READINESS_UNAVAILABLE");
        }
    }

    /**
     * 将相关人员请求转换为持久化实体；未提交新证件号时仅允许复用同记录的历史密文。
     *
     * @param merchantId 平台商户号，不允许为空
     * @param source 页面提交的相关人员，不允许为空
     * @param previous 同人员历史有效记录，可为空
     * @param displayOrder 页面展示顺序，从 0 开始
     * @param now 本次保存时间，不允许为空
     * @return 不包含明文证件号的相关人员实体
     */
    private MerchantRelatedPersonDO toEntity(String merchantId,
                                             MerchantOnboardingDTOs.RelatedPerson source,
                                             MerchantRelatedPersonDO previous,
                                             int displayOrder,
                                             LocalDateTime now) {
        MerchantRelatedPersonDO row = new MerchantRelatedPersonDO();
        row.setMerchantId(merchantId);
        row.setFullName(source.getFullName().trim());
        row.setPersonRoles(join(source.getPersonRoles()));
        row.setNationality(upper(source.getNationality()));
        row.setDateOfBirth(source.getDateOfBirth());
        row.setResidenceCountry(upper(source.getResidenceCountry()));
        row.setResidentialAddress(trimToNull(source.getResidentialAddress()));
        row.setIdType(upper(source.getIdType()));
        if (StringUtils.hasText(source.getIdNumber())) {
            row.setIdNumberCipher(sensitiveCrypto.encryptIdNumber(merchantId, displayOrder, source.getIdNumber()));
            row.setIdNumberMasked(sensitiveCrypto.maskIdNumber(source.getIdNumber()));
        } else if (previous != null) {
            row.setIdNumberCipher(previous.getIdNumberCipher());
            row.setIdNumberMasked(previous.getIdNumberMasked());
        }
        row.setIdExpiryDate(source.getIdExpiryDate());
        row.setOwnershipPercentage(source.getOwnershipPercentage());
        row.setControllerFlag(bool(source.getControllerFlag()));
        row.setPepFlag(bool(source.getPepFlag()));
        row.setEmail(lower(source.getEmail()));
        row.setPhone(trimToNull(source.getPhone()));
        row.setDisplayOrder(displayOrder);
        row.setGmtCreate(now);
        row.setGmtModified(now);
        row.setDeleted(NOT_DELETED);
        return row;
    }

    /**
     * 加载商户进件关联人员并转换为响应模型。
     */
    private List<MerchantOnboardingDTOs.RelatedPerson> loadRelatedPersons(String merchantId) {
        return relatedPersonMapper.selectList(Wrappers.<MerchantRelatedPersonDO>lambdaQuery()
                        .eq(MerchantRelatedPersonDO::getMerchantId, merchantId)
                        .eq(MerchantRelatedPersonDO::getDeleted, NOT_DELETED)
                        .orderByAsc(MerchantRelatedPersonDO::getDisplayOrder)
                        .orderByAsc(MerchantRelatedPersonDO::getId))
                .stream().map(this::toRelatedPerson).toList();
    }

    /**
     * 转换为商户关联自然人信息。
     */
    private MerchantOnboardingDTOs.RelatedPerson toRelatedPerson(MerchantRelatedPersonDO row) {
        MerchantOnboardingDTOs.RelatedPerson dto = new MerchantOnboardingDTOs.RelatedPerson();
        dto.setId(row.getId());
        dto.setFullName(row.getFullName());
        dto.setPersonRoles(split(row.getPersonRoles()));
        dto.setNationality(row.getNationality());
        dto.setDateOfBirth(row.getDateOfBirth());
        dto.setResidenceCountry(row.getResidenceCountry());
        dto.setResidentialAddress(row.getResidentialAddress());
        dto.setIdType(row.getIdType());
        dto.setIdNumber(null);
        dto.setIdNumberMasked(row.getIdNumberMasked());
        dto.setIdExpiryDate(row.getIdExpiryDate());
        dto.setOwnershipPercentage(row.getOwnershipPercentage());
        dto.setControllerFlag(Integer.valueOf(1).equals(row.getControllerFlag()));
        dto.setPepFlag(Integer.valueOf(1).equals(row.getPepFlag()));
        dto.setEmail(row.getEmail());
        dto.setPhone(row.getPhone());
        return dto;
    }

    /**
     * 加载商户进件资料并转换为响应模型。
     */
    private List<MerchantOnboardingDTOs.Document> loadDocuments(String merchantId) {
        return documentMapper.selectList(Wrappers.<BizDocumentDO>lambdaQuery()
                        .eq(BizDocumentDO::getBizType, DOCUMENT_BIZ_TYPE)
                        .eq(BizDocumentDO::getBizId, merchantId)
                        .eq(BizDocumentDO::getDeleted, NOT_DELETED)
                        .orderByDesc(BizDocumentDO::getGmtCreate)
                        .orderByDesc(BizDocumentDO::getId))
                .stream().map(this::toDocument).toList();
    }

    /**
     * 将商户资料实体转换为进件文档响应。
     */
    private MerchantOnboardingDTOs.Document toDocument(BizDocumentDO row) {
        MerchantOnboardingDTOs.Document dto = new MerchantOnboardingDTOs.Document();
        dto.setId(row.getId());
        dto.setDocumentType(row.getDocumentType());
        dto.setOriginalFilename(row.getOriginalFilename());
        dto.setContentType(row.getContentType());
        dto.setFileSize(row.getFileSize());
        dto.setSha256(row.getSha256());
        dto.setDocumentStatus(row.getDocumentStatus());
        dto.setGmtCreate(row.getGmtCreate());
        return dto;
    }

    /**
     * 按创建时间倒序加载商户审核记录。
     */
    private List<MerchantOnboardingDTOs.ReviewRecord> loadReviewRecords(String merchantId) {
        return reviewRecordMapper.selectList(Wrappers.<MerchantReviewRecordDO>lambdaQuery()
                        .eq(MerchantReviewRecordDO::getMerchantId, merchantId)
                        .orderByDesc(MerchantReviewRecordDO::getGmtCreate)
                        .orderByDesc(MerchantReviewRecordDO::getId))
                .stream().map(this::toReviewRecord).toList();
    }

    /**
     * 转换为复核记录。
     */
    private MerchantOnboardingDTOs.ReviewRecord toReviewRecord(MerchantReviewRecordDO row) {
        MerchantOnboardingDTOs.ReviewRecord dto = new MerchantOnboardingDTOs.ReviewRecord();
        dto.setId(row.getId());
        dto.setReviewAction(row.getReviewAction());
        dto.setFromStatus(row.getFromStatus());
        dto.setToStatus(row.getToStatus());
        dto.setReviewComment(row.getReviewComment());
        dto.setOperatorName(row.getOperatorName());
        dto.setGmtCreate(row.getGmtCreate());
        return dto;
    }

    /**
     * 追加不可变审核轨迹，记录状态迁移和当前操作人，不更新历史记录。
     *
     * @param merchantId 平台商户号，不允许为空
     * @param action 审核或激活动作编码，不允许为空
     * @param fromStatus 动作前审核状态，不允许为空
     * @param toStatus 动作后审核状态，不允许为空
     * @param comment 审核意见或补件说明，可为空
     */
    private void appendReviewRecord(String merchantId,
                                    String action,
                                    String fromStatus,
                                    String toStatus,
                                    String comment) {
        InternalAuthAccount operator = InternalAuthContextHolder.get();
        MerchantReviewRecordDO record = new MerchantReviewRecordDO();
        record.setMerchantId(merchantId);
        record.setReviewAction(action);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setReviewComment(comment);
        record.setOperatorId(operator == null || operator.getAccountId() == null
                ? null : String.valueOf(operator.getAccountId()));
        record.setOperatorName(operatorName(operator));
        record.setGmtCreate(LocalDateTime.now());
        reviewRecordMapper.insert(record);
    }

    /**
     * 解析当前操作人的审计名称。
     */
    private String operatorName(InternalAuthAccount operator) {
        if (operator == null) return "system";
        if (StringUtils.hasText(operator.getRealName())) return operator.getRealName().trim();
        if (StringUtils.hasText(operator.getLoginAccount())) return operator.getLoginAccount().trim();
        return "system";
    }

    /**
     * 判断商户相关人员是否具有指定角色。
     */
    private boolean hasRole(MerchantOnboardingDTOs.RelatedPerson person, String role) {
        return person.getPersonRoles() != null
                && person.getPersonRoles().stream().anyMatch(item -> role.equalsIgnoreCase(item));
    }

    /**
     * 校验问题列表为空，否则汇总问题并拒绝当前操作。
     */
    private void requireNoIssues(List<String> issues, String prefix) {
        if (!issues.isEmpty()) {
            throw invalid(prefix + issues.stream().map(this::readinessIssueLabel).collect(Collectors.joining("、")));
        }
    }

    /**
     * 校验进件必填字段，并将缺失项编码加入问题列表。
     */
    private void required(List<String> issues, String value, String issueCode) {
        if (!StringUtils.hasText(value)) issues.add(issueCode);
    }

    /**
     * 将稳定问题编码转换为中文业务标签，未知值原样保留以兼容扩展项。
     *
     * @param issueCode 就绪问题编码或历史文案，不允许为空
     * @return 面向管理端操作异常的可读中文标签
     */
    private String readinessIssueLabel(String issueCode) {
        return READINESS_ISSUE_LABELS.getOrDefault(issueCode, issueCode);
    }

    /**
     * 将非空文本集合连接为持久化字符串。
     */
    private String join(List<String> values) {
        if (values == null) return null;
        return values.stream().filter(StringUtils::hasText).map(this::upper).distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 将持久化文本拆分为去重编码集合。
     */
    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) return new ArrayList<>();
        return Arrays.stream(value.split(","))
                .map(String::trim).filter(StringUtils::hasText)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 将动态数据库字段安全转换为布尔值。
     */
    private Integer bool(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    /**
     * 将业务编码规范化为大写文本。
     */
    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    /**
     * 将业务编码规范化为小写文本。
     */
    private String lower(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    /**
     * 去除文本首尾空白并把空串转换为 null。
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 返回默认复核状态。
     */
    private String defaultReviewStatus(String value) {
        return StringUtils.hasText(value) ? value : REVIEW_NOT_SUBMITTED;
    }

    /**
     * 构建商户进件参数校验异常。
     */
    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
