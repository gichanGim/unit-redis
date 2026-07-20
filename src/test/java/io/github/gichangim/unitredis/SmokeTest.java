package io.github.gichangim.unitredis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 빌드/테스트 파이프라인이 동작하는지 확인하는 스모크 테스트. Phase 1 시작 시 교체한다. */
class SmokeTest {

    @Test
    void gradleTestPipelineWorks() {
        assertTrue(Runtime.version().feature() >= 21, "Java 21+ required");
    }
}
