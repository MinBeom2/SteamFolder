package com.steamfolder.steamfolder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"rate-limit.enabled=true",
		"spring.data.redis.port=1",
		"spring.data.redis.connect-timeout=100ms"
})
@AutoConfigureMockMvc
class SteamfolderApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void rejectsSensitiveRequestWhenRedisIsUnavailable() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("RL002"));
	}

	@Test
	void forwardsReadRequestWhenRedisIsUnavailable() throws Exception {
		int status = mockMvc.perform(get("/api/v1/rankings"))
				.andReturn()
				.getResponse()
				.getStatus();

		assertThat(status).isNotEqualTo(503);
	}

}
