package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.exception.ExternalApiNotConfiguredException;
import com.hermes.broker.common.exception.OpenDartApiException;
import com.hermes.broker.common.property.OpenDartProperties;
import com.hermes.broker.market.application.port.out.LoadCorporateDisclosurePort;
import com.hermes.broker.market.application.port.out.LoadCorporateProfilePort;
import com.hermes.broker.market.application.port.out.LoadDartCorporationPort;
import com.hermes.broker.market.application.port.out.LoadFinancialStatementPort;
import com.hermes.broker.market.domain.CorporateDisclosure;
import com.hermes.broker.market.domain.CorporateProfile;
import com.hermes.broker.market.domain.FinancialStatement;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenDartAdapter implements LoadDartCorporationPort, LoadCorporateProfilePort, LoadCorporateDisclosurePort, LoadFinancialStatementPort {

    private final OpenDartProperties properties;
    private RestClient restClient;

    @PostConstruct
    public void init() {
        if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("OpenDART API is not configured or disabled.");
            return;
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
    }

    private void checkConfigured() {
        if (restClient == null) {
            throw new ExternalApiNotConfiguredException("OpenDART API is not configured.");
        }
    }

    @Override
    public Optional<String> getCorpCode(String stockCode) {
        checkConfigured();
        Map<String, String> mapping = getCorpCodeMap();
        return Optional.ofNullable(mapping.get(stockCode));
    }

    @Cacheable(value = "opendart_corp_code", key = "'all_codes'")
    public Map<String, String> getCorpCodeMap() {
        log.info("Downloading and parsing OpenDART corpCode.xml...");
        checkConfigured();
        try {
            byte[] zipData = restClient.get()
                    .uri(builder -> builder
                            .path("/api/corpCode.xml")
                            .queryParam("crtfc_key", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(byte[].class);

            if (zipData == null) return Collections.emptyMap();

            return parseCorpCodeZip(zipData);
        } catch (Exception e) {
            log.error("Failed to download or parse corpCode.xml", e);
            throw new OpenDartApiException("Failed to fetch corpCode mapping", e);
        }
    }

    private Map<String, String> parseCorpCodeZip(byte[] zipData) throws Exception {
        Map<String, String> map = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml")) {
                    XMLInputFactory factory = XMLInputFactory.newInstance();
                    XMLStreamReader reader = factory.createXMLStreamReader(new java.io.FilterInputStream(zis) {
                        @Override
                        public void close() {}
                    });
                    
                    try {
                        String currentCorpCode = null;
                        String currentStockCode = null;
                        String textContent = null;

                        while (reader.hasNext()) {
                            int event = reader.next();
                            switch (event) {
                                case XMLEvent.CHARACTERS:
                                    textContent = reader.getText().trim();
                                    break;
                                case XMLEvent.END_ELEMENT:
                                    String qName = reader.getLocalName();
                                    if ("corp_code".equals(qName)) {
                                        currentCorpCode = textContent;
                                    } else if ("stock_code".equals(qName)) {
                                        currentStockCode = textContent;
                                    } else if ("list".equals(qName)) {
                                        if (currentStockCode != null && !currentStockCode.isBlank()) {
                                            map.put(currentStockCode, currentCorpCode);
                                        }
                                        currentCorpCode = null;
                                        currentStockCode = null;
                                    }
                                    break;
                            }
                        }
                    } finally {
                        reader.close();
                    }
                }
            }
        }
        log.info("Successfully parsed {} corpCodes from OpenDART.", map.size());
        return map;
    }

    @Override
    @Cacheable(value = "opendart_profile", key = "#corpCode")
    public Optional<CorporateProfile> loadProfile(String corpCode) {
        checkConfigured();
        try {
            Map response = restClient.get()
                    .uri(builder -> builder
                            .path("/api/company.json")
                            .queryParam("crtfc_key", properties.apiKey())
                            .queryParam("corp_code", corpCode)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"000".equals(response.get("status"))) {
                return Optional.empty();
            }

            CorporateProfile profile = new CorporateProfile(
                    (String) response.get("corp_name"),
                    (String) response.get("corp_name_eng"),
                    (String) response.get("stock_name"),
                    (String) response.get("stock_code"),
                    (String) response.get("ceo_nm"),
                    (String) response.get("corp_cls"),
                    (String) response.get("est_dt"),
                    (String) response.get("acc_mt")
            );
            return Optional.of(profile);

        } catch (Exception e) {
            log.error("Failed to load profile for corpCode: {}", corpCode, e);
            throw new OpenDartApiException("Profile load failed", e);
        }
    }

    @Override
    @Cacheable(value = "opendart_disclosure", key = "#corpCode")
    public List<CorporateDisclosure> loadRecentDisclosures(String corpCode) {
        checkConfigured();
        try {
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String threeMonthsAgo = LocalDate.now().minusMonths(3).format(DateTimeFormatter.BASIC_ISO_DATE);

            Map response = restClient.get()
                    .uri(builder -> builder
                            .path("/api/list.json")
                            .queryParam("crtfc_key", properties.apiKey())
                            .queryParam("corp_code", corpCode)
                            .queryParam("bgn_de", threeMonthsAgo)
                            .queryParam("end_de", today)
                            .queryParam("page_count", "10")
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"000".equals(response.get("status")) || !response.containsKey("list")) {
                return Collections.emptyList();
            }

            List<Map<String, String>> list = (List<Map<String, String>>) response.get("list");
            return list.stream()
                    .map(item -> new CorporateDisclosure(
                            item.get("rcept_no"),
                            item.get("corp_name"),
                            item.get("report_nm"),
                            item.get("flr_nm"),
                            LocalDate.parse(item.get("rcept_dt"), DateTimeFormatter.BASIC_ISO_DATE),
                            item.get("rm")
                    ))
                    .toList();

        } catch (Exception e) {
            log.error("Failed to load disclosures for corpCode: {}", corpCode, e);
            // 에러 시 빈 리스트 반환하여 전체 장애 전파 방지
            return Collections.emptyList();
        }
    }

    @Override
    public List<FinancialStatement> loadRecentFinancialStatements(String corpCode) {
        checkConfigured();
        try {
            String bsnsYear = String.valueOf(LocalDate.now().getYear() - 1); // 작년 기준
            
            Map response = restClient.get()
                    .uri(builder -> builder
                            .path("/api/fnlttSinglAcnt.json")
                            .queryParam("crtfc_key", properties.apiKey())
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", bsnsYear)
                            .queryParam("reprt_code", "11011") // 사업보고서
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"000".equals(response.get("status")) || !response.containsKey("list")) {
                return Collections.emptyList();
            }

            List<Map<String, String>> list = (List<Map<String, String>>) response.get("list");
            return list.stream()
                    .map(item -> new FinancialStatement(
                            item.get("bsns_year"),
                            item.get("reprt_code"),
                            item.get("account_nm"),
                            item.get("thstrm_amount"),
                            item.get("currency")
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to load financials for corpCode: {}", corpCode, e);
            return Collections.emptyList();
        }
    }
}
