package com.moyeorock.domain.group.repository;

import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 해체된 모임은 없는 모임으로 본다 — 모임을 찾을 때는 findById·existsById 대신 status를 받는 메서드를 쓴다.
public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByIdAndStatus(Long id, GroupStatus status);

    boolean existsByIdAndStatus(Long id, GroupStatus status);
}
