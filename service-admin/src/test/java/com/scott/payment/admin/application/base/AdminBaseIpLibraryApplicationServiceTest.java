package com.scott.payment.admin.application.base;

import com.scott.payment.admin.dto.base.IpLibraryDTOs;
import com.scott.payment.admin.entity.base.IpLibraryEntities;
import com.scott.payment.admin.mapper.IpLibraryDataMapper;
import com.scott.payment.admin.mapper.IpLibrarySplitModelMapper;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseIpLibraryApplicationServiceTest
 * @date : 2026-07-05 00:34
 * @email : scott_x@163.com
 * @description : AdminBaseIpLibraryApplicationServiceTest 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class AdminBaseIpLibraryApplicationServiceTest {

    @Mock
    /**
     * split Model Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private IpLibrarySplitModelMapper splitModelMapper;
    @Mock
    /**
     * data Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private IpLibraryDataMapper dataMapper;

    /**
     * service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private AdminBaseIpLibraryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AdminBaseIpLibraryApplicationService(splitModelMapper, dataMapper);
    }

    @Test
    void shouldConvertIpv4ToUnsignedNumber() {
        assertThat(service.ipToNumber("1.0.0.0", "IPV4")).isEqualTo("16777216");
        assertThat(service.ipToNumber("255.255.255.255", "IPV4")).isEqualTo("4294967295");
    }

    @Test
    void shouldConvertIpv6ToDecimalNumber() {
        assertThat(service.ipToNumber("2001:db8::1", "IPV6")).isEqualTo("42540766411282592856903984951653826561");
    }

    @Test
    void shouldRejectMismatchedIpType() {
        assertThatThrownBy(() -> service.ipToNumber("2001:db8::1", "IPV4"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("IPv4 地址格式不正确");
    }

    @Test
    void shouldAutoDetectIpTypeWhenConvertingNumber() {
        assertThat(service.ipToNumber("1.0.0.0", null)).isEqualTo("16777216");
        assertThat(service.ipToNumber("2001:db8::1", null)).isEqualTo("42540766411282592856903984951653826561");
    }

    @Test
    void shouldLookupMatchedRangeFromRoutedShard() {
        when(splitModelMapper.selectList(any())).thenReturn(List.of(ipv4Shard()));
        when(dataMapper.selectLookupCandidate(eq("ip_library_v4_data_01"), eq("DEFAULT"), eq("16777216")))
                .thenReturn(row("16777216", "16777471"));

        IpLibraryDTOs.IpLibraryLookupRequest request = new IpLibraryDTOs.IpLibraryLookupRequest();
        request.setIpAddress("1.0.0.0");

        IpLibraryDTOs.IpLibraryRecordResponse response = service.lookup(request);

        assertThat(response).isNotNull();
        assertThat(response.getIpType()).isEqualTo("IPV4");
        assertThat(response.getIpAddressStart()).isEqualTo("1.0.0.0");
        assertThat(response.getIpAddressEnd()).isEqualTo("1.0.0.255");
    }

    @Test
    void shouldReturnNullWhenCandidateDoesNotCoverIp() {
        when(splitModelMapper.selectList(any())).thenReturn(List.of(ipv4Shard()));
        when(dataMapper.selectLookupCandidate(eq("ip_library_v4_data_01"), eq("DEFAULT"), eq("16777472")))
                .thenReturn(row("16777216", "16777471"));

        IpLibraryDTOs.IpLibraryLookupRequest request = new IpLibraryDTOs.IpLibraryLookupRequest();
        request.setIpAddress("1.0.1.0");

        assertThat(service.lookup(request)).isNull();
    }

    @Test
    void shouldPageOnlyRequestedShardWindow() {
        when(splitModelMapper.selectList(any())).thenReturn(List.of(ipv4Shard()));
        when(dataMapper.countRows(eq("ip_library_v4_data_01"), eq("DEFAULT"), eq("16777216")))
                .thenReturn(1L);
        when(dataMapper.selectPageRows(eq("ip_library_v4_data_01"), eq("DEFAULT"), eq("16777216"), eq(0L), eq(10)))
                .thenReturn(List.of(row("16777472", "16778239")));

        IpLibraryDTOs.IpLibraryQueryRequest request = new IpLibraryDTOs.IpLibraryQueryRequest();
        request.setIpAddress("1.0.0.0");

        PageResult<IpLibraryDTOs.IpLibraryRecordResponse> page = service.page(request);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getCountryAlpha2()).isEqualTo("CN");
    }

    @Test
    void shouldPageCurrentIpTypeWhenIpAddressIsBlank() {
        when(splitModelMapper.selectList(any())).thenReturn(List.of(ipv4Shard()));
        when(dataMapper.countRows(eq("ip_library_v4_data_01"), eq("DEFAULT"), eq(null)))
                .thenReturn(1L);
        when(dataMapper.selectPageRows(eq("ip_library_v4_data_01"), eq("DEFAULT"), eq(null), eq(0L), eq(10)))
                .thenReturn(List.of(row("16777472", "16778239")));

        IpLibraryDTOs.IpLibraryQueryRequest request = new IpLibraryDTOs.IpLibraryQueryRequest();
        request.setIpType("IPV4");

        PageResult<IpLibraryDTOs.IpLibraryRecordResponse> page = service.page(request);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getIpType()).isEqualTo("IPV4");
    }

    private IpLibraryEntities.IpLibrarySplitModelDO ipv4Shard() {
        IpLibraryEntities.IpLibrarySplitModelDO row = new IpLibraryEntities.IpLibrarySplitModelDO();
        row.setIpType("IPV4");
        row.setShardNo(1);
        row.setTableName("ip_library_v4_data_01");
        row.setRangeStart("0");
        row.setRangeEnd("536870911");
        row.setDataVersion("DEFAULT");
        row.setActiveFlag(1);
        return row;
    }

    private IpLibraryEntities.IpLibraryDataRow row(String start, String end) {
        IpLibraryEntities.IpLibraryDataRow row = new IpLibraryEntities.IpLibraryDataRow();
        row.setId(1L);
        row.setIpNumberStart(start);
        row.setIpNumberEnd(end);
        row.setCountryAlpha2("CN");
        row.setCountryAlpha3("CHN");
        row.setCountryNumeric("156");
        row.setCountryName("China");
        row.setStateProvince("Fujian");
        row.setCity("Fuzhou");
        row.setDataVersion("DEFAULT");
        row.setCreateBy("scott");
        return row;
    }
}
