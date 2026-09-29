package com.moyeorock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// @EntityScan으로 실제 엔티티 패키지만 스캔한다. 기본값(베이스 패키지 전체)을 쓰면 테스트
// 클래스 안에 선언한 @Entity(예: BaseEntityAuditingTest의 AuditingProbe)까지 프로덕션
// persistence unit에 딸려 들어가서, ddl-auto: validate가 "마이그레이션에 없는 테이블"로
// 실패한다(테스트 소스와 메인 소스가 같은 classpath에서 함께 스캔되기 때문).
@EntityScan(basePackages = "com.moyeorock.domain")
@SpringBootApplication
@ConfigurationPropertiesScan
public class MoyeorockApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoyeorockApplication.class, args);
    }

}
