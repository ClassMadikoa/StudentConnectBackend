package com.studentconnect.repository;

import com.studentconnect.entity.JobListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JobListingRepository extends JpaRepository<JobListing, Long> {

    List<JobListing> findByActiveTrue();

    List<JobListing> findByEmployerIdAndActiveTrue(Long employerId);

    /**
     * Native JSONB @> containment query — finds jobs whose required_skills
     * overlap with the student's skills JSON array string.
     */
    @Query(value = """
        SELECT * FROM job_listings
        WHERE active = true
          AND required_skills @> CAST(:skills AS jsonb)
        ORDER BY posted_at DESC
        """, nativeQuery = true)
    List<JobListing> findMatchingJobsForStudent(@Param("skills") String skillsJson);
}
