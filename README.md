# StudentConnect Backend

Welcome to the **StudentConnect Backend** repository. This is a robust, scalable Java-based REST API built using Spring Boot. It serves as the core engine for the StudentConnect platform, handling user authentication, profiles, mentorship connections, job applications, and real-time push notifications.

---

## 🚀 Tech Stack

* **Language:** Java 17+
* **Framework:** Spring Boot 3.x
    * Spring Security (with JWT Authentication)
    * Spring Data JPA (Data Persistence)
* **Database:** PostgreSQL / MySQL (Configurable via profiles)
* **Build Tool:** Maven

---

## 🛠️ Features

* **Role-Based Access Control (RBAC):** Separate logic and endpoints for `STUDENT`, `MENTOR`, and `EMPLOYER` profiles.
* **Secure Authentication:** Stateless session management via custom JWT filters (`JwtAuthFilter`).
* **Mentorship Matchmaking:** Functional repositories to initiate and monitor peer-to-peer or student-to-mentor connections.
* **Job & Application Portal:** Employers can post job listings; students can apply seamlessly.
* **Push Notification Service:** Integrated token management system to handle scalable user alerting.

---

## 📦 Project Structure

```text
com.studentconnect
├── controller          # REST API endpoints Exposed to the frontend
├── entity              # JPA Database Models (User, StudentProfile, JobListing, etc.)
├── repository          # Data Access Layers extending JpaRepository
├── security            # JWT Util and Spring Security Configurations
└── service             # Core Business Logic (e.g., NotificationService)
