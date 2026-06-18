package com.studentconnect.repository;

import com.studentconnect.entity.MentorshipConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MentorshipConnectionRepository extends JpaRepository<MentorshipConnection, Long> {
    List<MentorshipConnection> findByStudentId(Long studentId);
    List<MentorshipConnection> findByMentorId(Long mentorId);
    List<MentorshipConnection> findByMentorIdAndStatus(
        Long mentorId, MentorshipConnection.ConnectionStatus status);
    Optional<MentorshipConnection> findByStudentIdAndMentorId(Long studentId, Long mentorId);
}
