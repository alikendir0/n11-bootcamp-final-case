package com.n11.error;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ProblemDetailControllerAdviceTest.TestApp.class)
@AutoConfigureMockMvc
class ProblemDetailControllerAdviceTest {

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void setMdc() {
        MDC.put(ProblemDetailControllerAdvice.CORRELATION_ID_MDC_KEY,
                "11111111-1111-1111-1111-111111111111");
    }

    @AfterEach
    void clearMdc() {
        MDC.remove(ProblemDetailControllerAdvice.CORRELATION_ID_MDC_KEY);
    }

    @Test
    void responseStatusException_400_producesLockedProblemJsonShape() throws Exception {
        mvc.perform(get("/test/throw-400"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("https://n11clone/errors/validation"))
            .andExpect(jsonPath("$.title").value(equalTo("Validation failed")))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value(equalTo("nope")))
            .andExpect(jsonPath("$.instance").value(equalTo("/test/throw-400")))
            .andExpect(jsonPath("$.correlationId")
                .value(equalTo("11111111-1111-1111-1111-111111111111")));
    }

    // ---- Test slice scaffolding ----

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean ThrowingController throwingController() { return new ThrowingController(); }
        @Bean ProblemDetailControllerAdvice problemDetailControllerAdvice() {
            return new ProblemDetailControllerAdvice();
        }
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/throw-400")
        public String throw400() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nope");
        }
    }
}
