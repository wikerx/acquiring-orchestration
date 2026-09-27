package com.scott.payment.merchant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.component.db.merchant.entity.MerchantDocumentDO;
import com.scott.payment.component.db.merchant.entity.MerchantProfileChangeRequestDO;
import com.scott.payment.component.db.merchant.entity.MerchantProfileReviewRecordDO;
import com.scott.payment.component.db.merchant.mapper.MerchantDocumentMapper;
import com.scott.payment.component.db.merchant.mapper.MerchantProfileChangeRequestMapper;
import com.scott.payment.component.db.merchant.mapper.MerchantProfileReviewRecordMapper;
import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;
import com.scott.payment.merchant.service.profile.MerchantProfilePayloadCrypto;
import com.scott.payment.merchant.service.profile.MerchantProfileSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileChangeRequestServiceTest
 * @date : 2026-09-20 12:00
 * @email : scott_x@163.com
 * @description : 商户资料变更申请状态机测试，验证草稿提交不会提前修改正式商户主档
 * @status : create
 */
@Slf4j
class MerchantProfileChangeRequestServiceTest {

    /** 初始化资料变更申请实体的 MyBatis-Plus 字段元数据。 */
    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, MerchantProfileChangeRequestDO.class);
        TableInfoHelper.initTableInfo(assistant, MerchantDocumentDO.class);
    }

    /** 新建草稿只保存加密快照，不得触碰正式商户主档。 */
    @Test
    void shouldCreateDraftWithoutApplyingProfileChanges() {
        log.info("测试资料变更草稿，关键输入: 已激活商户修改法定名称");
        MerchantProfileChangeRequestMapper requestMapper = mock(MerchantProfileChangeRequestMapper.class);
        MerchantProfileSnapshotService snapshotService = mock(MerchantProfileSnapshotService.class);
        MerchantProfilePayloadCrypto payloadCrypto = mock(MerchantProfilePayloadCrypto.class);
        MerchantProfileReviewRecordMapper reviewRecordMapper = mock(MerchantProfileReviewRecordMapper.class);
        MerchantDocumentMapper documentMapper = mock(MerchantDocumentMapper.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MerchantProfileChangeDTOs.ProfileSnapshot current = snapshot("Current Legal Name");
        MerchantProfileChangeDTOs.ProfileSnapshot proposed = snapshot("Updated Legal Name");
        when(snapshotService.loadCurrent("200045")).thenReturn(current);
        when(requestMapper.selectOne(any())).thenReturn(null);
        when(payloadCrypto.encrypt(any(), any())).thenReturn("cipher-text");
        when(payloadCrypto.decrypt(any(), any())).thenReturn(objectMapper.valueToTree(proposed).toString());

        MerchantProfileChangeRequestService service = new MerchantProfileChangeRequestService(
                requestMapper, reviewRecordMapper, documentMapper, snapshotService, payloadCrypto, objectMapper);
        MerchantProfileChangeDTOs.ChangeRequest result = service.saveDraft("200045", proposed);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getRequestNo()).startsWith("MCR");
        assertThat(result.getChangedFields()).contains("merchantName");
        verify(requestMapper).insert(any(MerchantProfileChangeRequestDO.class));
        verify(snapshotService, never()).apply(any(), any());
        log.info("资料变更草稿测试完成，结果: 草稿已保存且正式资料未变更");
    }

    /** 草稿提交后必须进入待审核且仍不得应用正式资料。 */
    @Test
    void shouldSubmitDraftForReviewWithoutApplyingProfileChanges() {
        log.info("测试资料变更提交，关键输入: DRAFT 申请");
        MerchantProfileChangeRequestMapper requestMapper = mock(MerchantProfileChangeRequestMapper.class);
        MerchantProfileReviewRecordMapper reviewRecordMapper = mock(MerchantProfileReviewRecordMapper.class);
        MerchantDocumentMapper documentMapper = mock(MerchantDocumentMapper.class);
        MerchantProfileSnapshotService snapshotService = mock(MerchantProfileSnapshotService.class);
        MerchantProfilePayloadCrypto payloadCrypto = mock(MerchantProfilePayloadCrypto.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MerchantProfileChangeRequestDO draft = draftRequest();
        when(requestMapper.selectOne(any())).thenReturn(draft);
        when(requestMapper.update(any(), any())).thenReturn(1);
        when(payloadCrypto.decrypt(draft.getRequestNo(), draft.getProposedSnapshotCipher()))
                .thenReturn(objectMapper.valueToTree(snapshot("Updated Legal Name")).toString());
        when(documentMapper.selectList(any())).thenReturn(requiredDocuments());

        MerchantProfileChangeRequestService service = new MerchantProfileChangeRequestService(
                requestMapper, reviewRecordMapper, documentMapper, snapshotService, payloadCrypto, objectMapper);
        MerchantProfileChangeDTOs.ChangeRequest result = service.submit(
                "200045", draft.getRequestNo(), "Legal name correction");

        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
        verify(reviewRecordMapper).insert(any(MerchantProfileReviewRecordDO.class));
        verify(snapshotService, never()).apply(any(), any());
        log.info("资料变更提交测试完成，结果: 申请进入待审核且正式资料未变更");
    }

    /** 待审核申请必须保持只读，不能被新草稿覆盖。 */
    @Test
    void shouldRejectDraftOverwriteWhileReviewIsPending() {
        log.info("测试待审核资料保护，关键输入: PENDING_REVIEW 申请再次保存");
        MerchantProfileChangeRequestMapper requestMapper = mock(MerchantProfileChangeRequestMapper.class);
        MerchantProfileReviewRecordMapper reviewRecordMapper = mock(MerchantProfileReviewRecordMapper.class);
        MerchantDocumentMapper documentMapper = mock(MerchantDocumentMapper.class);
        MerchantProfileSnapshotService snapshotService = mock(MerchantProfileSnapshotService.class);
        MerchantProfilePayloadCrypto payloadCrypto = mock(MerchantProfilePayloadCrypto.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MerchantProfileChangeRequestDO pending = draftRequest();
        pending.setStatus("PENDING_REVIEW");
        when(snapshotService.loadCurrent("200045")).thenReturn(snapshot("Current Legal Name"));
        when(requestMapper.selectOne(any())).thenReturn(pending);

        MerchantProfileChangeRequestService service = new MerchantProfileChangeRequestService(
                requestMapper, reviewRecordMapper, documentMapper, snapshotService, payloadCrypto, objectMapper);

        assertThatThrownBy(() -> service.saveDraft("200045", snapshot("Updated Legal Name")))
                .hasMessageContaining("cannot be edited in current status");
        verify(requestMapper, never()).updateById(any(MerchantProfileChangeRequestDO.class));
        verify(payloadCrypto, never()).encrypt(any(), any());
        verifyNoInteractions(reviewRecordMapper);
        log.info("待审核资料保护测试完成，结果: 旧申请未被覆盖");
    }

    /** 草稿重存必须保留新相关人员的证件号明文，仅在响应中返回脱敏值。 */
    @Test
    void shouldPreserveNewPersonIdNumberWhenDraftIsSavedAgain() throws Exception {
        MerchantProfileChangeRequestMapper requestMapper = mock(MerchantProfileChangeRequestMapper.class);
        MerchantProfileReviewRecordMapper reviewRecordMapper = mock(MerchantProfileReviewRecordMapper.class);
        MerchantDocumentMapper documentMapper = mock(MerchantDocumentMapper.class);
        MerchantProfileSnapshotService snapshotService = mock(MerchantProfileSnapshotService.class);
        MerchantProfilePayloadCrypto payloadCrypto = mock(MerchantProfilePayloadCrypto.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MerchantProfileChangeRequestDO draft = draftRequest();
        MerchantProfileChangeDTOs.ProfileSnapshot current = snapshot("Current Legal Name");
        current.setRelatedPersons(List.of());
        MerchantProfileChangeDTOs.ProfileSnapshot previous = snapshot("Updated Legal Name");
        MerchantProfileChangeDTOs.RelatedPerson previousPerson = newRelatedPerson("P123456789");
        previous.setRelatedPersons(List.of(previousPerson));
        MerchantProfileChangeDTOs.ProfileSnapshot proposed = snapshot("Updated Legal Name");
        MerchantProfileChangeDTOs.RelatedPerson proposedPerson = newRelatedPerson(null);
        proposedPerson.setIdNumberMasked("P12***6789");
        proposed.setRelatedPersons(List.of(proposedPerson));
        when(snapshotService.loadCurrent("200045")).thenReturn(current);
        when(requestMapper.selectOne(any())).thenReturn(draft);
        when(payloadCrypto.decrypt(draft.getRequestNo(), draft.getProposedSnapshotCipher()))
                .thenReturn(objectMapper.writeValueAsString(previous));
        when(payloadCrypto.maskIdNumber("P123456789")).thenReturn("P12***6789");
        when(payloadCrypto.encrypt(eq(draft.getRequestNo()), anyString())).thenReturn("updated-cipher");
        when(requestMapper.update(any(), any())).thenReturn(1);

        MerchantProfileChangeRequestService service = new MerchantProfileChangeRequestService(
                requestMapper, reviewRecordMapper, documentMapper, snapshotService, payloadCrypto, objectMapper);
        MerchantProfileChangeDTOs.ChangeRequest result = service.saveDraft("200045", proposed);

        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(payloadCrypto).encrypt(eq(draft.getRequestNo()), snapshotCaptor.capture());
        MerchantProfileChangeDTOs.ProfileSnapshot stored = objectMapper.readValue(
                snapshotCaptor.getValue(), MerchantProfileChangeDTOs.ProfileSnapshot.class);
        assertThat(stored.getRelatedPersons().get(0).getIdNumber()).isEqualTo("P123456789");
        assertThat(result.getProposedProfile().getRelatedPersons().get(0).getIdNumber()).isNull();
        assertThat(result.getProposedProfile().getRelatedPersons().get(0).getIdNumberMasked())
                .isEqualTo("P12***6789");
        assertThat(draft.getVersion()).isEqualTo(1);
    }

    /** 草稿并发更新失败时不得以旧版本覆盖已提交或已修改的申请。 */
    @Test
    void shouldRejectDraftUpdateWhenVersionChanges() throws Exception {
        MerchantProfileChangeRequestMapper requestMapper = mock(MerchantProfileChangeRequestMapper.class);
        MerchantProfileReviewRecordMapper reviewRecordMapper = mock(MerchantProfileReviewRecordMapper.class);
        MerchantDocumentMapper documentMapper = mock(MerchantDocumentMapper.class);
        MerchantProfileSnapshotService snapshotService = mock(MerchantProfileSnapshotService.class);
        MerchantProfilePayloadCrypto payloadCrypto = mock(MerchantProfilePayloadCrypto.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MerchantProfileChangeRequestDO draft = draftRequest();
        MerchantProfileChangeDTOs.ProfileSnapshot proposed = snapshot("Updated Legal Name");
        when(snapshotService.loadCurrent("200045")).thenReturn(snapshot("Current Legal Name"));
        when(requestMapper.selectOne(any())).thenReturn(draft);
        when(payloadCrypto.decrypt(draft.getRequestNo(), draft.getProposedSnapshotCipher()))
                .thenReturn(objectMapper.writeValueAsString(proposed));
        when(payloadCrypto.encrypt(eq(draft.getRequestNo()), anyString())).thenReturn("updated-cipher");
        when(requestMapper.update(any(), any())).thenReturn(0);

        MerchantProfileChangeRequestService service = new MerchantProfileChangeRequestService(
                requestMapper, reviewRecordMapper, documentMapper, snapshotService, payloadCrypto, objectMapper);

        assertThatThrownBy(() -> service.saveDraft("200045", proposed))
                .hasMessageContaining("status has been updated");
        verify(requestMapper, never()).updateById(any(MerchantProfileChangeRequestDO.class));
        verifyNoInteractions(reviewRecordMapper);
    }

    /** 补件状态允许修订后重新提交审核。 */
    @Test
    void shouldResubmitSupplementRequiredRequest() {
        log.info("测试补件重提，关键输入: SUPPLEMENT_REQUIRED 申请");
        MerchantProfileChangeRequestMapper requestMapper = mock(MerchantProfileChangeRequestMapper.class);
        MerchantProfileReviewRecordMapper reviewRecordMapper = mock(MerchantProfileReviewRecordMapper.class);
        MerchantDocumentMapper documentMapper = mock(MerchantDocumentMapper.class);
        MerchantProfileSnapshotService snapshotService = mock(MerchantProfileSnapshotService.class);
        MerchantProfilePayloadCrypto payloadCrypto = mock(MerchantProfilePayloadCrypto.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MerchantProfileChangeRequestDO supplement = draftRequest();
        supplement.setStatus("SUPPLEMENT_REQUIRED");
        when(requestMapper.selectOne(any())).thenReturn(supplement);
        when(requestMapper.update(any(), any())).thenReturn(1);
        when(documentMapper.selectList(any())).thenReturn(requiredDocuments());
        when(payloadCrypto.decrypt(supplement.getRequestNo(), supplement.getProposedSnapshotCipher()))
                .thenReturn(objectMapper.valueToTree(snapshot("Updated Legal Name")).toString());

        MerchantProfileChangeRequestService service = new MerchantProfileChangeRequestService(
                requestMapper, reviewRecordMapper, documentMapper, snapshotService, payloadCrypto, objectMapper);
        MerchantProfileChangeDTOs.ChangeRequest result = service.submit(
                "200045", supplement.getRequestNo(), "Supplement completed");

        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
        verify(reviewRecordMapper).insert(any(MerchantProfileReviewRecordDO.class));
        verify(snapshotService, never()).apply(any(), any());
        log.info("补件重提测试完成，结果: 申请重新进入待审核");
    }

    /** 撤回活动申请必须进入终态并释放唯一占位。 */
    @Test
    void shouldWithdrawRequestAndReleaseActiveSlot() {
        log.info("测试资料变更撤回，关键输入: 活动草稿申请");
        MerchantProfileChangeRequestMapper requestMapper = mock(MerchantProfileChangeRequestMapper.class);
        MerchantProfileReviewRecordMapper reviewRecordMapper = mock(MerchantProfileReviewRecordMapper.class);
        MerchantDocumentMapper documentMapper = mock(MerchantDocumentMapper.class);
        MerchantProfileSnapshotService snapshotService = mock(MerchantProfileSnapshotService.class);
        MerchantProfilePayloadCrypto payloadCrypto = mock(MerchantProfilePayloadCrypto.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MerchantProfileChangeRequestDO draft = draftRequest();
        when(requestMapper.selectOne(any())).thenReturn(draft);
        when(requestMapper.update(any(), any())).thenReturn(1);
        when(payloadCrypto.decrypt(draft.getRequestNo(), draft.getProposedSnapshotCipher()))
                .thenReturn(objectMapper.valueToTree(snapshot("Updated Legal Name")).toString());

        MerchantProfileChangeRequestService service = new MerchantProfileChangeRequestService(
                requestMapper, reviewRecordMapper, documentMapper, snapshotService, payloadCrypto, objectMapper);
        MerchantProfileChangeDTOs.ChangeRequest result = service.withdraw("200045", draft.getRequestNo());

        assertThat(result.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(draft.getActiveFlag()).isNull();
        verify(reviewRecordMapper).insert(any(MerchantProfileReviewRecordDO.class));
        verify(snapshotService, never()).apply(any(), any());
        log.info("资料变更撤回测试完成，结果: 活动申请占位已释放");
    }

    /** 构造变更申请资料快照。 */
    private MerchantProfileChangeDTOs.ProfileSnapshot snapshot(String merchantName) {
        MerchantProfileChangeDTOs.ProfileSnapshot snapshot = new MerchantProfileChangeDTOs.ProfileSnapshot();
        snapshot.setMerchantName(merchantName);
        snapshot.setMerchantType("COMPANY");
        snapshot.setCountryCode("USA");
        snapshot.setOperatingCountry("USA");
        snapshot.setBusinessType("ECOMMERCE");
        snapshot.setIndustryCategory("RETAIL");
        snapshot.setMerchantDescription("Online retail business");
        snapshot.setRegistrationNumber("REG-200045");
        snapshot.setLegalEntityType("LIMITED_COMPANY");
        snapshot.setIncorporationDate(LocalDate.of(2024, 1, 1));
        snapshot.setIncorporationCountry("USA");
        snapshot.setRegisteredCity("San Francisco");
        snapshot.setRegisteredAddress("1 Market Street");
        snapshot.setBusinessModel("B2C");
        snapshot.setSalesChannels(List.of("WEBSITE"));
        snapshot.setProductsServices("Retail goods");
        snapshot.setTargetMarkets(List.of("USA"));
        snapshot.setCustomerType("CONSUMER");
        snapshot.setTransactionCurrencies(List.of("USD"));
        snapshot.setExpectedMonthlyVolume(new BigDecimal("100000"));
        snapshot.setExpectedVolumeCurrency("USD");
        snapshot.setAverageTicket(new BigDecimal("80"));
        snapshot.setWebsiteLiveFlag(false);
        snapshot.setOtherSalesUrl("https://market.example.com/store");
        snapshot.setRelatedPersons(requiredPersons());
        snapshot.setCapturedAt(LocalDateTime.of(2026, 9, 20, 12, 0));
        return snapshot;
    }

    /** 构造满足法人和 UBO 门槛的相关人员。 */
    private List<MerchantProfileChangeDTOs.RelatedPerson> requiredPersons() {
        MerchantProfileChangeDTOs.RelatedPerson person = new MerchantProfileChangeDTOs.RelatedPerson();
        person.setId(1L);
        person.setFullName("Alex Morgan");
        person.setPersonRoles(List.of("LEGAL_REPRESENTATIVE", "UBO"));
        person.setIdNumberMasked("P12*****6789");
        return List.of(person);
    }

    /** 构造尚未落库、通过草稿快照维护的相关人员。 */
    private MerchantProfileChangeDTOs.RelatedPerson newRelatedPerson(String idNumber) {
        MerchantProfileChangeDTOs.RelatedPerson person = new MerchantProfileChangeDTOs.RelatedPerson();
        person.setFullName("Taylor Morgan");
        person.setPersonRoles(List.of("LEGAL_REPRESENTATIVE", "UBO"));
        person.setDateOfBirth(LocalDate.of(1990, 5, 20));
        person.setIdType("PASSPORT");
        person.setIdNumber(idNumber);
        return person;
    }

    /** 构造提交审核所需的注册证明和人员身份证明。 */
    private List<MerchantDocumentDO> requiredDocuments() {
        MerchantDocumentDO registration = new MerchantDocumentDO();
        registration.setDocumentType("BUSINESS_LICENSE");
        registration.setDocumentStatus("ACTIVE");
        MerchantDocumentDO identity = new MerchantDocumentDO();
        identity.setDocumentType("UBO_ID");
        identity.setDocumentStatus("ACTIVE");
        return List.of(registration, identity);
    }

    /** 构造活动草稿申请。 */
    private MerchantProfileChangeRequestDO draftRequest() {
        MerchantProfileChangeRequestDO row = new MerchantProfileChangeRequestDO();
        row.setId(1L);
        row.setRequestNo("MCR202609200001");
        row.setMerchantId("200045");
        row.setStatus("DRAFT");
        row.setActiveFlag(1);
        row.setProposedSnapshotCipher("cipher-text");
        row.setChangedFieldsJson("[\"merchantName\"]");
        row.setVersion(0);
        row.setGmtCreate(LocalDateTime.of(2026, 9, 20, 12, 0));
        row.setGmtModified(LocalDateTime.of(2026, 9, 20, 12, 0));
        row.setDeleted(0);
        return row;
    }
}
