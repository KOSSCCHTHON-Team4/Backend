package team4.emotionmap.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import team4.emotionmap.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}
