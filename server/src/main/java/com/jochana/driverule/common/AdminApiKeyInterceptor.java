package com.jochana.driverule.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 검수 CMS API({@code /api/v1/admin/**}) 는 {@code X-Admin-Key} 헤더가 맞아야 쓴다.
 * 키가 설정되지 않았으면(ADMIN_API_KEY 비어 있음) 관리자 API 전체를 막는다.
 * 앱용 API 는 식별자 없이 쓰므로 인증하지 않는다 (결정 003 · 005).
 * <p>
 * 서블릿 필터로 요청 URI 앞부분을 비교하면 {@code /api/v1;x/admin/...} · {@code /api/v1/%61dmin/...} 처럼
 * 철자만 다른 경로가 검사를 건너뛰고 컨트롤러에 닿는다. 라우팅과 <b>같은 경로 매칭</b>을 쓰도록 인터셉터로 건다.
 */
@Configuration
public class AdminApiKeyInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	static final String HEADER = "X-Admin-Key";
	static final String PATTERN = "/api/v1/admin/**";

	private final byte[] apiKey;

	public AdminApiKeyInterceptor(@Value("${driverule.admin.api-key:}") String apiKey) {
		this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns(PATTERN);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		String given = request.getHeader(HEADER);
		if (apiKey.length > 0 && given != null
				&& MessageDigest.isEqual(apiKey, given.getBytes(StandardCharsets.UTF_8))) {
			return true;
		}
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(
				"{\"status\":401,\"title\":\"Unauthorized\",\"code\":\"ADMIN_KEY_REQUIRED\",\"errors\":[]}");
		return false;
	}

}
