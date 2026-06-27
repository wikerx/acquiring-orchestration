package com.scott.payment.admin.application.base;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.base.MccRequests;
import com.scott.payment.admin.dto.base.MccVO;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.base.MccEntities;
import com.scott.payment.admin.mapper.BaseMccCodeMapper;
import com.scott.payment.admin.mapper.BaseMccLevel1Mapper;
import com.scott.payment.admin.mapper.BaseMccLevel2Mapper;
import com.scott.payment.admin.mapper.BaseMccRiskPolicyMapper;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * MCC 管理后台应用服务测试。
 *
 * <p>重点覆盖卡品牌 ALL 禁入、所有卡品牌展开、范围字段校验和分类删除保护，避免风险策略基础数据被错误写入。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminBaseMccApplicationServiceTest {

    @Mock
    private BaseMccLevel1Mapper level1Mapper;
    @Mock
    private BaseMccLevel2Mapper level2Mapper;
    @Mock
    private BaseMccCodeMapper codeMapper;
    @Mock
    private BaseMccRiskPolicyMapper riskPolicyMapper;
    @Mock
    private SysDictDataMapper dictDataMapper;
    @Mock
    private AdminDictService adminDictService;
    @Mock
    private IsoCountryMapper isoCountryMapper;
    @Mock
    private BaseMerchantInfoMapper merchantInfoMapper;
    @Mock
    private ExcelExportService excelExportService;
    @Mock
    private ExcelI18nMessageResolver excelI18nMessageResolver;
    @Mock
    private ExcelLocaleResolver excelLocaleResolver;

    private AdminBaseMccApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AdminBaseMccApplicationService(
                level1Mapper,
                level2Mapper,
                codeMapper,
                riskPolicyMapper,
                dictDataMapper,
                adminDictService,
                isoCountryMapper,
                merchantInfoMapper,
                excelExportService,
                excelI18nMessageResolver,
                excelLocaleResolver
        );
    }

    @Test
    void shouldBuildTreeWithoutLevel3() {
        when(level1Mapper.selectList(any())).thenReturn(List.of(level1()));
        when(level2Mapper.selectList(any())).thenReturn(List.of(level2()));
        when(codeMapper.selectList(any())).thenReturn(List.of(mccCode()));

        List<MccVO.MccTreeNodeVO> tree = service.tree(new MccRequests.MccTreeQueryRequest());

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getNodeType()).isEqualTo("LEVEL1");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        MccVO.MccTreeNodeVO level2 = tree.get(0).getChildren().get(0);
        assertThat(level2.getNodeType()).isEqualTo("LEVEL2");
        assertThat(level2.getChildren()).hasSize(1);
        assertThat(level2.getChildren().get(0).getNodeType()).isEqualTo("MCC_CODE");
        assertThat(level2.getChildren().get(0).getLevel()).isEqualTo(3);
    }

    @Test
    void shouldRejectAllAsCardScheme() {
        when(dictDataMapper.selectList(any())).thenReturn(cardSchemeRows());

        MccRequests.MccRiskPolicySaveRequest request = basePolicyRequest();
        request.setCardSchemes(List.of("ALL"));

        assertThatThrownBy(() -> service.createPolicies(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("card_scheme 不允许使用 ALL");
    }

    @Test
    void shouldRequireChannelCodeWhenChannelScopeSpecific() {
        when(dictDataMapper.selectList(any())).thenReturn(cardSchemeRows());
        when(codeMapper.selectCount(any())).thenReturn(1L);

        MccRequests.MccRiskPolicySaveRequest request = basePolicyRequest();
        request.setChannelScope("SPECIFIC");
        request.setChannelCode("");

        assertThatThrownBy(() -> service.createPolicies(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("channel_code 必填");
    }

    @Test
    void shouldExpandAllCardSchemesIntoRealPolicyRows() {
        when(dictDataMapper.selectList(any())).thenReturn(cardSchemeRows());
        when(codeMapper.selectCount(any())).thenReturn(1L);
        when(riskPolicyMapper.selectCount(any())).thenReturn(0L);
        when(codeMapper.selectOne(any())).thenReturn(mccCode());
        when(adminDictService.listDictData(any())).thenReturn(List.of(dictDataDTO("Visa / 维萨卡")));
        doAnswer(invocation -> {
            MccEntities.BaseMccRiskPolicyDO row = invocation.getArgument(0);
            row.setId(row.getCardScheme().equals("VISA") ? 1L : 2L);
            return 1;
        }).when(riskPolicyMapper).insert(any(MccEntities.BaseMccRiskPolicyDO.class));

        MccRequests.MccRiskPolicySaveRequest request = basePolicyRequest();
        request.setCardSchemes(null);
        request.setSelectAllCardSchemes(true);

        List<MccVO.MccRiskPolicyVO> rows = service.createPolicies(request);

        assertThat(rows).extracting(MccVO.MccRiskPolicyVO::getCardScheme)
                .containsExactlyInAnyOrder("VISA", "MASTERCARD");
    }

    @Test
    void shouldRejectDeletingLevel1WhenLevel2Exists() {
        when(level2Mapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        MccRequests.MccDeleteRequest request = new MccRequests.MccDeleteRequest();
        request.setNodeType("LEVEL1");
        request.setId(1001L);

        assertThatThrownBy(() -> service.deleteCategory(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存在二级分类");
    }

    private MccRequests.MccRiskPolicySaveRequest basePolicyRequest() {
        MccRequests.MccRiskPolicySaveRequest request = new MccRequests.MccRiskPolicySaveRequest();
        request.setMccCode("5411");
        request.setCardSchemes(List.of("VISA"));
        request.setChannelScope("ALL");
        request.setChannelCode("");
        request.setCountryScope("ALL");
        request.setCountryCode("");
        request.setRiskLevel("LOW");
        request.setAllowOnboarding(1);
        request.setAllowAcquiring(1);
        request.setRequireEnhancedReview(0);
        request.setStatus(1);
        return request;
    }

    private List<SysDictDataDO> cardSchemeRows() {
        return List.of(dictData("VISA", "Visa / 维萨卡"), dictData("MASTERCARD", "Mastercard / 万事达卡"));
    }

    private SysDictDataDO dictData(String value, String label) {
        SysDictDataDO row = new SysDictDataDO();
        row.setId((long) value.hashCode());
        row.setDictValue(value);
        row.setDictLabel(label);
        return row;
    }

    private SysDictDataDTO dictDataDTO(String label) {
        SysDictDataDTO dto = new SysDictDataDTO();
        dto.setDictLabel(label);
        return dto;
    }

    private MccEntities.BaseMccCodeDO mccCode() {
        MccEntities.BaseMccCodeDO row = new MccEntities.BaseMccCodeDO();
        row.setId(3001L);
        row.setLevel1Id(1001L);
        row.setLevel2Id(2001L);
        row.setMccCode("5411");
        row.setNameCn("杂货店、超市");
        row.setNameEn("Grocery Stores, Supermarkets");
        row.setMccType("COMMON");
        row.setRiskLevel("LOW");
        row.setStatus(1);
        row.setSortNo(10);
        row.setDeleted(0L);
        return row;
    }

    private MccEntities.BaseMccLevel1DO level1() {
        MccEntities.BaseMccLevel1DO row = new MccEntities.BaseMccLevel1DO();
        row.setId(1001L);
        row.setLevel1Code("RETAIL");
        row.setNameCn("零售");
        row.setNameEn("Retail");
        row.setStatus(1);
        row.setSortNo(10);
        row.setDeleted(0L);
        return row;
    }

    private MccEntities.BaseMccLevel2DO level2() {
        MccEntities.BaseMccLevel2DO row = new MccEntities.BaseMccLevel2DO();
        row.setId(2001L);
        row.setLevel1Id(1001L);
        row.setLevel2Code("GROCERY");
        row.setNameCn("杂货");
        row.setNameEn("Grocery");
        row.setStatus(1);
        row.setSortNo(10);
        row.setDeleted(0L);
        return row;
    }
}
