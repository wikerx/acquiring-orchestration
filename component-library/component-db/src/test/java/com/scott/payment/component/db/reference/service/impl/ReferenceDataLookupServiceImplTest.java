package com.scott.payment.component.db.reference.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import com.scott.payment.component.db.reference.entity.IpLibraryDataRow;
import com.scott.payment.component.db.reference.entity.IpLibraryShardDO;
import com.scott.payment.component.db.reference.mapper.CardBinLookupMapper;
import com.scott.payment.component.db.reference.mapper.IpLocationLookupMapper;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReferenceDataLookupServiceImplTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 公共基础数据检索服务行为测试。
 * @status : create
 */
class ReferenceDataLookupServiceImplTest {

    @Test
    void shouldLookupIpv4LocationFromReadySlaveShard() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        IpLibraryShardDO shard = readyShard("IPV4", "ip_library_v4_data_01", "20260811");
        IpLibraryDataRow row = new IpLibraryDataRow();
        row.setCountryAlpha2("US");
        row.setCountryAlpha3("USA");
        row.setCountryNumeric("840");
        row.setCountryName("United States");
        row.setStateProvince("California");
        row.setCity("Mountain View");
        when(ipMapper.selectReadyShards("IPV4", "134744072")).thenReturn(List.of(shard));
        when(ipMapper.selectLookupCandidate("ip_library_v4_data_01", "20260811", "134744072"))
                .thenReturn(row);

        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(ipMapper, cardBinMapper);

        IpLookupResult result = service.lookupIp("008.008.008.008");

        assertThat(result.matched()).isTrue();
        assertThat(result.ipAddress()).isEqualTo("8.8.8.8");
        assertThat(result.ipType()).isEqualTo("IPV4");
        assertThat(result.countryAlpha3()).isEqualTo("USA");
        assertThat(result.city()).isEqualTo("Mountain View");
    }

    @Test
    void shouldLookupBestCardBinWithinProvidedPrecision() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        CardBinRangeDO row = new CardBinRangeDO();
        row.setBinLength(6);
        row.setCardBrand("VISA");
        row.setCardSubBrand("CLASSIC");
        row.setCardType("CREDIT");
        row.setCardLevel("GOLD");
        row.setIssuerCountryName("United States");
        row.setIssuerCountryAlpha2("US");
        row.setIssuerCountryAlpha3("USA");
        row.setIssuerCountryNumeric("840");
        row.setIssuerBank("Example Bank");
        when(cardBinMapper.selectBestMatch(41111100000L, 6)).thenReturn(row);

        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(ipMapper, cardBinMapper);

        CardBinLookupResult result = service.lookupCardBin("411111");

        assertThat(result.matched()).isTrue();
        assertThat(result.cardBin()).isEqualTo("411111");
        assertThat(result.binLength()).isEqualTo(6);
        assertThat(result.cardBrand()).isEqualTo("VISA");
        assertThat(result.issuerBank()).isEqualTo("Example Bank");
        verify(cardBinMapper, never()).selectNextEffectiveTime(41111100000L, 6);
    }

    @Test
    void shouldExposeCardBinTimeBoundariesForCache() {
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(4);
        LocalDateTime nextEffectiveTime = LocalDateTime.now().plusMinutes(2);
        CardBinRangeDO row = new CardBinRangeDO();
        row.setBinLength(8);
        row.setExpireTime(expireTime);
        when(cardBinMapper.selectBestMatch(41111112000L, 8)).thenReturn(row);
        when(cardBinMapper.selectNextEffectiveTime(41111112000L, 8)).thenReturn(nextEffectiveTime);
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(
                mock(IpLocationLookupMapper.class), cardBinMapper);

        var snapshot = service.lookupCardBinSnapshot("41111112");

        assertThat(snapshot.result().matched()).isTrue();
        assertThat(snapshot.expireTime()).isEqualTo(expireTime);
        assertThat(snapshot.nextEffectiveTime()).isEqualTo(nextEffectiveTime);
    }

    @Test
    void shouldReturnStableMissResultsForValidInputs() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        IpLibraryShardDO shard = readyShard("IPV6", "ip_library_v6_data_01", "20260811");
        when(ipMapper.selectReadyShards("IPV6", "1")).thenReturn(List.of(shard));
        when(ipMapper.selectLookupCandidate("ip_library_v6_data_01", "20260811", "1")).thenReturn(null);
        when(cardBinMapper.selectBestMatch(99999900000L, 6)).thenReturn(null);
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(ipMapper, cardBinMapper);

        IpLookupResult ipResult = service.lookupIp("::1");
        CardBinLookupResult cardBinResult = service.lookupCardBin("999999");

        assertThat(ipResult.matched()).isFalse();
        assertThat(ipResult.ipAddress()).isEqualTo("0:0:0:0:0:0:0:1");
        assertThat(cardBinResult.matched()).isFalse();
        assertThat(cardBinResult.cardBin()).isEqualTo("999999");
    }

    @Test
    void shouldSkipFutureBoundaryQueryWhenBinMissIsNotCached() {
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(
                mock(IpLocationLookupMapper.class), cardBinMapper);

        var snapshot = service.lookupCardBinSnapshot("99999900000");

        assertThat(snapshot.result().matched()).isFalse();
        assertThat(snapshot.nextEffectiveTime()).isNull();
        verify(cardBinMapper).selectBestMatch(99999900000L, 11);
        verify(cardBinMapper, never()).selectNextEffectiveTime(99999900000L, 11);
    }

    @Test
    void shouldRejectInvalidIpAndCardBinBeforeDatabaseLookup() {
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(
                mock(IpLocationLookupMapper.class), mock(CardBinLookupMapper.class));

        assertThatThrownBy(() -> service.lookupIp("example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookupCardBin("41111A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookupCardBin("411111111111"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailWhenIpShardConfigurationIsMissingOrAmbiguous() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        when(ipMapper.selectReadyShards("IPV4", "134744072")).thenReturn(List.of());
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(ipMapper, cardBinMapper);

        assertThatThrownBy(() -> service.lookupIp("8.8.8.8"))
                .isInstanceOfSatisfying(ServiceException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode()));
    }

    @Test
    void shouldRejectIpShardWhosePhysicalTableDoesNotMatchIpType() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        IpLibraryShardDO mismatchedShard = readyShard("IPV4", "ip_library_v6_data_01", "20260811");
        when(ipMapper.selectReadyShards("IPV4", "134744072")).thenReturn(List.of(mismatchedShard));
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(ipMapper, cardBinMapper);

        assertThatThrownBy(() -> service.lookupIp("8.8.8.8"))
                .isInstanceOfSatisfying(ServiceException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode()));
    }

    @Test
    void shouldRejectCardBinMatchMorePreciseThanMerchantInput() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        CardBinRangeDO invalidRow = new CardBinRangeDO();
        invalidRow.setBinLength(7);
        when(cardBinMapper.selectBestMatch(41111100000L, 6)).thenReturn(invalidRow);
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(ipMapper, cardBinMapper);

        assertThatThrownBy(() -> service.lookupCardBin("411111"))
                .isInstanceOfSatisfying(ServiceException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode()));
    }

    @Test
    void shouldRouteReferenceDataLookupsToSlaveDataSource() {
        assertThat(ReferenceDataLookupServiceImpl.class.getAnnotation(DS.class)).isNull();

        assertSlaveMethod("lookupIp", String.class);
        assertSlaveMethod("lookupCardBin", String.class);
        assertSlaveMethod("lookupCardBinSnapshot", String.class);
    }

    @Test
    void shouldReadCompleteElevenDigitSnapshotFromSlave() {
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        CardBinRangeDO row = new CardBinRangeDO();
        row.setBinLength(11);
        row.setCardBrand("VISA");
        row.setCardSubBrand("SIGNATURE");
        row.setIssuerCountryNumeric("840");
        row.setIssuerBank("Example Bank");
        LocalDateTime nextEffectiveTime = LocalDateTime.now().plusMinutes(5);
        when(cardBinMapper.selectBestMatch(41111112345L, 11)).thenReturn(row);
        when(cardBinMapper.selectNextEffectiveTime(41111112345L, 11)).thenReturn(nextEffectiveTime);
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(
                mock(IpLocationLookupMapper.class), cardBinMapper);

        var snapshot = service.lookupCardBinSnapshot("41111112345");

        assertThat(snapshot.result().cardSubBrand()).isEqualTo("SIGNATURE");
        assertThat(snapshot.result().issuerCountryNumeric()).isEqualTo("840");
        assertThat(snapshot.result().issuerBank()).isEqualTo("Example Bank");
        assertThat(snapshot.nextEffectiveTime()).isEqualTo(nextEffectiveTime);
    }

    private void assertSlaveMethod(String methodName, Class<?>... parameterTypes) {
        Method method;
        try {
            method = ReferenceDataLookupServiceImpl.class.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
        DS dataSource = method.getAnnotation(DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.SLAVE);
        if (methodName.startsWith("lookupCardBin")) {
            Transactional transaction = method.getAnnotation(Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
            assertThat(transaction.readOnly()).isTrue();
        }
    }

    private IpLibraryShardDO readyShard(String ipType, String tableName, String dataVersion) {
        IpLibraryShardDO shard = new IpLibraryShardDO();
        shard.setIpType(ipType);
        shard.setTableName(tableName);
        shard.setDataVersion(dataVersion);
        shard.setActiveFlag(1);
        shard.setLoadStatus("READY");
        return shard;
    }
}
