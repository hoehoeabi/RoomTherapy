package com.roomtherapy.backend.global.util;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component("profileUtil")
@RequiredArgsConstructor
public class ProfileUtil {

    private final Environment environment;

    /**
     * 운영(prod) 환경이 아닌 로컬/개발 환경인지 확인
     */
    public boolean isDev() {
        return !environment.acceptsProfiles(Profiles.of("prod"));
    }

    /**
     * 운영(prod) 환경인지 확인
     */
    public boolean isProd() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
}
