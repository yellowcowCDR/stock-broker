package com.hermes.broker.market.adapter.out.external;

import com.hermes.broker.common.property.OpenDartProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenDartAdapterTest {

    @Test
    void reusesCachedCorpCodeMapAcrossStockLookups() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl("https://opendart.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://opendart.test/api/corpCode.xml?crtfc_key=test-key"))
                .andRespond(withSuccess(corpCodeZip(), MediaType.APPLICATION_OCTET_STREAM));

        OpenDartProperties properties = new OpenDartProperties(
                true,
                "https://opendart.test",
                "test-key",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofHours(24),
                "0 0 0 * * *"
        );
        OpenDartAdapter adapter = new OpenDartAdapter(
                properties,
                new ConcurrentMapCacheManager("opendart_corp_code")
        );
        ReflectionTestUtils.setField(adapter, "restClient", restClientBuilder.build());

        assertThat(adapter.getCorpCode("005930")).contains("00126380");
        assertThat(adapter.getCorpCode("000660")).contains("00164779");
        server.verify();
    }

    private byte[] corpCodeZip() throws Exception {
        String xml = """
                <result>
                    <list>
                        <corp_code>00126380</corp_code>
                        <stock_code>005930</stock_code>
                    </list>
                    <list>
                        <corp_code>00164779</corp_code>
                        <stock_code>000660</stock_code>
                    </list>
                </result>
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
