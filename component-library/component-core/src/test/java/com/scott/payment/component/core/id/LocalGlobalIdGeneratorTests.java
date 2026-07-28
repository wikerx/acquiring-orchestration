package com.scott.payment.component.core.id;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LocalGlobalIdGeneratorTests
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : Local Global ID Generator Tests 自动化测试类，位于 公共组件库，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class LocalGlobalIdGeneratorTests {

    @Test
    void nextIdShouldReturnValidGlobalId() {
        LocalGlobalIdGenerator generator = new LocalGlobalIdGenerator();

        String id = generator.nextId();

        assertThat(id).hasSize(GlobalIdConstants.ID_LENGTH);
        assertThat(id).containsOnlyDigits();
        assertThat(GlobalIdValidator.isValid(id)).isTrue();
    }

    @Test
    void nextIdShouldNotDuplicateWhenCalledContinuously() {
        LocalGlobalIdGenerator generator = new LocalGlobalIdGenerator();
        Set<String> ids = ConcurrentHashMap.newKeySet();

        for (int index = 0; index < 10_000; index++) {
            ids.add(generator.nextId());
        }

        assertThat(ids).hasSize(10_000);
    }

    @Test
    void nextIdShouldNotDuplicateWhenCalledConcurrently() throws InterruptedException {
        LocalGlobalIdGenerator generator = new LocalGlobalIdGenerator();
        int threadCount = 20;
        int perThreadCount = 5_000;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int index = 0; index < perThreadCount; index++) {
                        String id = generator.nextId();
                        assertThat(id).hasSize(GlobalIdConstants.ID_LENGTH);
                        assertThat(id).containsOnlyDigits();
                        assertThat(GlobalIdValidator.isValid(id)).isTrue();
                        ids.add(id);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        assertThat(finishLatch.await(20, TimeUnit.SECONDS)).isTrue();
        executorService.shutdownNow();
        assertThat(ids).hasSize(threadCount * perThreadCount);
    }
}
