package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.admin.dto.merchant.AdminMerchantProfileChangeDTOs;
import com.scott.payment.admin.dto.merchant.MerchantOnboardingDTOs;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.BizDocumentDO;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.MerchantRelatedPersonDO;
import com.scott.payment.admin.mapper.BizDocumentMapper;
import com.scott.payment.admin.mapper.MerchantRelatedPersonMapper;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.merchant.entity.MerchantProfileChangeRequestDO;
import com.scott.payment.component.db.merchant.entity.MerchantProfileReviewRecordDO;
import com.scott.payment.component.db.merchant.mapper.MerchantProfileChangeRequestMapper;
import com.scott.payment.component.db.merchant.mapper.MerchantProfileReviewRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantProfileChangeServiceTest
 * @date : 2026-09-21 16:30
 * @email : scott_x@163.com
 * @description : 管理端商户资料变更审核服务测试，验证审批应用、并发保护和审核意见门禁
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
class AdminMerchantProfileChangeServiceTest {

    @Mock
    private MerchantProfileChangeRequestMapper requestMapper;
    @Mock
    private MerchantProfileReviewRecordMapper reviewRecordMapper;
    @Mock
    private BaseMerchantInfoMapper merchantInfoMapper;
    @Mock
    private MerchantRelatedPersonMapper relatedPersonMapper;
    @Mock
    private BizDocumentMapper documentMapper;
    @Mock
    private MerchantOnboardingService onboardingService;
    @Mock
    private MerchantProfileSensitiveCrypto sensitiveCrypto;
    @Mock
    private ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

    private ObjectMapper objectMapper;
    private AdminMerchantProfileChangeService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, MerchantProfileChangeRequestDO.class);
        TableInfoHelper.initTableInfo(assistant, BaseMerchantInfoDO.class);
        TableInfoHelper.initTableInfo(assistant, MerchantRelatedPersonDO.class);
        TableInfoHelper.initTableInfo(assistant, BizDocumentDO.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AdminMerchantProfileChangeService(
                requestMapper,
                reviewRecordMapper,
                merchantInfoMapper,
                relatedPersonMapper,
                documentMapper,
                onboardingService,
                sensitiveCrypto,
                cacheInvalidationCoordinator,
                objectMapper
        );
    }

    /** 审核通过必须先完成申请状态 CAS，再原子应用商户主档、人员和资料。 */
    @Test
    void shouldApplySnapshotAfterApprovalStateCasSucceeds() throws Exception {
        MerchantProfileChangeRequestDO request = pendingRequest();
        BaseMerchantInfoDO merchant = merchant("Current Legal Name");
        stubRequestAndSnapshots(request, snapshot("Current Legal Name"), snapshot("Approved Legal Name"));
        when(merchantInfoMapper.selectOne(any())).thenReturn(merchant);
        when(relatedPersonMapper.selectList(any())).thenReturn(List.of());
        when(documentMapper.selectList(any())).thenReturn(List.of());
        when(requestMapper.update(any(), any())).thenReturn(1);
        when(merchantInfoMapper.update(any(), any())).thenReturn(1);

        AdminMerchantProfileChangeDTOs.ChangeRequest result = service.review(
                request.getRequestNo(), review("PASS", "Verified"));

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        InOrder writeOrder = inOrder(requestMapper, merchantInfoMapper);
        writeOrder.verify(requestMapper).update(any(), any());
        writeOrder.verify(merchantInfoMapper).update(any(), any());
        verify(cacheInvalidationCoordinator)
                .prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, request.getMerchantId());
        verify(onboardingService).replaceRelatedPersons(eq(request.getMerchantId()), any(), any());
        verify(documentMapper).update(any(), any());
        verify(reviewRecordMapper).insert(any(MerchantProfileReviewRecordDO.class));
    }

    /** 提交后被管理端修改的字段不得被旧申请覆盖。 */
    @Test
    void shouldRejectApprovalWhenChangedFieldIsStale() throws Exception {
        MerchantProfileChangeRequestDO request = pendingRequest();
        stubRequestAndSnapshots(request, snapshot("Original Legal Name"), snapshot("Proposed Legal Name"));
        when(merchantInfoMapper.selectOne(any())).thenReturn(merchant("Admin Updated Name"));
        when(relatedPersonMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.review(request.getRequestNo(), review("PASS", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("merchant profile field changed after submission: merchantName");

        verify(requestMapper, never()).update(any(), any());
        verify(merchantInfoMapper, never()).update(any(), any());
        verify(cacheInvalidationCoordinator, never()).prepare(any(), any());
        verify(reviewRecordMapper, never()).insert(any(MerchantProfileReviewRecordDO.class));
    }

    /** 补件和驳回必须提供可回传给商户的审核意见。 */
    @Test
    void shouldRequireCommentForSupplementAndRejection() {
        when(requestMapper.selectOne(any())).thenReturn(pendingRequest());

        assertThatThrownBy(() -> service.review("MCR202609210001", review("SUPPLEMENT", " ")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("review comment is required");
        assertThatThrownBy(() -> service.review("MCR202609210001", review("REJECT", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("review comment is required");

        verify(requestMapper, never()).update(any(), any());
        verify(merchantInfoMapper, never()).update(any(), any());
        verify(reviewRecordMapper, never()).insert(any(MerchantProfileReviewRecordDO.class));
    }

    /** 状态 CAS 失败时必须在触碰正式商户资料前终止审批。 */
    @Test
    void shouldNotApplySnapshotWhenApprovalStateCasFails() throws Exception {
        MerchantProfileChangeRequestDO request = pendingRequest();
        stubRequestAndSnapshots(request, snapshot("Current Legal Name"), snapshot("Proposed Legal Name"));
        when(merchantInfoMapper.selectOne(any())).thenReturn(merchant("Current Legal Name"));
        when(relatedPersonMapper.selectList(any())).thenReturn(List.of());
        when(requestMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.review(request.getRequestNo(), review("PASS", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("status has changed");

        verify(merchantInfoMapper, never()).update(any(), any());
        verify(cacheInvalidationCoordinator, never()).prepare(any(), any());
        verify(onboardingService, never()).replaceRelatedPersons(any(), any(), any());
        verify(documentMapper, never()).update(any(), any());
        verify(reviewRecordMapper, never()).insert(any(MerchantProfileReviewRecordDO.class));
    }

    /** 管理端详情必须只展示新证件号的脱敏值，不得返回明文。 */
    @Test
    void shouldMaskNewPersonIdNumberInDetail() throws Exception {
        MerchantProfileChangeRequestDO request = pendingRequest();
        AdminMerchantProfileChangeDTOs.ProfileSnapshot current = snapshot("Current Legal Name");
        AdminMerchantProfileChangeDTOs.ProfileSnapshot proposed = snapshot("Proposed Legal Name");
        MerchantOnboardingDTOs.RelatedPerson person = new MerchantOnboardingDTOs.RelatedPerson();
        person.setFullName("Taylor Morgan");
        person.setIdNumber("P123456789");
        proposed.setRelatedPersons(List.of(person));
        stubRequestAndSnapshots(request, current, proposed);
        when(merchantInfoMapper.selectOne(any())).thenReturn(merchant("Current Legal Name"));
        when(documentMapper.selectList(any())).thenReturn(List.of());
        when(sensitiveCrypto.maskIdNumber("P123456789")).thenReturn("P12***6789");

        AdminMerchantProfileChangeDTOs.ChangeRequest result = service.get(request.getRequestNo());

        MerchantOnboardingDTOs.RelatedPerson displayed = result.getProposedProfile()
                .getRelatedPersons().get(0);
        assertThat(displayed.getIdNumber()).isNull();
        assertThat(displayed.getIdNumberMasked()).isEqualTo("P12***6789");
    }

    private void stubRequestAndSnapshots(
            MerchantProfileChangeRequestDO request,
            AdminMerchantProfileChangeDTOs.ProfileSnapshot current,
            AdminMerchantProfileChangeDTOs.ProfileSnapshot proposed) throws Exception {
        when(requestMapper.selectOne(any())).thenReturn(request);
        when(sensitiveCrypto.decryptChangeSnapshot(request.getRequestNo(), request.getCurrentSnapshotCipher()))
                .thenReturn(objectMapper.writeValueAsString(current));
        when(sensitiveCrypto.decryptChangeSnapshot(request.getRequestNo(), request.getProposedSnapshotCipher()))
                .thenReturn(objectMapper.writeValueAsString(proposed));
    }

    private AdminMerchantProfileChangeDTOs.ReviewRequest review(String decision, String comment) {
        AdminMerchantProfileChangeDTOs.ReviewRequest request = new AdminMerchantProfileChangeDTOs.ReviewRequest();
        request.setDecision(decision);
        request.setComment(comment);
        return request;
    }

    private AdminMerchantProfileChangeDTOs.ProfileSnapshot snapshot(String merchantName) {
        AdminMerchantProfileChangeDTOs.ProfileSnapshot snapshot =
                new AdminMerchantProfileChangeDTOs.ProfileSnapshot();
        snapshot.setMerchantName(merchantName);
        snapshot.setRelatedPersons(List.of());
        return snapshot;
    }

    private MerchantProfileChangeRequestDO pendingRequest() {
        MerchantProfileChangeRequestDO request = new MerchantProfileChangeRequestDO();
        request.setId(1L);
        request.setRequestNo("MCR202609210001");
        request.setMerchantId("200045");
        request.setStatus("PENDING_REVIEW");
        request.setActiveFlag(1);
        request.setCurrentSnapshotCipher("current-cipher");
        request.setProposedSnapshotCipher("proposed-cipher");
        request.setChangedFieldsJson("[\"merchantName\"]");
        request.setVersion(2);
        request.setSubmittedAt(LocalDateTime.of(2026, 9, 21, 12, 0));
        request.setGmtCreate(LocalDateTime.of(2026, 9, 21, 11, 0));
        request.setGmtModified(LocalDateTime.of(2026, 9, 21, 12, 0));
        request.setDeleted(0);
        return request;
    }

    private BaseMerchantInfoDO merchant(String merchantName) {
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setId(10L);
        merchant.setMerchantId("200045");
        merchant.setMerchantName(merchantName);
        merchant.setDeleted(0);
        return merchant;
    }
}
