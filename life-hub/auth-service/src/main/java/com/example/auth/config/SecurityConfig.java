package com.example.auth.config;

import com.example.common.security.InternalApiKeyFilter;
import com.example.common.security.JwtTokenAuthenticationFilter;
import com.example.common.security.SecurityErrorWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtTokenAuthenticationFilter jwtTokenAuthenticationFilter,
        InternalApiKeyFilter internalApiKeyFilter,
        SecurityErrorWriter securityErrorWriter
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) ->
                    securityErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    securityErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "没有访问权限"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 内部服务间接口不走 JWT，由 InternalApiKeyFilter 用 X-Internal-Key 鉴权
                // （market-service 等无库服务经此查询用户状态/家庭范围）
                .requestMatchers("/api/internal/**").permitAll()
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/password/reset",
                    "/api/auth/logout",
                    "/actuator/health",
                    "/error",
                    "/doc.html",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-resources/**",
                    "/webjars/**",
                    "/favicon.ico"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
