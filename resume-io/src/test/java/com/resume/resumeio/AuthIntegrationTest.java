package com.resume.resumeio;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class AuthIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void loginWithDemoUserShouldReturnToken() {
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"demo","password":"123456"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.token").isNotEmpty()
                .jsonPath("$.data.user.username").isEqualTo("demo");
    }

    @Test
    void loginWithWrongPasswordShouldReturn401Code() {
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"demo","password":"wrong"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(401);
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturn401() {
        webTestClient.post()
                .uri("/api/resume/upload")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void meShouldReturnMaskedSensitiveProfile() {
        String token = loginAndGetToken();

        webTestClient.get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.email").isEqualTo("d***@example.com")
                .jsonPath("$.data.profile.realName").isEqualTo("张*")
                .jsonPath("$.data.profile.phone").isEqualTo("138****5678")
                .jsonPath("$.data.profile.idCard").isEqualTo("110101********1234");
    }

    @Test
    void meFullProfileShouldReturnUnmaskedDataForOwner() {
        String token = loginAndGetToken();

        webTestClient.get()
                .uri("/api/auth/me/profile/full")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.profile.realName").isEqualTo("张三")
                .jsonPath("$.data.profile.phone").isEqualTo("13812345678");
    }

    private String loginAndGetToken() {
        String json = webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"demo","password":"123456"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        return JsonPath.read(json, "$.data.token");
    }
}
