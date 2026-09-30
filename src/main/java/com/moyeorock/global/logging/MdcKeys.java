package com.moyeorock.global.logging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// application.yml의 logging.pattern.level(%X{requestId}, %X{userId})과 키 이름이 같아야 한다.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MdcKeys {

    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
}
