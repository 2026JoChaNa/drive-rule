package com.jochana.driverule.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 키 비교만 본다. 어떤 경로에 걸리는지는 CardAdminControllerWebTest 가 실제 라우팅으로 확인한다. */
class AdminApiKeyInterceptorTest {

	@Test
	void needsMatchingKey() throws Exception {
		AdminApiKeyInterceptor interceptor = new AdminApiKeyInterceptor("secret");

		assertThat(run(interceptor, null).getStatus()).isEqualTo(401);
		assertThat(run(interceptor, "wrong").getStatus()).isEqualTo(401);
		assertThat(run(interceptor, "secret").getStatus()).isEqualTo(200);
	}

	@Test
	void blankKeyDisablesAdminApi() throws Exception {
		assertThat(run(new AdminApiKeyInterceptor(""), "").getStatus()).isEqualTo(401);
	}

	private static MockHttpServletResponse run(AdminApiKeyInterceptor interceptor, String key) throws Exception {
		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/cards");
		if (key != null) {
			req.addHeader(AdminApiKeyInterceptor.HEADER, key);
		}
		MockHttpServletResponse res = new MockHttpServletResponse();
		interceptor.preHandle(req, res, new Object());
		return res;
	}

}
