package com.hermes.broker.market.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.broker.common.exception.GlobalExceptionHandler;
import com.hermes.broker.common.exception.MarketDataUnavailableException;
import com.hermes.broker.common.monitoring.OperationalEventRecorder;
import com.hermes.broker.market.application.port.in.GetMarketOverviewUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketExceptionAdviceTest {

    @Test
    void marketDataUnavailableTakesPrecedenceOverGlobalCatchAll() throws Exception {
        GetMarketOverviewUseCase useCase = mock(GetMarketOverviewUseCase.class);
        when(useCase.getOverview(any()))
                .thenThrow(new MarketDataUnavailableException(
                        "KIS domestic market overview lookup failed."));
        MarketOverviewController controller = new MarketOverviewController(useCase);
        GlobalExceptionHandler globalAdvice = new GlobalExceptionHandler(
                new ObjectMapper(),
                Clock.systemUTC(),
                mock(OperationalEventRecorder.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(globalAdvice, new MarketExceptionAdvice())
                .build();

        mockMvc.perform(get("/api/v1/broker/market/overview"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Real market data unavailable"))
                .andExpect(jsonPath("$.message")
                        .value("KIS domestic market overview lookup failed."));
    }
}
