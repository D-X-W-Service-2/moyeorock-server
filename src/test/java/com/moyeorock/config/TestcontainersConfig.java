package com.moyeorock.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * DB가 필요한 테스트에서 {@code @Import(TestcontainersConfig.class)}로 가져다 쓴다.
 * {@code @ServiceConnection}이 datasource를 자동 주입하므로 test application.yml에 datasource 설정이 없다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
    }
}
