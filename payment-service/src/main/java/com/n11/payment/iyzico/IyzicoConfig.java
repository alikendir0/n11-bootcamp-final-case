package com.n11.payment.iyzico;

import com.iyzipay.Options;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IyzicoProperties.class)
public class IyzicoConfig {

    @Bean
    Options iyzicoOptions(IyzicoProperties properties) {
        Options options = new Options();
        options.setBaseUrl(properties.baseUrl());
        options.setApiKey(properties.apiKey());
        options.setSecretKey(properties.secretKey());
        return options;
    }
}
