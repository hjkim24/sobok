package com.chihuahua.sobok.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.sql.Date;

@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {
    private final RefreshTokenRepository refreshTokenRepository;

    // 매일 자정에 실행
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        Date now = Date.valueOf(LocalDate.now());
        refreshTokenRepository.deleteAllByExpiredAtBefore(now);
    }
}
