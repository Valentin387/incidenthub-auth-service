package com.incidenthub.auth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;

@TestConfiguration
public class WireMockConfig {

    @Bean(destroyMethod = "stop")
    public WireMockServer wireMockServer(@Value("${wiremock.server.port:8090}") int port) {
        WireMockServer wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig().port(port)
        );
        wireMockServer.start();
        return wireMockServer;
    }
}