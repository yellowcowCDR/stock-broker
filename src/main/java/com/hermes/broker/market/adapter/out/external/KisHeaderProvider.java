package com.hermes.broker.market.adapter.out.external;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class KisHeaderProvider {

    private final KisTokenManager kisTokenManager;

    @Value("${kis.api.app-key}")
    private String appKey;

    @Value("${kis.api.app-secret}")
    private String appSecret;

    /**
     * KIS API 호출 시 필요한 공통 헤더를 생성하는 Consumer 를 반환합니다.
     * Spring RestClient 의 .headers() 메서드에 직접 전달할 수 있습니다.
     *
     * @param trId API 별 고유 거래 ID (Transaction ID)
     * @return HttpHeaders Consumer
     */
    public Consumer<HttpHeaders> createCommonHeaders(String trId) {
        return headers -> {
            headers.set("authorization", "Bearer " + kisTokenManager.getToken());
            headers.set("appkey", appKey);
            headers.set("appsecret", appSecret);
            headers.set("tr_id", trId);
            // 일반 개인 고객의 경우 "P", 법인의 경우 "B"
            headers.set("custtype", "P");
        };
    }
}
