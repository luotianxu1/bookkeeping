package com.example.common.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * prod 环境守卫：服务间 internal api-key 必须显式配置且不得为开发默认值，
 * 否则启动失败（fail-fast），防止线上带着可猜测的默认 key 运行。
 */
@Configuration
@Profile("prod")
public class ProdInternalKeyGuard implements InitializingBean {

    private static final String DEV_DEFAULT_KEY = "dev-internal-key";

    @Value("${internal.api-key:}")
    private String apiKey;

    @Override
    public void afterPropertiesSet() {
        if (apiKey == null || apiKey.isBlank() || DEV_DEFAULT_KEY.equals(apiKey)) {
            throw new IllegalStateException(
                "prod 环境必须通过环境变量 INTERNAL_API_KEY 配置服务间 internal api-key，且不得使用开发默认值");
        }
    }
}
