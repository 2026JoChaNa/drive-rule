package com.jochana.driverule.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 문서 기본 정보. 문서 경로·켜기/끄기는 application.yml 의 springdoc 설정을 따른다.
 */
@Configuration
public class OpenApiConfig {

	static final String ADMIN_KEY = "adminKey";

	@Bean
	OpenAPI driveRuleOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("DRIVE RULE API")
						.description("차량 앱 · 검수 CMS 가 쓰는 DRIVE RULE 백엔드 API")
						.version("v1"))
				.components(new Components().addSecuritySchemes(ADMIN_KEY, new SecurityScheme()
						.type(SecurityScheme.Type.APIKEY)
						.in(SecurityScheme.In.HEADER)
						.name("X-Admin-Key")
						.description("검수 CMS API(/api/v1/admin/**) 전용")));
	}

	/** CMS 경로에만 X-Admin-Key 요구를 표시한다 (앱용 API 는 인증 없음). */
	@Bean
	OpenApiCustomizer adminKeyRequirement() {
		return openApi -> openApi.getPaths().forEach((path, item) -> {
			if (path.startsWith("/api/v1/admin/")) {
				item.readOperations().forEach(op -> op.addSecurityItem(new SecurityRequirement().addList(ADMIN_KEY)));
			}
		});
	}

}
