package com.jochana.driverule.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 문서 기본 정보. 문서 경로·켜기/끄기는 application.yml 의 springdoc 설정을 따른다.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI driveRuleOpenApi() {
		return new OpenAPI().info(new Info()
				.title("DRIVE RULE API")
				.description("차량 앱 · 검수 CMS 가 쓰는 DRIVE RULE 백엔드 API")
				.version("v0"));
	}

}
