package com.incidenthub.auth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class WireMockConfig {

    @Bean(destroyMethod = "stop")
    public WireMockServer wireMockServer() {
        // Use fixed port 8090 to match application-test.yml
        WireMockServer wireMockServer = new WireMockServer(
            WireMockConfiguration.wireMockConfig().port(8090)
        );
        wireMockServer.start();
        return wireMockServer;
    }
}