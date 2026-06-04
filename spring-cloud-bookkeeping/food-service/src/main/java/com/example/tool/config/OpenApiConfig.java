package com.example.tool.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI toolServiceOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Tool Service API")
                .description("工具服务接口文档")
                .version("v1.0.0"));
    }
}
