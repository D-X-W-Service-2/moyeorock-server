package com.moyeorock;

import com.moyeorock.config.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class MoyeorockApplicationTests {

    @Test
    void contextLoads() {
    }

}
