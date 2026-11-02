package com.moyeorock.domain.group.enums;

// MANAGER는 값만 있고 권한은 없다 — 지금은 MEMBER와 동일하게 취급한다(erd.md §5).
// 임원진 전용 동작이 필요해지는 시점에 권한을 붙인다.
public enum GroupRole {
    OWNER, MANAGER, MEMBER
}
