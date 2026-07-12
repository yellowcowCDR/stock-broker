package com.hermes.broker;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@OpenAPIDefinition(
    info = @Info(
        title = "Hermes Trading Broker API",
        description = "자동 매매 에이전트를 위한 주식 주문 및 시세 중계 서버 API 문서입니다.",
        version = "v1.0"
    )
)
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class HermesBrokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HermesBrokerApplication.class, args);
    }
}
