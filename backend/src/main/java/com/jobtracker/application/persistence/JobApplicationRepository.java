package com.jobtracker.application.persistence;

import com.jobtracker.application.domain.JobApplication;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long>,
        JpaSpecificationExecutor<JobApplication> {
    @Query(value = """
            SELECT a.id AS id, a.company AS company, a.job_title AS "jobTitle", a.job_url AS "jobUrl",
                   a.date_applied AS "dateApplied", a.status AS status, a.notes AS notes,
                   a.created_at AS "createdAt", a.updated_at AS "updatedAt",
                   (SELECT h.source FROM application_status_history h
                    WHERE h.application_id = a.id
                    ORDER BY h.changed_at DESC, h.id DESC LIMIT 1) AS "lastStatusSource",
                   (SELECT e.subject FROM email_message e
                    WHERE e.application_id = a.id
                    ORDER BY e.received_at DESC, e.id DESC LIMIT 1) AS "lastEmailSubject",
                   (SELECT e.received_at FROM email_message e
                    WHERE e.application_id = a.id
                    ORDER BY e.received_at DESC, e.id DESC LIMIT 1) AS "lastEmailAt"
            FROM job_application a
            WHERE (CAST(:status AS varchar) IS NULL OR a.status = CAST(:status AS varchar))
              AND (CAST(:search AS varchar) IS NULL
                   OR lower(a.company) LIKE CONCAT('%', :search, '%') ESCAPE '!'
                   OR lower(a.job_title) LIKE CONCAT('%', :search, '%') ESCAPE '!')
            ORDER BY a.created_at DESC, a.id DESC
            """, nativeQuery = true)
    List<JobApplicationRow> findApplicationRows(
            @Param("status") String status, @Param("search") String escapedSearch);

    @Query(value = """
            SELECT a.id AS id, a.company AS company, a.job_title AS "jobTitle", a.job_url AS "jobUrl",
                   a.date_applied AS "dateApplied", a.status AS status, a.notes AS notes,
                   a.created_at AS "createdAt", a.updated_at AS "updatedAt",
                   (SELECT h.source FROM application_status_history h
                    WHERE h.application_id = a.id
                    ORDER BY h.changed_at DESC, h.id DESC LIMIT 1) AS "lastStatusSource",
                   (SELECT e.subject FROM email_message e
                    WHERE e.application_id = a.id
                    ORDER BY e.received_at DESC, e.id DESC LIMIT 1) AS "lastEmailSubject",
                   (SELECT e.received_at FROM email_message e
                    WHERE e.application_id = a.id
                    ORDER BY e.received_at DESC, e.id DESC LIMIT 1) AS "lastEmailAt"
            FROM job_application a
            WHERE a.id = :id
            """, nativeQuery = true)
    Optional<JobApplicationRow> findApplicationRowById(@Param("id") Long id);
}