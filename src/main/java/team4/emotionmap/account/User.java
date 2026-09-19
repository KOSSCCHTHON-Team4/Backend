package team4.emotionmap.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * app_user 테이블 매핑 (V1__init.sql).
 * home_lat/home_lng 는 사용자의 홈 위치(감정지도 기준점). nullable.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private Double homeLat;

    private Double homeLng;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private User(String nickname, String email, String passwordHash,
                 Double homeLat, Double homeLng) {
        this.nickname = nickname;
        this.email = email;
        this.passwordHash = passwordHash;
        this.homeLat = homeLat;
        this.homeLng = homeLng;
        this.createdAt = OffsetDateTime.now();
    }

    /** 홈 위치 수정. */
    public void updateHomeLocation(Double homeLat, Double homeLng) {
        this.homeLat = homeLat;
        this.homeLng = homeLng;
    }
}
