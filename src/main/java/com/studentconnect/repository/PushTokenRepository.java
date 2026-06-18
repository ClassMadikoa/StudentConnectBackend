package com.studentconnect.repository;

import com.studentconnect.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    List<PushToken> findByUserId(Long userId);
    Optional<PushToken> findByUserIdAndPushToken(Long userId, String pushToken);

    @Transactional
    void deleteByUserIdAndPushToken(Long userId, String pushToken);
}
