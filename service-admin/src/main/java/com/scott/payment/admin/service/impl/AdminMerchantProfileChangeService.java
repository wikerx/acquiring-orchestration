package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.admin.dto.merchant.AdminMerchantProfileChangeDTOs;
import com.scott.payment.admin.dto.merchant.MerchantOnboardingDTOs;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.BizDocumentDO;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.MerchantRelatedPersonDO;
import com.scott.payment.admin.mapper.BizDocumentMapper;
import com.scott.payment.admin.mapper.MerchantRelatedPersonMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.merchant.entity.MerchantProfileChangeRequestDO;
import com.scott.payment.component.db.merchant.entity.MerchantProfileReviewRecordDO;
import com.scott.payment.component.db.merchant.mapper.MerchantProfileChangeRequestMapper;
import com.scott.payment.component.db.merchant.mapper.MerchantProfileReviewRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantProfileChangeService
 * @date : 2026-09-21 10:10
 * @email : scott_x@163.com
 * @description : 管理端商户资料变更审核服务，保证审批状态、正式主档、人员和资料在同一事务生效
 * @status : create
 */
@Service
public class AdminMerchantProfileChangeService {

    private static final int NOT_DELETED = 0;
    private static final String STATUS_PENDING = "PENDING_REVIEW";
    private static final String STATUS_SUPPLEMENT = "SUPPLEMENT_REQUIRED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final Set<String> DECISIONS = Set.of("PASS", "SUPPLEMENT", "REJECT");

    private final MerchantProfileChangeRequestMapper requestMapper;
    private final MerchantProfileReviewRecordMapper reviewRecordMapper;
    private final BaseMerchantInfoMapper merchantInfoMapper;
    private final MerchantRelatedPersonMapper relatedPersonMapper;
    private final BizDocumentMapper documentMapper;
    private final MerchantOnboardingService onboardingService;
    private final MerchantProfileSensitiveCrypto sensitiveCrypto;
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;
    private final ObjectMapper objectMapper;

    /** 创建管理端商户资料变更审核服务。 */
    public AdminMerchantProfileChangeService(
            MerchantProfileChangeRequestMapper requestMapper,
            MerchantProfileReviewRecordMapper reviewRecordMapper,
            BaseMerchantInfoMapper merchantInfoMapper,
            MerchantRelatedPersonMapper relatedPersonMapper,
            BizDocumentMapper documentMapper,
            MerchantOnboardingService onboardingService,
            MerchantProfileSensitiveCrypto sensitiveCrypto,
            ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator,
            ObjectMapper objectMapper) {
        this.requestMapper = requestMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.merchantInfoMapper = merchantInfoMapper;
        this.relatedPersonMapper = relatedPersonMapper;
        this.documentMapper = documentMapper;
        this.onboardingService = onboardingService;
        this.sensitiveCrypto = sensitiveCrypto;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
        this.objectMapper = objectMapper;
    }

    /** 分页查询资料变更申请，列表不解密敏感快照。 */
    @DS(DataSourceName.MASTER)
    public PageResult<AdminMerchantProfileChangeDTOs.ChangeRequest> page(
            AdminMerchantProfileChangeDTOs.Query query) {
        long pageNo = query == null ? 1 : Math.max(1, query.getPageNo());
        long pageSize = query == null ? 20 : Math.min(100, Math.max(1, query.getPageSize()));
        String merchantId = query == null ? null : trimToNull(query.getMerchantId());
        String status = query == null ? null : upper(query.getStatus());
        IPage<MerchantProfileChangeRequestDO> page = requestMapper.selectPage(
                new Page<>(pageNo, pageSize),
                Wrappers.<MerchantProfileChangeRequestDO>lambdaQuery()
                        .eq(StringUtils.hasText(merchantId), MerchantProfileChangeRequestDO::getMerchantId, merchantId)
                        .eq(StringUtils.hasText(status), MerchantProfileChangeRequestDO::getStatus, status)
                        .eq(MerchantProfileChangeRequestDO::getDeleted, NOT_DELETED)
                        .orderByDesc(MerchantProfileChangeRequestDO::getSubmittedAt)
                        .orderByDesc(MerchantProfileChangeRequestDO::getId));
        Map<String, String> names = loadMerchantNames(page.getRecords());
        List<AdminMerchantProfileChangeDTOs.ChangeRequest> records = page.getRecords().stream()
                .map(row -> toResponse(row, names.get(row.getMerchantId()), false)).toList();
        return PageResult.of(page.getTotal(), pageNo, pageSize, records);
    }

    /** 查询资料变更申请详情并解密快照，证件号明文在响应前清空。 */
    @DS(DataSourceName.MASTER)
    public AdminMerchantProfileChangeDTOs.ChangeRequest get(String requestNo) {
        MerchantProfileChangeRequestDO row = requireRequest(requestNo);
        BaseMerchantInfoDO merchant = requireMerchant(row.getMerchantId());
        return toResponse(row, merchant.getMerchantName(), true);
    }

    /** 执行通过、补件或驳回决定，通过时原子应用快照并激活本申请资料。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantProfileChangeDTOs.ChangeRequest review(
            String requestNo, AdminMerchantProfileChangeDTOs.ReviewRequest request) {
        MerchantProfileChangeRequestDO row = requireRequest(requestNo);
        if (!STATUS_PENDING.equals(row.getStatus())) {
            throw invalid("only pending merchant profile changes can be reviewed");
        }
        String decision = request == null ? null : upper(request.getDecision());
        if (decision == null || !DECISIONS.contains(decision)) {
            throw invalid("review decision must be PASS, SUPPLEMENT or REJECT");
        }
        String comment = request == null ? null : trimToNull(request.getComment());
        if (!"PASS".equals(decision) && comment == null) {
            throw invalid("review comment is required for supplement or rejection");
        }
        AdminMerchantProfileChangeDTOs.ProfileSnapshot proposed = readSnapshot(
                row, row.getProposedSnapshotCipher());
        if ("PASS".equals(decision)) {
            ensureNoStaleChangedFields(row);
        }
        String targetStatus = switch (decision) {
            case "PASS" -> STATUS_APPROVED;
            case "SUPPLEMENT" -> STATUS_SUPPLEMENT;
            default -> STATUS_REJECTED;
        };
        LocalDateTime now = LocalDateTime.now();
        int version = row.getVersion() == null ? 0 : row.getVersion();
        int affected = requestMapper.update(null,
                Wrappers.<MerchantProfileChangeRequestDO>lambdaUpdate()
                        .set(MerchantProfileChangeRequestDO::getStatus, targetStatus)
                        .set(MerchantProfileChangeRequestDO::getActiveFlag,
                                STATUS_SUPPLEMENT.equals(targetStatus) ? 1 : null)
                        .set(MerchantProfileChangeRequestDO::getReviewComment, comment)
                        .set(MerchantProfileChangeRequestDO::getReviewedBy, currentOperatorId())
                        .set(MerchantProfileChangeRequestDO::getReviewedAt, now)
                        .set(MerchantProfileChangeRequestDO::getVersion, version + 1)
                        .set(MerchantProfileChangeRequestDO::getGmtModified, now)
                        .eq(MerchantProfileChangeRequestDO::getId, row.getId())
                        .eq(MerchantProfileChangeRequestDO::getStatus, STATUS_PENDING)
                        .eq(MerchantProfileChangeRequestDO::getVersion, version)
                        .eq(MerchantProfileChangeRequestDO::getDeleted, NOT_DELETED));
        if (affected != 1) {
            throw invalid("merchant profile change status has changed, please refresh");
        }
        if ("PASS".equals(decision)) {
            applySnapshot(row.getMerchantId(), row.getRequestNo(), proposed);
        }
        appendReviewRecord(row, decision, STATUS_PENDING, targetStatus, comment);
        row.setStatus(targetStatus);
        row.setActiveFlag(STATUS_SUPPLEMENT.equals(targetStatus) ? 1 : null);
        row.setReviewComment(comment);
        row.setReviewedAt(now);
        row.setVersion(version + 1);
        row.setGmtModified(now);
        return toResponse(row, requireMerchant(row.getMerchantId()).getMerchantName(), true);
    }

    /** 对申请创建时快照中的实际变更字段执行并发校验，拒绝覆盖后续管理端修改。 */
    private void ensureNoStaleChangedFields(MerchantProfileChangeRequestDO row) {
        AdminMerchantProfileChangeDTOs.ProfileSnapshot original = readSnapshot(
                row, row.getCurrentSnapshotCipher());
        AdminMerchantProfileChangeDTOs.ProfileSnapshot current = loadCurrent(row.getMerchantId());
        JsonNode originalNode = objectMapper.valueToTree(original);
        JsonNode currentNode = objectMapper.valueToTree(current);
        for (String field : readChangedFields(row)) {
            if (!Objects.equals(originalNode.get(field), currentNode.get(field))) {
                throw invalid("merchant profile field changed after submission: " + field);
            }
        }
    }

    /** 只更新需审核的高风险字段，联系人、语言和时区等即时字段保持当前值。 */
    private void applySnapshot(String merchantId,
                               String requestNo,
                               AdminMerchantProfileChangeDTOs.ProfileSnapshot value) {
        BaseMerchantInfoDO merchant = requireMerchant(merchantId);
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, merchantId);
        LocalDateTime now = LocalDateTime.now();
        int affected = merchantInfoMapper.update(null,
                Wrappers.<BaseMerchantInfoDO>lambdaUpdate()
                        .set(BaseMerchantInfoDO::getMerchantName, trimToNull(value.getMerchantName()))
                        .set(BaseMerchantInfoDO::getBillingDescriptor, trimToNull(value.getBillingDescriptor()))
                        .set(BaseMerchantInfoDO::getMerchantType, upper(value.getMerchantType()))
                        .set(BaseMerchantInfoDO::getCountryCode, upper(value.getCountryCode()))
                        .set(BaseMerchantInfoDO::getOperatingCountry, upper(value.getOperatingCountry()))
                        .set(BaseMerchantInfoDO::getBusinessType, upper(value.getBusinessType()))
                        .set(BaseMerchantInfoDO::getIndustryCategory, upper(value.getIndustryCategory()))
                        .set(BaseMerchantInfoDO::getMerchantDescription, trimToNull(value.getMerchantDescription()))
                        .set(BaseMerchantInfoDO::getRegistrationNumber, trimToNull(value.getRegistrationNumber()))
                        .set(BaseMerchantInfoDO::getLegalEntityType, upper(value.getLegalEntityType()))
                        .set(BaseMerchantInfoDO::getIncorporationDate, value.getIncorporationDate())
                        .set(BaseMerchantInfoDO::getIncorporationCountry, upper(value.getIncorporationCountry()))
                        .set(BaseMerchantInfoDO::getRegisteredState, trimToNull(value.getRegisteredState()))
                        .set(BaseMerchantInfoDO::getRegisteredCity, trimToNull(value.getRegisteredCity()))
                        .set(BaseMerchantInfoDO::getRegisteredPostcode, trimToNull(value.getRegisteredPostcode()))
                        .set(BaseMerchantInfoDO::getRegisteredAddress, trimToNull(value.getRegisteredAddress()))
                        .set(BaseMerchantInfoDO::getOperatingSameAsRegistered, bool(value.getOperatingSameAsRegistered()))
                        .set(BaseMerchantInfoDO::getTaxId, trimToNull(value.getTaxId()))
                        .set(BaseMerchantInfoDO::getCompanySize, upper(value.getCompanySize()))
                        .set(BaseMerchantInfoDO::getEmployeeCount, value.getEmployeeCount())
                        .set(BaseMerchantInfoDO::getRegionCode, trimToNull(value.getRegionCode()))
                        .set(BaseMerchantInfoDO::getCity, trimToNull(value.getCity()))
                        .set(BaseMerchantInfoDO::getAddressLine, trimToNull(value.getAddressLine()))
                        .set(BaseMerchantInfoDO::getPostalCode, trimToNull(value.getPostalCode()))
                        .set(BaseMerchantInfoDO::getBusinessModel, upper(value.getBusinessModel()))
                        .set(BaseMerchantInfoDO::getSalesChannels, join(value.getSalesChannels(), false))
                        .set(BaseMerchantInfoDO::getProductsServices, trimToNull(value.getProductsServices()))
                        .set(BaseMerchantInfoDO::getTargetMarkets, join(value.getTargetMarkets(), true))
                        .set(BaseMerchantInfoDO::getCustomerType, upper(value.getCustomerType()))
                        .set(BaseMerchantInfoDO::getTransactionCurrencies, join(value.getTransactionCurrencies(), true))
                        .set(BaseMerchantInfoDO::getExpectedMonthlyVolume, value.getExpectedMonthlyVolume())
                        .set(BaseMerchantInfoDO::getExpectedVolumeCurrency, upper(value.getExpectedVolumeCurrency()))
                        .set(BaseMerchantInfoDO::getAverageTicket, value.getAverageTicket())
                        .set(BaseMerchantInfoDO::getMaxTicket, value.getMaxTicket())
                        .set(BaseMerchantInfoDO::getExpectedMonthlyCount, value.getExpectedMonthlyCount())
                        .set(BaseMerchantInfoDO::getExpectedRefundRate, value.getExpectedRefundRate())
                        .set(BaseMerchantInfoDO::getExpectedChargebackRate, value.getExpectedChargebackRate())
                        .set(BaseMerchantInfoDO::getRecurringPaymentFlag, bool(value.getRecurringPaymentFlag()))
                        .set(BaseMerchantInfoDO::getPresaleFlag, bool(value.getPresaleFlag()))
                        .set(BaseMerchantInfoDO::getFulfillmentDays, value.getFulfillmentDays())
                        .set(BaseMerchantInfoDO::getDigitalGoodsFlag, bool(value.getDigitalGoodsFlag()))
                        .set(BaseMerchantInfoDO::getRestrictedBusinessFlag, bool(value.getRestrictedBusinessFlag()))
                        .set(BaseMerchantInfoDO::getExpectedGoLiveDate, value.getExpectedGoLiveDate())
                        .set(BaseMerchantInfoDO::getWebsiteUrl, trimToNull(value.getWebsiteUrl()))
                        .set(BaseMerchantInfoDO::getAppStoreUrl, trimToNull(value.getAppStoreUrl()))
                        .set(BaseMerchantInfoDO::getGooglePlayUrl, trimToNull(value.getGooglePlayUrl()))
                        .set(BaseMerchantInfoDO::getOtherSalesUrl, trimToNull(value.getOtherSalesUrl()))
                        .set(BaseMerchantInfoDO::getWebsiteLanguages, join(value.getWebsiteLanguages(), false))
                        .set(BaseMerchantInfoDO::getWebsiteLiveFlag, bool(value.getWebsiteLiveFlag()))
                        .set(BaseMerchantInfoDO::getPrivacyPolicyUrl, trimToNull(value.getPrivacyPolicyUrl()))
                        .set(BaseMerchantInfoDO::getRefundPolicyUrl, trimToNull(value.getRefundPolicyUrl()))
                        .set(BaseMerchantInfoDO::getTermsUrl, trimToNull(value.getTermsUrl()))
                        .set(BaseMerchantInfoDO::getShippingPolicyUrl, trimToNull(value.getShippingPolicyUrl()))
                        .set(BaseMerchantInfoDO::getGmtModified, now)
                        .eq(BaseMerchantInfoDO::getId, merchant.getId())
                        .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                        .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED));
        if (affected != 1) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile approval did not affect exactly one row");
        }
        onboardingService.replaceRelatedPersons(merchantId, value.getRelatedPersons(), now);
        documentMapper.update(null, Wrappers.<BizDocumentDO>lambdaUpdate()
                .set(BizDocumentDO::getDocumentStatus, "ACTIVE")
                .set(BizDocumentDO::getGmtModified, now)
                .eq(BizDocumentDO::getBizType, "MERCHANT_KYB")
                .eq(BizDocumentDO::getBizId, merchantId)
                .eq(BizDocumentDO::getRequestNo, requestNo)
                .eq(BizDocumentDO::getDocumentStatus, STATUS_PENDING)
                .eq(BizDocumentDO::getDeleted, NOT_DELETED));
    }

    private AdminMerchantProfileChangeDTOs.ProfileSnapshot loadCurrent(String merchantId) {
        BaseMerchantInfoDO row = requireMerchant(merchantId);
        AdminMerchantProfileChangeDTOs.ProfileSnapshot value = new AdminMerchantProfileChangeDTOs.ProfileSnapshot();
        value.setMerchantName(row.getMerchantName());
        value.setBillingDescriptor(row.getBillingDescriptor());
        value.setMerchantType(row.getMerchantType());
        value.setCountryCode(row.getCountryCode());
        value.setOperatingCountry(row.getOperatingCountry());
        value.setBusinessType(row.getBusinessType());
        value.setIndustryCategory(row.getIndustryCategory());
        value.setMerchantDescription(row.getMerchantDescription());
        value.setRegistrationNumber(row.getRegistrationNumber());
        value.setLegalEntityType(row.getLegalEntityType());
        value.setIncorporationDate(row.getIncorporationDate());
        value.setIncorporationCountry(row.getIncorporationCountry());
        value.setRegisteredState(row.getRegisteredState());
        value.setRegisteredCity(row.getRegisteredCity());
        value.setRegisteredPostcode(row.getRegisteredPostcode());
        value.setRegisteredAddress(row.getRegisteredAddress());
        value.setOperatingSameAsRegistered(toBoolean(row.getOperatingSameAsRegistered()));
        value.setTaxId(row.getTaxId());
        value.setCompanySize(row.getCompanySize());
        value.setEmployeeCount(row.getEmployeeCount());
        value.setRegionCode(row.getRegionCode());
        value.setCity(row.getCity());
        value.setAddressLine(row.getAddressLine());
        value.setPostalCode(row.getPostalCode());
        value.setBusinessModel(row.getBusinessModel());
        value.setSalesChannels(split(row.getSalesChannels()));
        value.setProductsServices(row.getProductsServices());
        value.setTargetMarkets(split(row.getTargetMarkets()));
        value.setCustomerType(row.getCustomerType());
        value.setTransactionCurrencies(split(row.getTransactionCurrencies()));
        value.setExpectedMonthlyVolume(row.getExpectedMonthlyVolume());
        value.setExpectedVolumeCurrency(row.getExpectedVolumeCurrency());
        value.setAverageTicket(row.getAverageTicket());
        value.setMaxTicket(row.getMaxTicket());
        value.setExpectedMonthlyCount(row.getExpectedMonthlyCount());
        value.setExpectedRefundRate(row.getExpectedRefundRate());
        value.setExpectedChargebackRate(row.getExpectedChargebackRate());
        value.setRecurringPaymentFlag(toBoolean(row.getRecurringPaymentFlag()));
        value.setPresaleFlag(toBoolean(row.getPresaleFlag()));
        value.setFulfillmentDays(row.getFulfillmentDays());
        value.setDigitalGoodsFlag(toBoolean(row.getDigitalGoodsFlag()));
        value.setRestrictedBusinessFlag(toBoolean(row.getRestrictedBusinessFlag()));
        value.setExpectedGoLiveDate(row.getExpectedGoLiveDate());
        value.setWebsiteUrl(row.getWebsiteUrl());
        value.setAppStoreUrl(row.getAppStoreUrl());
        value.setGooglePlayUrl(row.getGooglePlayUrl());
        value.setOtherSalesUrl(row.getOtherSalesUrl());
        value.setWebsiteLanguages(split(row.getWebsiteLanguages()));
        value.setWebsiteLiveFlag(toBoolean(row.getWebsiteLiveFlag()));
        value.setPrivacyPolicyUrl(row.getPrivacyPolicyUrl());
        value.setRefundPolicyUrl(row.getRefundPolicyUrl());
        value.setTermsUrl(row.getTermsUrl());
        value.setShippingPolicyUrl(row.getShippingPolicyUrl());
        value.setRelatedPersons(loadRelatedPersons(merchantId));
        return value;
    }

    private List<MerchantOnboardingDTOs.RelatedPerson> loadRelatedPersons(String merchantId) {
        return relatedPersonMapper.selectList(Wrappers.<MerchantRelatedPersonDO>lambdaQuery()
                        .eq(MerchantRelatedPersonDO::getMerchantId, merchantId)
                        .eq(MerchantRelatedPersonDO::getDeleted, NOT_DELETED)
                        .orderByAsc(MerchantRelatedPersonDO::getDisplayOrder)
                        .orderByAsc(MerchantRelatedPersonDO::getId))
                .stream().map(this::toRelatedPerson).toList();
    }

    private MerchantOnboardingDTOs.RelatedPerson toRelatedPerson(MerchantRelatedPersonDO row) {
        MerchantOnboardingDTOs.RelatedPerson value = new MerchantOnboardingDTOs.RelatedPerson();
        value.setId(row.getId());
        value.setFullName(row.getFullName());
        value.setPersonRoles(split(row.getPersonRoles()));
        value.setNationality(row.getNationality());
        value.setDateOfBirth(row.getDateOfBirth());
        value.setResidenceCountry(row.getResidenceCountry());
        value.setResidentialAddress(row.getResidentialAddress());
        value.setIdType(row.getIdType());
        value.setIdNumberMasked(row.getIdNumberMasked());
        value.setIdExpiryDate(row.getIdExpiryDate());
        value.setOwnershipPercentage(row.getOwnershipPercentage());
        value.setControllerFlag(toBoolean(row.getControllerFlag()));
        value.setPepFlag(toBoolean(row.getPepFlag()));
        value.setEmail(row.getEmail());
        value.setPhone(row.getPhone());
        return value;
    }

    private AdminMerchantProfileChangeDTOs.ChangeRequest toResponse(
            MerchantProfileChangeRequestDO row, String merchantName, boolean includeDetail) {
        AdminMerchantProfileChangeDTOs.ChangeRequest response = new AdminMerchantProfileChangeDTOs.ChangeRequest();
        response.setRequestNo(row.getRequestNo());
        response.setMerchantId(row.getMerchantId());
        response.setMerchantName(merchantName);
        response.setStatus(row.getStatus());
        response.setChangedFields(readChangedFields(row));
        response.setSubmitComment(row.getSubmitComment());
        response.setReviewComment(row.getReviewComment());
        response.setSubmittedBy(row.getSubmittedBy());
        response.setReviewedBy(row.getReviewedBy());
        response.setSubmittedAt(row.getSubmittedAt());
        response.setReviewedAt(row.getReviewedAt());
        response.setGmtCreate(row.getGmtCreate());
        response.setGmtModified(row.getGmtModified());
        if (includeDetail) {
            response.setCurrentProfile(sanitize(readSnapshot(row, row.getCurrentSnapshotCipher())));
            response.setProposedProfile(sanitize(readSnapshot(row, row.getProposedSnapshotCipher())));
            response.setDocuments(loadDocuments(row.getMerchantId(), row.getRequestNo()));
        }
        return response;
    }

    private AdminMerchantProfileChangeDTOs.ProfileSnapshot readSnapshot(
            MerchantProfileChangeRequestDO row, String cipher) {
        try {
            String json = sensitiveCrypto.decryptChangeSnapshot(row.getRequestNo(), cipher);
            return objectMapper.readValue(json, AdminMerchantProfileChangeDTOs.ProfileSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile change snapshot is invalid");
        }
    }

    private AdminMerchantProfileChangeDTOs.ProfileSnapshot sanitize(
            AdminMerchantProfileChangeDTOs.ProfileSnapshot source) {
        AdminMerchantProfileChangeDTOs.ProfileSnapshot copy = objectMapper.convertValue(
                source, AdminMerchantProfileChangeDTOs.ProfileSnapshot.class);
        if (copy.getRelatedPersons() != null) {
            copy.getRelatedPersons().forEach(person -> {
                if (StringUtils.hasText(person.getIdNumber())) {
                    person.setIdNumberMasked(sensitiveCrypto.maskIdNumber(person.getIdNumber()));
                }
                person.setIdNumber(null);
            });
        }
        return copy;
    }

    private List<String> readChangedFields(MerchantProfileChangeRequestDO row) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(row.getChangedFieldsJson());
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile change field list is invalid");
        }
    }

    private List<AdminMerchantProfileChangeDTOs.Document> loadDocuments(
            String merchantId, String requestNo) {
        return documentMapper.selectList(Wrappers.<BizDocumentDO>lambdaQuery()
                        .eq(BizDocumentDO::getBizType, "MERCHANT_KYB")
                        .eq(BizDocumentDO::getBizId, merchantId)
                        .eq(BizDocumentDO::getRequestNo, requestNo)
                        .eq(BizDocumentDO::getDeleted, NOT_DELETED)
                        .orderByDesc(BizDocumentDO::getGmtCreate))
                .stream().map(this::toDocument).toList();
    }

    private AdminMerchantProfileChangeDTOs.Document toDocument(BizDocumentDO row) {
        AdminMerchantProfileChangeDTOs.Document response = new AdminMerchantProfileChangeDTOs.Document();
        response.setId(row.getId());
        response.setDocumentType(row.getDocumentType());
        response.setOriginalFilename(row.getOriginalFilename());
        response.setContentType(row.getContentType());
        response.setFileSize(row.getFileSize());
        response.setSha256(row.getSha256());
        response.setDocumentStatus(row.getDocumentStatus());
        response.setGmtCreate(row.getGmtCreate());
        return response;
    }

    private Map<String, String> loadMerchantNames(List<MerchantProfileChangeRequestDO> rows) {
        Set<String> merchantIds = rows.stream().map(MerchantProfileChangeRequestDO::getMerchantId)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        if (merchantIds.isEmpty()) return Collections.emptyMap();
        return merchantInfoMapper.selectList(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .in(BaseMerchantInfoDO::getMerchantId, merchantIds)
                        .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED))
                .stream().collect(Collectors.toMap(
                        BaseMerchantInfoDO::getMerchantId,
                        BaseMerchantInfoDO::getMerchantName,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private MerchantProfileChangeRequestDO requireRequest(String requestNo) {
        if (!StringUtils.hasText(requestNo)) throw invalid("request number is required");
        MerchantProfileChangeRequestDO row = requestMapper.selectOne(
                Wrappers.<MerchantProfileChangeRequestDO>lambdaQuery()
                        .eq(MerchantProfileChangeRequestDO::getRequestNo, requestNo.trim())
                        .eq(MerchantProfileChangeRequestDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1"));
        if (row == null) throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(),
                "merchant profile change request was not found");
        return row;
    }

    private BaseMerchantInfoDO requireMerchant(String merchantId) {
        BaseMerchantInfoDO row = merchantInfoMapper.selectOne(
                Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                        .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1"));
        if (row == null || row.getId() == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant was not found");
        }
        return row;
    }

    private void appendReviewRecord(MerchantProfileChangeRequestDO request,
                                    String action,
                                    String fromStatus,
                                    String toStatus,
                                    String comment) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        MerchantProfileReviewRecordDO record = new MerchantProfileReviewRecordDO();
        record.setMerchantId(request.getMerchantId());
        record.setReviewScope("PROFILE_CHANGE");
        record.setRequestNo(request.getRequestNo());
        record.setReviewAction(action);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setReviewComment(comment);
        record.setOperatorId(currentOperatorId());
        record.setOperatorName(operatorName(account));
        record.setGmtCreate(LocalDateTime.now());
        reviewRecordMapper.insert(record);
    }

    private String currentOperatorId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        return account == null || account.getAccountId() == null
                ? null : String.valueOf(account.getAccountId());
    }

    private String operatorName(InternalAuthAccount account) {
        if (account == null) return "system";
        if (StringUtils.hasText(account.getRealName())) return account.getRealName().trim();
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : "system";
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) return Collections.emptyList();
        return Arrays.stream(value.split(",")).map(String::trim)
                .filter(StringUtils::hasText).distinct().toList();
    }

    private String join(List<String> values, boolean uppercase) {
        if (values == null) return null;
        String joined = values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> uppercase ? value.toUpperCase(Locale.ROOT) : value)
                .distinct().collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private Integer bool(Boolean value) {
        return value == null ? null : value ? 1 : 0;
    }

    private Boolean toBoolean(Integer value) {
        return value == null ? null : value == 1;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
