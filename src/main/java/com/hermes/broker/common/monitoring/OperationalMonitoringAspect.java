package com.hermes.broker.common.monitoring;

import com.hermes.broker.market.domain.MarketOverview;
import com.hermes.broker.market.domain.MarketWatchlistResult;
import com.hermes.broker.market.domain.StockNewsResult;
import com.hermes.broker.market.domain.UsFundamentalsSnapshot;
import com.hermes.broker.market.domain.NewsSearchSnapshot;
import com.hermes.broker.market.dto.response.NewsResponseDto;
import com.hermes.broker.trading.domain.risk.RiskDecision;
import com.hermes.broker.trading.domain.risk.RiskEvaluationResult;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationalMonitoringAspect {

    private final OperationalEventRecorder recorder;

    @Around("execution(public * com.hermes.broker.market.adapter.out.external.*.*(..))")
    public Object monitorExternalProvider(ProceedingJoinPoint joinPoint) throws Throwable {
        String targetClass = joinPoint.getTarget().getClass().getSimpleName();
        if (!targetClass.endsWith("Adapter") && !targetClass.equals("KisTokenManager")) {
            return joinPoint.proceed();
        }
        if ("supports".equals(joinPoint.getSignature().getName())) {
            return joinPoint.proceed();
        }
        String provider = provider(targetClass);
        try {
            Object result = joinPoint.proceed();
            recorder.recordExternalSuccess(provider);
            observeSnapshot(result);
            return result;
        } catch (Throwable failure) {
            recorder.recordExternalFailure(provider, failure);
            throw failure;
        }
    }

    @Around("execution(public * com.hermes.broker..adapter.out.persistence.*.save*(..)) || "
            + "execution(public * com.hermes.broker..adapter.out.persistence.*.delete*(..))")
    public Object monitorDatabaseMutation(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable failure) {
            recorder.recordDatabaseFailure(joinPoint.getSignature().toShortString(), failure);
            throw failure;
        }
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object monitorScheduledExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String scheduler = joinPoint.getSignature().toShortString();
        try {
            Object result = joinPoint.proceed();
            recorder.recordScheduledExecution(scheduler, true, null);
            return result;
        } catch (Throwable failure) {
            recorder.recordScheduledExecution(scheduler, false, failure.getMessage());
            throw failure;
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.hermes.broker.trading.application.service.RiskEvaluationService.evaluate(..))",
            returning = "result")
    public void monitorDailyLossLimit(RiskEvaluationResult result) {
        if (result != null && result.decision() == RiskDecision.BLOCKED_BY_DAILY_LOSS) {
            recorder.recordDailyLossLimit(String.join(", ", result.reasons()));
        }
    }

    @AfterReturning(
            pointcut = "execution(public * com.hermes.broker.market.application.service.MarketNewsService.*(..)) || "
                    + "execution(public * com.hermes.broker.market.application.service.MarketWatchlistService.*(..)) || "
                    + "execution(public * com.hermes.broker.market.application.service.MarketOverviewService.*(..)) || "
                    + "execution(public * com.hermes.broker.market.application.service.UsFundamentalsService.*(..))",
            returning = "result")
    public void monitorApplicationSnapshot(Object result) {
        observeSnapshot(result);
    }

    private void observeSnapshot(Object result) {
        if (result instanceof StockNewsResult news) {
            recorder.recordDataSnapshot("news", news.fetchedAt(), news.complete(), news.freshness());
        } else if (result instanceof NewsResponseDto news && news.result() != null) {
            recorder.recordDataSnapshot("news", news.result().fetchedAt(), news.result().complete(),
                    news.result().freshness());
        } else if (result instanceof NewsSearchSnapshot news) {
            recorder.recordDataSnapshot("news", news.fetchedAt(), news.complete());
        } else if (result instanceof MarketWatchlistResult watchlist) {
            recorder.recordDataSnapshot("watchlist", watchlist.fetchedAt(), watchlist.complete(),
                    watchlist.freshness());
        } else if (result instanceof MarketOverview overview) {
            recorder.recordDataSnapshot("overview", overview.fetchedAt(), overview.complete(),
                    overview.freshness());
        } else if (result instanceof UsFundamentalsSnapshot fundamentals) {
            recorder.recordDataSnapshot("us-fundamentals", fundamentals.fetchedAt(), fundamentals.complete());
        }
    }

    private String provider(String className) {
        String normalized = className.toLowerCase();
        if (normalized.contains("naver")) return "naver";
        if (normalized.contains("opendart")) return "opendart";
        if (normalized.contains("alphavantage")) return "alpha-vantage";
        if (normalized.contains("kis")) return "kis";
        return "other";
    }
}
