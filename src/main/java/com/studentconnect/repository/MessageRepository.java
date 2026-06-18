package com.studentconnect.repository;

import com.studentconnect.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
        SELECT m FROM Message m
        WHERE m.sender.id = :userId OR m.recipient.id = :userId
        ORDER BY m.sentAt DESC
        """)
    List<Message> findConversationsForUser(@Param("userId") Long userId);

    @Query("""
        SELECT m FROM Message m
        WHERE (m.sender.id = :userA AND m.recipient.id = :userB)
           OR (m.sender.id = :userB AND m.recipient.id = :userA)
        ORDER BY m.sentAt ASC
        """)
    List<Message> findChatBetween(@Param("userA") Long userA, @Param("userB") Long userB);
}
