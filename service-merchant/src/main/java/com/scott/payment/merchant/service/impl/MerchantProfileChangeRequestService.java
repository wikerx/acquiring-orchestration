package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.merchant.entity.MerchantDocumentDO;
import com.scott.payment.component.db.merchant.entity.MerchantProfileChangeRequestDO;
import com.scott.payment.component.db.merchant.entity.MerchantProfileReviewRecordDO;
import com.scott.payment.component.db.merchant.mapper.MerchantDocumentMapper;
import com.scott.payment.component.db.merchant.mapper.MerchantProfileChangeRequestMapper;
import com.scott.payment.component.db.merchant.mapper.MerchantProfileReviewRecordMapper;
import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;
import com.scott.payment.merchant.service.profile.MerchantProfilePayloadCrypto;
import com.scott.payment.merchant.service.profile.MerchantProfileSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileChangeRequestService
 * @date : 2026-09-20 12:00
 * @email : scott_x@163.com
 * @description : 商户资料变更申请领域服务，确保待审核快照与正式商户主档隔离
 * @status : create
 */
@Service
public class MerchantProfileChangeRequestService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_SUPPLEMENT_REQUIRED = "SUPPLEMENT_REQUIRED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    private static final int ACTIVE = 1;
    private static final int NOT_DELETED = 0;
    private static final String DOCUMENT_BIZ_TYPE = "MERCHANT_KYB";
    private static final Set<String> REGISTRATION_DOCUMENT_TYPES =
            Set.of("BUSINESS_LICENSE", "INCORPORATION");
    private static final Set<String> ID_DOCUMENT_TYPES =
            Set.of("LEGAL_REP_ID", "DIRECTOR_ID", "UBO_ID", "AUTHORIZED_PERSON_ID");

    private final MerchantProfileChangeRequestMapper requestMapper;
    private final MerchantProfileReviewRecordMapper reviewRecordMapper;
    private final MerchantDocumentMapper documentMapper;
    private final MerchantProfileSnapshotService snapshotService;
    private final MerchantProfilePayloadCrypto payloadCrypto;
    private final ObjectMapper objectMapper;

    /** 创建商户资料变更申请领域服务。 */
    public MerchantProfileChangeRequestService(MerchantProfileChangeRequestMapper requestMapper,
                                               MerchantProfileReviewRecordMapper reviewRecordMapper,
                                               MerchantDocumentMapper documentMapper,
                                               MerchantProfileSnapshotService snapshotService,
                                               MerchantProfilePayloadCrypto payloadCrypto,
                                               ObjectMapper objectMapper) {
        this.requestMapper = requestMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.documentMapper = documentMapper;
        this.snapshotService = snapshotService;
        this.payloadCrypto = payloadCrypto;
        this.objectMapper = objectMapper;
    }

    /** 将草稿或已补件申请提交管理端审核，使用状态和版本条件阻止重复提交。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MerchantProfileChangeDTOs.ChangeRequest submit(
            String merchantId, String requestNo, String submitComment) {
        MerchantProfileChangeRequestDO row = requireActive(merchantId, requestNo);
        if (!STATUS_DRAFT.equals(row.getStatus())
                && !STATUS_SUPPLEMENT_REQUIRED.equals(row.getStatus())) {
            throw invalid("merchant profile change cannot be submitted in current status");
        }
        String fromStatus = row.getStatus();
        validateForSubmission(row, readProposed(row));
        LocalDateTime now = LocalDateTime.now();
        int version = row.getVersion() == null ? 0 : row.getVersion();
        int affected = requestMapper.update(null,
                Wrappers.<MerchantProfileChangeRequestDO>lambdaUpdate()
                        .set(MerchantProfileChangeRequestDO::getStatus, STATUS_PENDING_REVIEW)
                        .set(MerchantProfileChangeRequestDO::getSubmitComment, trimToNull(submitComment))
                        .set(MerchantProfileChangeRequestDO::getSubmittedBy, currentOperatorId())
                        .set(MerchantProfileChangeRequestDO::getSubmittedAt, now)
                        .set(MerchantProfileChangeRequestDO::getGmtModified, now)
                        .set(MerchantProfileChangeRequestDO::getVersion, version + 1)
                        .eq(MerchantProfileChangeRequestDO::getId, row.getId())
                        .eq(MerchantProfileChangeRequestDO::getMerchantId, merchantId)
                        .eq(MerchantProfileChangeRequestDO::getStatus, fromStatus)
                        .eq(MerchantProfileChangeRequestDO::getVersion, version)
                        .eq(MerchantProfileChangeRequestDO::getDeleted, NOT_DELETED));
        if (affected != 1) {
            throw invalid("merchant profile change status has been updated, please refresh");
        }
        appendReviewRecord(row, "SUBMIT", fromStatus, STATUS_PENDING_REVIEW, submitComment);
        row.setStatus(STATUS_PENDING_REVIEW);
        row.setSubmitComment(trimToNull(submitComment));
        row.setSubmittedAt(now);
        row.setGmtModified(now);
        row.setVersion(version + 1);
        return toResponse(row, readProposed(row), readChangedFields(row));
    }

    /** 撤回尚未完成审核的申请并释放活动申请唯一占位。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MerchantProfileChangeDTOs.ChangeRequest withdraw(String merchantId, String requestNo) {
        MerchantProfileChangeRequestDO row = requireActive(merchantId, requestNo);
        if (STATUS_APPROVED.equals(row.getStatus()) || STATUS_REJECTED.equals(row.getStatus())) {
            throw invalid("completed merchant profile change cannot be withdrawn");
        }
        String fromStatus = row.getStatus();
        LocalDateTime now = LocalDateTime.now();
        int version = row.getVersion() == null ? 0 : row.getVersion();
        int affected = requestMapper.update(null,
                Wrappers.<MerchantProfileChangeRequestDO>lambdaUpdate()
                        .set(MerchantProfileChangeRequestDO::getStatus, STATUS_WITHDRAWN)
                        .set(MerchantProfileChangeRequestDO::getActiveFlag, null)
                        .set(MerchantProfileChangeRequestDO::getGmtModified, now)
                        .set(MerchantProfileChangeRequestDO::getVersion, version + 1)
                        .eq(MerchantProfileChangeRequestDO::getId, row.getId())
                        .eq(MerchantProfileChangeRequestDO::getMerchantId, merchantId)
                        .eq(MerchantProfileChangeRequestDO::getStatus, fromStatus)
                        .eq(MerchantProfileChangeRequestDO::getVersion, version)
                        .eq(MerchantProfileChangeRequestDO::getDeleted, NOT_DELETED));
        if (affected != 1) {
            throw invalid("merchant profile change status has been updated, please refresh");
        }
        appendReviewRecord(row, "WITHDRAW", fromStatus, STATUS_WITHDRAWN, null);
        row.setStatus(STATUS_WITHDRAWN);
        row.setActiveFlag(null);
        row.setGmtModified(now);
        row.setVersion(version + 1);
        return toResponse(row, readProposed(row), readChangedFields(row));
    }

    /** 保存当前认证商户的资料变更草稿，不修改正式商户主档。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MerchantProfileChangeDTOs.ChangeRequest saveDraft(
            String merchantId, MerchantProfileChangeDTOs.ProfileSnapshot proposed) {
        if (proposed == null) {
            throw invalid("merchant profile change snapshot is required");
        }
        MerchantProfileChangeDTOs.ProfileSnapshot current = snapshotService.loadCurrent(merchantId);
        MerchantProfileChangeRequestDO existing = findActive(merchantId);
        if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())
                && !STATUS_SUPPLEMENT_REQUIRED.equals(existing.getStatus())) {
            throw invalid("merchant profile change cannot be edited in current status");
        }
        if (existing != null) {
            preserveSensitivePersonValues(readProposed(existing), proposed);
        }
        List<String> changedFields = changedFields(current, proposed);
        if (changedFields.isEmpty()) {
            throw invalid("merchant profile change contains no differences");
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            MerchantProfileChangeRequestDO row = new MerchantProfileChangeRequestDO();
            row.setRequestNo(PaymentOrderNoGenerator.nextOrderNo("MCR"));
            row.setMerchantId(merchantId);
            row.setStatus(STATUS_DRAFT);
            row.setActiveFlag(ACTIVE);
            row.setCurrentSnapshotCipher(payloadCrypto.encrypt(row.getRequestNo(), toJson(current)));
            row.setProposedSnapshotCipher(payloadCrypto.encrypt(row.getRequestNo(), toJson(proposed)));
            row.setChangedFieldsJson(toJson(changedFields));
            row.setVersion(0);
            row.setGmtCreate(now);
            row.setGmtModified(now);
            row.setDeleted(NOT_DELETED);
            requestMapper.insert(row);
            return toResponse(row, proposed, changedFields);
        }
        String proposedCipher = payloadCrypto.encrypt(existing.getRequestNo(), toJson(proposed));
        String changedFieldsJson = toJson(changedFields);
        String fromStatus = existing.getStatus();
        int version = existing.getVersion() == null ? 0 : existing.getVersion();
        int affected = requestMapper.update(null,
                Wrappers.<MerchantProfileChangeRequestDO>lambdaUpdate()
                        .set(MerchantProfileChangeRequestDO::getProposedSnapshotCipher, proposedCipher)
                        .set(MerchantProfileChangeRequestDO::getChangedFieldsJson, changedFieldsJson)
                        .set(MerchantProfileChangeRequestDO::getGmtModified, now)
                        .set(MerchantProfileChangeRequestDO::getVersion, version + 1)
                        .eq(MerchantProfileChangeRequestDO::getId, existing.getId())
                        .eq(MerchantProfileChangeRequestDO::getMerchantId, merchantId)
                        .eq(MerchantProfileChangeRequestDO::getStatus, fromStatus)
                        .eq(MerchantProfileChangeRequestDO::getVersion, version)
                        .eq(MerchantProfileChangeRequestDO::getDeleted, NOT_DELETED));
        if (affected != 1) {
            throw invalid("merchant profile change status has been updated, please refresh");
        }
        existing.setProposedSnapshotCipher(proposedCipher);
        existing.setChangedFieldsJson(changedFieldsJson);
        existing.setGmtModified(now);
        existing.setVersion(version + 1);
        return toResponse(existing, proposed, changedFields);
    }

    /** 查询商户资料工作区，敏感证件号只返回脱敏值。 */
    @DS(DataSourceName.MASTER)
    public MerchantProfileChangeDTOs.Workspace getWorkspace(String merchantId) {
        MerchantProfileChangeDTOs.Workspace workspace = new MerchantProfileChangeDTOs.Workspace();
        MerchantProfileChangeDTOs.ProfileSnapshot current = snapshotService.loadCurrent(merchantId);
        workspace.setCurrentProfile(current);
        MerchantProfileChangeRequestDO active = findActive(merchantId);
        MerchantProfileChangeDTOs.ProfileSnapshot completenessProfile = current;
        String activeRequestNo = null;
        if (active != null) {
            MerchantProfileChangeDTOs.ProfileSnapshot proposed = readProposed(active);
            workspace.setActiveRequest(toResponse(active, proposed, readChangedFields(active)));
            completenessProfile = proposed;
            activeRequestNo = active.getRequestNo();
        }
        List<MerchantProfileChangeDTOs.ChangeRequest> history = requestMapper.selectList(
                        Wrappers.<MerchantProfileChangeRequestDO>lambdaQuery()
                                .eq(MerchantProfileChangeRequestDO::getMerchantId, merchantId)
                                .eq(MerchantProfileChangeRequestDO::getDeleted, NOT_DELETED)
                                .orderByDesc(MerchantProfileChangeRequestDO::getGmtCreate)
                                .last("LIMIT 50"))
                .stream().map(row -> toResponse(row, null, readChangedFields(row))).toList();
        workspace.setRequestHistory(history);
        workspace.setDocuments(loadDocuments(merchantId));
        List<String> issues = completenessIssues(
                completenessProfile, workspace.getDocuments(), activeRequestNo);
        workspace.setCompletenessIssues(issues);
        workspace.setCompletenessPercent(Math.max(0, 100 - issues.size() * 5));
        return workspace;
    }

    /** 查询当前商户指定申请详情，不允许跨商户读取。 */
    @DS(DataSourceName.MASTER)
    public MerchantProfileChangeDTOs.ChangeRequest getRequest(String merchantId, String requestNo) {
        MerchantProfileChangeRequestDO row = requestMapper.selectOne(
                Wrappers.<MerchantProfileChangeRequestDO>lambdaQuery()
                        .eq(MerchantProfileChangeRequestDO::getMerchantId, merchantId)
                        .eq(MerchantProfileChangeRequestDO::getRequestNo, requestNo)
                        .eq(MerchantProfileChangeRequestDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1"));
        if (row == null) {
            throw invalid("merchant profile change request was not found");
        }
        return toResponse(row, readProposed(row), readChangedFields(row));
    }

    /** 查询商户当前尚未进入终态的资料变更申请。 */
    public MerchantProfileChangeRequestDO findActive(String merchantId) {
        return requestMapper.selectOne(Wrappers.<MerchantProfileChangeRequestDO>lambdaQuery()
                .eq(MerchantProfileChangeRequestDO::getMerchantId, merchantId)
                .eq(MerchantProfileChangeRequestDO::getActiveFlag, ACTIVE)
                .eq(MerchantProfileChangeRequestDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    /** 查询并校验活动申请归属于当前认证商户。 */
    private MerchantProfileChangeRequestDO requireActive(String merchantId, String requestNo) {
        MerchantProfileChangeRequestDO row = findActive(merchantId);
        if (row == null || requestNo == null || !requestNo.equals(row.getRequestNo())) {
            throw invalid("merchant profile change request was not found");
        }
        return row;
    }

    /** 计算两个资料快照之间发生变化的稳定字段编码。 */
    private List<String> changedFields(MerchantProfileChangeDTOs.ProfileSnapshot current,
                                       MerchantProfileChangeDTOs.ProfileSnapshot proposed) {
        ObjectNode currentNode = objectMapper.valueToTree(current);
        ObjectNode proposedNode = objectMapper.valueToTree(proposed);
        currentNode.remove("capturedAt");
        proposedNode.remove("capturedAt");
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : proposedNode.properties()) {
            if (!field.getValue().equals(currentNode.get(field.getKey()))) {
                changed.add(field.getKey());
            }
        }
        changed.sort(String::compareTo);
        return changed;
    }

    /** 将申请实体转换为不暴露密文的商户响应。 */
    private MerchantProfileChangeDTOs.ChangeRequest toResponse(
            MerchantProfileChangeRequestDO row,
            MerchantProfileChangeDTOs.ProfileSnapshot proposed,
            List<String> changedFields) {
        MerchantProfileChangeDTOs.ChangeRequest response = new MerchantProfileChangeDTOs.ChangeRequest();
        response.setRequestNo(row.getRequestNo());
        response.setMerchantId(row.getMerchantId());
        response.setStatus(row.getStatus());
        response.setChangedFields(changedFields);
        response.setProposedProfile(sanitize(proposed));
        response.setSubmitComment(row.getSubmitComment());
        response.setReviewComment(row.getReviewComment());
        response.setSubmittedAt(row.getSubmittedAt());
        response.setReviewedAt(row.getReviewedAt());
        response.setGmtCreate(row.getGmtCreate());
        response.setGmtModified(row.getGmtModified());
        return response;
    }

    /** 解密申请中的拟变更快照；任何解析失败均不返回密文或原始正文。 */
    private MerchantProfileChangeDTOs.ProfileSnapshot readProposed(MerchantProfileChangeRequestDO row) {
        try {
            String json = payloadCrypto.decrypt(row.getRequestNo(), row.getProposedSnapshotCipher());
            return objectMapper.readValue(json, MerchantProfileChangeDTOs.ProfileSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile change snapshot is invalid");
        }
    }

    /** 解析稳定字段编码列表。 */
    private List<String> readChangedFields(MerchantProfileChangeRequestDO row) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(row.getChangedFieldsJson());
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile change field list is invalid");
        }
    }

    /** 校验正式提交所需企业、经营、网站、相关人员和资料完整性。 */
    private void validateForSubmission(MerchantProfileChangeRequestDO row,
                                       MerchantProfileChangeDTOs.ProfileSnapshot profile) {
        List<String> issues = completenessIssues(
                profile, loadDocuments(row.getMerchantId()), row.getRequestNo());
        if (!issues.isEmpty()) {
            throw invalid("merchant profile change is incomplete: " + String.join(",", issues));
        }
    }

    private List<String> completenessIssues(MerchantProfileChangeDTOs.ProfileSnapshot profile,
                                            List<MerchantProfileChangeDTOs.Document> documents,
                                            String activeRequestNo) {
        List<String> issues = new ArrayList<>();
        required(issues, profile.getMerchantName(), "MERCHANT_NAME");
        required(issues, profile.getMerchantType(), "MERCHANT_TYPE");
        required(issues, profile.getCountryCode(), "COUNTRY_CODE");
        required(issues, profile.getOperatingCountry(), "OPERATING_COUNTRY");
        required(issues, profile.getMerchantDescription(), "MERCHANT_DESCRIPTION");
        required(issues, profile.getRegistrationNumber(), "REGISTRATION_NUMBER");
        required(issues, profile.getLegalEntityType(), "LEGAL_ENTITY_TYPE");
        if (profile.getIncorporationDate() == null) issues.add("INCORPORATION_DATE");
        required(issues, profile.getIncorporationCountry(), "INCORPORATION_COUNTRY");
        required(issues, profile.getRegisteredCity(), "REGISTERED_CITY");
        required(issues, profile.getRegisteredAddress(), "REGISTERED_ADDRESS");
        required(issues, profile.getBusinessModel(), "BUSINESS_MODEL");
        if (profile.getSalesChannels() == null || profile.getSalesChannels().isEmpty()) issues.add("SALES_CHANNELS");
        required(issues, profile.getProductsServices(), "PRODUCTS_SERVICES");
        if (profile.getTargetMarkets() == null || profile.getTargetMarkets().isEmpty()) issues.add("TARGET_MARKETS");
        required(issues, profile.getCustomerType(), "CUSTOMER_TYPE");
        if (profile.getTransactionCurrencies() == null || profile.getTransactionCurrencies().isEmpty()) {
            issues.add("TRANSACTION_CURRENCIES");
        }
        if (profile.getExpectedMonthlyVolume() == null) issues.add("EXPECTED_MONTHLY_VOLUME");
        required(issues, profile.getExpectedVolumeCurrency(), "EXPECTED_VOLUME_CURRENCY");
        if (profile.getAverageTicket() == null) issues.add("AVERAGE_TICKET");
        if (profile.getWebsiteLiveFlag() == null) issues.add("WEBSITE_LIVE_FLAG");
        if (Boolean.TRUE.equals(profile.getWebsiteLiveFlag())) {
            required(issues, profile.getWebsiteUrl(), "WEBSITE_URL");
            required(issues, profile.getPrivacyPolicyUrl(), "PRIVACY_POLICY_URL");
            required(issues, profile.getRefundPolicyUrl(), "REFUND_POLICY_URL");
            required(issues, profile.getTermsUrl(), "TERMS_URL");
        } else if (!StringUtils.hasText(profile.getOtherSalesUrl())) {
            issues.add("OTHER_SALES_URL_WHEN_WEBSITE_OFFLINE");
        }
        validatePersons(issues, profile);
        validateDocuments(issues, documents, activeRequestNo);
        return issues;
    }

    private void validatePersons(List<String> issues, MerchantProfileChangeDTOs.ProfileSnapshot profile) {
        List<MerchantProfileChangeDTOs.RelatedPerson> persons = profile.getRelatedPersons() == null
                ? List.of() : profile.getRelatedPersons();
        if (persons.isEmpty()) {
            issues.add("RELATED_PERSONS");
            return;
        }
        if (persons.stream().noneMatch(person -> hasRole(person, "LEGAL_REPRESENTATIVE"))) {
            issues.add("LEGAL_REPRESENTATIVE");
        }
        if (!"SOLE_TRADER".equalsIgnoreCase(profile.getMerchantType())
                && persons.stream().noneMatch(person -> hasRole(person, "UBO"))) {
            issues.add("UBO");
        }
        if (persons.stream().anyMatch(person -> !StringUtils.hasText(person.getIdNumber())
                && !StringUtils.hasText(person.getIdNumberMasked()))) {
            issues.add("RELATED_PERSON_ID_NUMBER");
        }
    }

    private boolean hasRole(MerchantProfileChangeDTOs.RelatedPerson person, String role) {
        return person != null && person.getPersonRoles() != null
                && person.getPersonRoles().stream().anyMatch(role::equalsIgnoreCase);
    }

    private void validateDocuments(List<String> issues,
                                   List<MerchantProfileChangeDTOs.Document> documents,
                                   String activeRequestNo) {
        Set<String> types = documents.stream()
                .filter(document -> !"DELETED".equalsIgnoreCase(document.getDocumentStatus()))
                .filter(document -> document.getRequestNo() == null
                        || Objects.equals(activeRequestNo, document.getRequestNo()))
                .filter(document -> document.getRequestNo() != null
                        || "ACTIVE".equalsIgnoreCase(document.getDocumentStatus())
                        || "UPLOADED".equalsIgnoreCase(document.getDocumentStatus()))
                .map(MerchantProfileChangeDTOs.Document::getDocumentType)
                .filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (types.stream().noneMatch(REGISTRATION_DOCUMENT_TYPES::contains)) {
            issues.add("BUSINESS_REGISTRATION_DOCUMENT");
        }
        if (types.stream().noneMatch(ID_DOCUMENT_TYPES::contains)) {
            issues.add("RELATED_PERSON_ID_DOCUMENT");
        }
    }

    private List<MerchantProfileChangeDTOs.Document> loadDocuments(String merchantId) {
        return documentMapper.selectList(Wrappers.<MerchantDocumentDO>lambdaQuery()
                        .eq(MerchantDocumentDO::getBizType, DOCUMENT_BIZ_TYPE)
                        .eq(MerchantDocumentDO::getBizId, merchantId)
                        .eq(MerchantDocumentDO::getDeleted, NOT_DELETED)
                        .orderByDesc(MerchantDocumentDO::getGmtCreate))
                .stream().map(this::toDocument).toList();
    }

    private MerchantProfileChangeDTOs.Document toDocument(MerchantDocumentDO row) {
        MerchantProfileChangeDTOs.Document response = new MerchantProfileChangeDTOs.Document();
        response.setId(row.getId());
        response.setRequestNo(row.getRequestNo());
        response.setDocumentType(row.getDocumentType());
        response.setOriginalFilename(row.getOriginalFilename());
        response.setContentType(row.getContentType());
        response.setFileSize(row.getFileSize());
        response.setSha256(row.getSha256());
        response.setDocumentStatus(row.getDocumentStatus());
        response.setGmtCreate(row.getGmtCreate());
        return response;
    }

    private MerchantProfileChangeDTOs.ProfileSnapshot sanitize(
            MerchantProfileChangeDTOs.ProfileSnapshot source) {
        if (source == null) return null;
        MerchantProfileChangeDTOs.ProfileSnapshot copy = objectMapper.convertValue(
                source, MerchantProfileChangeDTOs.ProfileSnapshot.class);
        if (copy.getRelatedPersons() != null) {
            copy.getRelatedPersons().forEach(person -> {
                if (StringUtils.hasText(person.getIdNumber())) {
                    person.setIdNumberMasked(payloadCrypto.maskIdNumber(person.getIdNumber()));
                }
                person.setIdNumber(null);
            });
        }
        return copy;
    }

    /** 草稿重存时保留同一申请内已加密的新证件号，避免脱敏响应覆盖原值。 */
    private void preserveSensitivePersonValues(
            MerchantProfileChangeDTOs.ProfileSnapshot previous,
            MerchantProfileChangeDTOs.ProfileSnapshot proposed) {
        if (previous == null || previous.getRelatedPersons() == null
                || proposed.getRelatedPersons() == null) {
            return;
        }
        for (MerchantProfileChangeDTOs.RelatedPerson person : proposed.getRelatedPersons()) {
            if (person == null || StringUtils.hasText(person.getIdNumber())) {
                continue;
            }
            previous.getRelatedPersons().stream()
                    .filter(candidate -> samePerson(candidate, person))
                    .map(MerchantProfileChangeDTOs.RelatedPerson::getIdNumber)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .ifPresent(person::setIdNumber);
        }
    }

    private boolean samePerson(MerchantProfileChangeDTOs.RelatedPerson previous,
                               MerchantProfileChangeDTOs.RelatedPerson proposed) {
        if (previous == null) {
            return false;
        }
        if (proposed.getId() != null) {
            return Objects.equals(previous.getId(), proposed.getId());
        }
        String previousMask = StringUtils.hasText(previous.getIdNumber())
                ? payloadCrypto.maskIdNumber(previous.getIdNumber()) : previous.getIdNumberMasked();
        return StringUtils.hasText(proposed.getIdNumberMasked())
                && Objects.equals(previousMask, proposed.getIdNumberMasked())
                && Objects.equals(trimToNull(previous.getFullName()), trimToNull(proposed.getFullName()))
                && Objects.equals(previous.getDateOfBirth(), proposed.getDateOfBirth())
                && Objects.equals(upper(previous.getIdType()), upper(proposed.getIdType()));
    }

    private void required(List<String> issues, String value, String code) {
        if (!StringUtils.hasText(value)) issues.add(code);
    }

    /** 追加资料变更审核轨迹；历史记录只新增不覆盖。 */
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
        record.setReviewComment(trimToNull(comment));
        record.setOperatorId(account == null || account.getAccountId() == null
                ? null : String.valueOf(account.getAccountId()));
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
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName().trim();
        }
        return account.getLoginAccount() == null ? "system" : account.getLoginAccount().trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile change serialization failed");
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
