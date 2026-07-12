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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseIpLibraryApplicationServiceTest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Admin Base Ip Library Application Service Test，位于 service-admin 的测试层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
class AdminBaseIpLibraryApplicationServiceTest {

    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Mock
    private IpLibrarySplitModelMapper splitModelMapper;
    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Mock
    private IpLibraryDataMapper dataMapper;

    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
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
