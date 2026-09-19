package team4.emotionmap.platform.security;

import team4.emotionmap.contracts.signing.SignedValueCodec;

/** 다른 모듈 테스트가 실제 HMAC 코덱을 쓰기 위한 진입점(패키지 private 생성자 우회). */
public final class HmacSignedValueCodecTestSupport {

    private HmacSignedValueCodecTestSupport() {
    }

    public static SignedValueCodec codec() {
        return new HmacSignedValueCodec("unit-test-signing-secret-0123456789-abcdefghij");
    }
}
