package com.example.finance.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI financeServiceOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Finance Service API")
                .description("财务服务接口文档")
                .version("v1.0.0"));
    }
}
