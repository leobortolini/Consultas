package com.fiap.consultas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;

@SpringBootTest
@AutoConfigureTestDatabase
@EnableTestBinder
class ConsultasApplicationTests {

	@Test
	void contextLoads() {
		// teste vazio para verificar se o contexto do spring carrega sem problemas
	}

}
