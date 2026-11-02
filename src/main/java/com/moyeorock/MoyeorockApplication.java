package com.moyeorock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// @EntityScan으로 실제 엔티티가 있는 패키지만 스캔한다. 기본값(베이스 패키지 전체)을 쓰면 테스트
// 클래스 안에 선언한 @Entity(예: BaseEntityAuditingTest의 AuditingProbe, com.moyeorock.global.common.entity
// 패키지)까지 프로덕션 persistence unit에 딸려 들어가서, ddl-auto: validate가 "마이그레이션에 없는
// 테이블"로 실패한다(테스트 소스와 메인 소스가 같은 classpath에서 함께 스캔되기 때문). 그래서
// com.moyeorock.global 전체가 아니라 실제 엔티티가 있는 하위 패키지만 콕 집어 나열한다 —
// domain(Team 등)과 global.file.entity(UploadedFile). global.common.entity는 일부러 뺐다
// (그 안엔 @MappedSuperclass뿐이라 스캔 대상일 필요가 없고, 테스트 클래스와 패키지가 겹친다).
@EntityScan(basePackages = {"com.moyeorock.domain", "com.moyeorock.global.file.entity"})
@SpringBootApplication
@ConfigurationPropertiesScan
public class MoyeorockApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoyeorockApplication.class, args);
    }

}
