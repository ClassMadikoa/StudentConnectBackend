-- ============================================================
-- StudentConnect Hub — PostgreSQL Schema
-- Run this once to initialize the database
-- ============================================================

CREATE DATABASE studentconnect;
\c studentconnect;

-- ─── Core User Table ─────────────────────────────────────────────────────────
CREATE TABLE users (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name         VARCHAR(100) NOT NULL,
    role         VARCHAR(20)  NOT NULL CHECK (role IN ('STUDENT', 'MENTOR', 'EMPLOYER')),
    created_at   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role  ON users(role);

-- ─── Student Profiles ────────────────────────────────────────────────────────
CREATE TABLE student_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    major               VARCHAR(100),
    university          VARCHAR(150),
    gpa                 DECIMAL(3,2),
    graduation_year     INT,
    skills              JSONB DEFAULT '[]',    -- e.g. ["React","Java","PostgreSQL"]
    bio                 TEXT,
    resume_url          VARCHAR(500),
    open_to_opportunities BOOLEAN DEFAULT true
);
CREATE INDEX idx_student_skills ON student_profiles USING gin(skills);

-- ─── Mentor Profiles ──────────────────────────────────────────────────────────
CREATE TABLE mentor_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company             VARCHAR(100),
    job_title           VARCHAR(100),
    years_of_experience INT,
    expertise           JSONB DEFAULT '[]',
    bio                 TEXT,
    rating              DECIMAL(3,2) DEFAULT 0.00,
    total_sessions      INT DEFAULT 0,
    accepting           BOOLEAN DEFAULT true
);
CREATE INDEX idx_mentor_expertise ON mentor_profiles USING gin(expertise);

-- ─── Employer Profiles ───────────────────────────────────────────────────────
CREATE TABLE employer_profiles (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_name VARCHAR(150) NOT NULL,
    industry     VARCHAR(100),
    website      VARCHAR(255),
    location     VARCHAR(100),
    description  TEXT
);
CREATE INDEX idx_employer_company ON employer_profiles(company_name);

-- ─── Job Listings ─────────────────────────────────────────────────────────────
CREATE TABLE job_listings (
    id              BIGSERIAL PRIMARY KEY,
    employer_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(150) NOT NULL,
    description     TEXT,
    type            VARCHAR(20) CHECK (type IN ('INTERNSHIP','PART_TIME','FULL_TIME','BURSARY')),
    location        VARCHAR(100),
    salary          VARCHAR(50),
    required_skills JSONB DEFAULT '[]',   -- enables @> matching
    active          BOOLEAN DEFAULT true,
    posted_at       TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_job_skills   ON job_listings USING gin(required_skills);
CREATE INDEX idx_job_employer ON job_listings(employer_id);
CREATE INDEX idx_job_active   ON job_listings(active);

-- ─── Job Applications ─────────────────────────────────────────────────────────
CREATE TABLE job_applications (
    id         BIGSERIAL PRIMARY KEY,
    job_id     BIGINT NOT NULL REFERENCES job_listings(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status     VARCHAR(20) DEFAULT 'SUBMITTED'
               CHECK (status IN ('SUBMITTED','INTERVIEWING','HIRED','REJECTED')),
    applied_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (job_id, student_id)
);

-- ─── Mentorship Connections ───────────────────────────────────────────────────
CREATE TABLE mentorship_connections (
    id           BIGSERIAL PRIMARY KEY,
    student_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mentor_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       VARCHAR(20) DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','ACTIVE','TERMINATED')),
    topic        TEXT,
    requested_at TIMESTAMP DEFAULT NOW(),
    accepted_at  TIMESTAMP,
    UNIQUE (student_id, mentor_id)
);

-- ─── Messages ─────────────────────────────────────────────────────────────────
CREATE TABLE messages (
    id           BIGSERIAL PRIMARY KEY,
    sender_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content      TEXT NOT NULL,
    sent_at      TIMESTAMP DEFAULT NOW(),
    read         BOOLEAN DEFAULT false
);
CREATE INDEX idx_messages_sender    ON messages(sender_id);
CREATE INDEX idx_messages_recipient ON messages(recipient_id);
CREATE INDEX idx_messages_time      ON messages(sent_at DESC);

-- ─── Skill Match Function (JSONB) ─────────────────────────────────────────────
-- Find jobs that require at least one skill the student has
CREATE OR REPLACE FUNCTION get_matching_jobs(student_skills JSONB)
RETURNS TABLE (
    job_id    BIGINT,
    title     VARCHAR,
    company   VARCHAR,
    match_pct INT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        jl.id,
        jl.title,
        ep.company_name,
        -- Percentage: matched skills / total required skills * 100
        CASE
            WHEN jsonb_array_length(jl.required_skills) = 0 THEN 0
            ELSE (
                SELECT COUNT(*)::INT
                FROM   jsonb_array_elements_text(jl.required_skills) rs
                WHERE  student_skills ? rs
            ) * 100 / jsonb_array_length(jl.required_skills)
        END AS match_pct
    FROM job_listings jl
    JOIN employer_profiles ep ON ep.user_id = jl.employer_id
    WHERE jl.active = true
    ORDER BY match_pct DESC;
END;
$$ LANGUAGE plpgsql;

-- ─── Push Tokens ─────────────────────────────────────────────────────────────
-- Stores Expo push tokens per user device.
-- One user can have multiple tokens (multiple devices / reinstalls).
CREATE TABLE push_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    push_token    VARCHAR(200) NOT NULL,
    registered_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, push_token)
);
CREATE INDEX idx_push_tokens_user ON push_tokens(user_id);

-- ─── Seed Demo Data ───────────────────────────────────────────────────────────
-- Password: 'password123' hashed with BCrypt (rounds=10)
INSERT INTO users (email, password_hash, name, role) VALUES
  ('student@demo.com',  '$2a$10$XOq5kISjZEkxlpAZwL2CbO5ja0IT1gIuvH3SDsc2N6cDtbf0w5lr6', 'Alex Johnson',    'STUDENT'),
  ('mentor@demo.com',   '$2a$10$XOq5kISjZEkxlpAZwL2CbO5ja0IT1gIuvH3SDsc2N6cDtbf0w5lr6', 'Dr. Sarah Chen',  'MENTOR'),
  ('employer@demo.com', '$2a$10$XOq5kISjZEkxlpAZwL2CbO5ja0IT1gIuvH3SDsc2N6cDtbf0w5lr6', 'TechNova Inc.',   'EMPLOYER');

INSERT INTO student_profiles (user_id, major, university, gpa, skills)
  VALUES (1, 'Computer Science', 'State University', 3.8, '["React","Java","Spring Boot","PostgreSQL"]');

INSERT INTO mentor_profiles (user_id, company, job_title, years_of_experience, expertise, rating, total_sessions)
  VALUES (2, 'Google', 'Senior Software Engineer', 8, '["Java","Spring Boot","System Design"]', 4.9, 142);

INSERT INTO employer_profiles (user_id, company_name, industry, website, location)
  VALUES (3, 'TechNova', 'Software Technology', 'https://technova.io', 'San Francisco, CA');

INSERT INTO job_listings (employer_id, title, description, type, location, salary, required_skills)
  VALUES
  (3, 'Junior React Developer',  'Build modern web apps using React and Node.js.', 'INTERNSHIP', 'Remote',   '$25/hr',  '["React","JavaScript"]'),
  (3, 'Backend Engineer',        'Design REST APIs using Java Spring Boot.',        'PART_TIME',  'Hybrid',   '$30/hr',  '["Java","Spring Boot","PostgreSQL"]'),
  (3, 'Full Stack Developer',    'Work across the full stack in our startup env.',  'FULL_TIME',  'Remote',   '$60k/yr', '["React","Java","PostgreSQL"]'),
  (3, 'TechNova STEM Bursary',   'Annual bursary for outstanding STEM students.',   'BURSARY',    'National', '$5,000',  '["STEM","University","CS"]');
