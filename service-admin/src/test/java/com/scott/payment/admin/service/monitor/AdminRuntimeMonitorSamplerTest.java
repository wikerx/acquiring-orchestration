package com.scott.payment.admin.service.monitor;

import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.RuntimeSample;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRuntimeMonitorSamplerTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : JVM 运行指标空值语义、有界历史和闭区间过滤测试。
 * @status : create
 */
class AdminRuntimeMonitorSamplerTest {

    @Test
    void shouldKeepUnavailableMeasurementsNullInsteadOfInventingZero() {
        assertThat(AdminRuntimeMonitorSampler.percentOrNull(-1D)).isNull();
        assertThat(AdminRuntimeMonitorSampler.percentOrNull(Double.NaN)).isNull();
        assertThat(AdminRuntimeMonitorSampler.percentOrNull(Double.POSITIVE_INFINITY)).isNull();
        assertThat(AdminRuntimeMonitorSampler.nonNegativeFiniteOrNull(-1D)).isNull();
        assertThat(AdminRuntimeMonitorSampler.nonNegativeFiniteOrNull(Double.NaN)).isNull();
        assertThat(AdminRuntimeMonitorSampler.nonNegativeOrNull(-1L)).isNull();

        assertThat(AdminRuntimeMonitorSampler.percentOrNull(0D)).isZero();
        assertThat(AdminRuntimeMonitorSampler.percentOrNull(0.2534D)).isEqualTo(25.34D);
        assertThat(AdminRuntimeMonitorSampler.nonNegativeFiniteOrNull(0D)).isZero();
        assertThat(AdminRuntimeMonitorSampler.nonNegativeOrNull(0L)).isZero();
    }

    @Test
    void shouldKeepOnlySevenDaysOfMinuteSamplesAndFilterInclusiveRange() {
        AdminRuntimeMonitorSampler sampler = new AdminRuntimeMonitorSampler();
        LocalDateTime base = LocalDateTime.of(2026, 9, 1, 0, 0);
        for (int index = 0; index < AdminRuntimeMonitorSampler.MAX_SAMPLES + 2; index++) {
            RuntimeSample sample = new RuntimeSample();
            sample.setTimestamp(base.plusMinutes(index));
            sampler.addSample(sample);
        }

        assertThat(sampler.between(base.minusDays(1), base.plusDays(30)))
                .hasSize(AdminRuntimeMonitorSampler.MAX_SAMPLES)
                .first()
                .extracting(RuntimeSample::getTimestamp)
                .isEqualTo(base.plusMinutes(2));
        assertThat(sampler.between(base.plusMinutes(10), base.plusMinutes(12)))
                .extracting(RuntimeSample::getTimestamp)
                .containsExactly(base.plusMinutes(10), base.plusMinutes(11), base.plusMinutes(12));
    }
}
