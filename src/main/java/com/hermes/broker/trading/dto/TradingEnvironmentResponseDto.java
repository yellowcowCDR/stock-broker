package com.hermes.broker.trading.dto;

import com.hermes.broker.common.property.AutonomyMode;
import com.hermes.broker.common.property.KisEnvironment;

public record TradingEnvironmentResponseDto(
        KisEnvironment kisEnvironment,
        String tradingMode,
        AutonomyMode autonomyMode,
        boolean realOrderEnabled,
        boolean entryKillSwitchEnabled,
        boolean overseasOrderEnabled,
        boolean overseasPaperOrderEnabled,
        boolean overseasLiveOrderEnabled
) {
}
