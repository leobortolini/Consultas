package com.fiap.consultas.infraestructure.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerIntegrationTest.TestControllerConfig.class)
@EnableTestBinder
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenRuntimeExceptionIsThrown_thenReturnInternalServerError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/test/runtime-exception")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Erro ao processar a requisição: Erro de runtime simulado"));
    }

    @Test
    void whenIllegalArgumentExceptionIsThrown_thenReturnBadRequest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/test/illegal-argument")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Requisição inválida: Argumento inválido simulado"));
    }

    @TestConfiguration
    static class TestControllerConfig {
        @Bean
        public TestController testController() {
            return new TestController();
        }

        @Bean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/runtime-exception")
        public String throwRuntimeException() {
            throw new RuntimeException("Erro de runtime simulado");
        }

        @GetMapping("/illegal-argument")
        public String throwIllegalArgumentException() {
            throw new IllegalArgumentException("Argumento inválido simulado");
        }
    }
}