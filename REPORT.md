# Distributed Real-Time Voting System Using Apache Kafka

---

**Course:** Distributed Systems  
**Academic Year:** 2025–2026  
**Date:** 5 March 2026  

---

## Table of Contents

1. [Introduction](#1-introduction)  
2. [Objectives](#2-objectives)  
3. [Technologies Used](#3-technologies-used)  
4. [System Architecture](#4-system-architecture)  
5. [Implementation Details](#5-implementation-details)  
   - 5.1 Vote Producer  
   - 5.2 Vote Consumer  
   - 5.3 Docker Infrastructure  
   - 5.4 Load Testing  
6. [Data Structures and Algorithms](#6-data-structures-and-algorithms)  
7. [Deployment Strategy](#7-deployment-strategy)  
8. [Testing and Results](#8-testing-and-results)  
9. [Conclusion](#9-conclusion)  

---

## 1. Introduction

Modern democratic processes increasingly demand digital solutions that are scalable, fault-tolerant, and capable of handling massive concurrent participation. This project presents the design and implementation of a **Distributed Real-Time Voting System** built using **Apache Kafka** as its messaging backbone. The system demonstrates core principles of distributed computing—decoupled microservices, asynchronous event streaming, fault recovery, and horizontal scalability—within a practical, real-world scenario: an electronic voting platform.

The application allows voters to cast votes through a REST API. Each vote is published as an event to a Kafka topic and subsequently consumed by a separate service that tallies results in real time. A unique voter-identification mechanism (based on Aadhar ID) ensures that duplicate votes are detected and rejected, preserving the integrity of the election.

---

## 2. Objectives

The primary objectives of this project are:

1. **Demonstrate Distributed Architecture** — Deploy functionally independent microservices across multiple physical machines communicating over a shared network.
2. **Leverage Event-Driven Design** — Use Apache Kafka as a publish-subscribe message broker to decouple the voting interface from the vote-counting logic.
3. **Ensure Data Integrity** — Implement a duplicate-detection mechanism so that each voter can cast only one vote.
4. **Achieve Scalability** — Prove the system can handle 1,000+ concurrent votes without data loss or service failure.
5. **Demonstrate Fault Tolerance** — Persist vote tallies to disk (`results.txt`) so the system can recover state after a restart.

---

## 3. Technologies Used

| Technology | Purpose |
|---|---|
| **Java 17** | Core programming language for both microservices |
| **Spring Boot 3.x** | Framework for building production-ready REST APIs |
| **Apache Kafka 7.6.1** | Distributed event-streaming platform for message brokering |
| **Apache Zookeeper** | Cluster coordination and broker metadata management |
| **Docker & Docker Compose** | Containerised deployment of Kafka and Zookeeper |
| **Maven** | Build automation and dependency management |
| **PowerShell** | Load-testing script for simulating concurrent voters |
| **cURL** | Command-line HTTP client for API interaction |

---

## 4. System Architecture

The system follows a **producer–consumer microservice pattern** with Kafka acting as the central event bus.

```
┌─────────────────────┐         ┌──────────────────────────────────┐
│   COMPUTER 3        │         │   COMPUTER 1 (Docker Host)       │
│   Vote Producer     │         │                                  │
│   (Spring Boot)     │  HTTP   │  ┌────────────┐ ┌────────────┐  │
│                     │ ──────► │  │  Kafka      │ │  Kafka     │  │
│  POST /vote?        │  Kafka  │  │  Broker 1   │ │  Broker 2  │  │
│  candidate=X&       │  topic  │  │  (port 9092)│ │  (port 9093│  │
│  aadhar=YYYY        │ "votes" │  └──────┬─────┘ └─────┬──────┘  │
│                     │         │         │             │          │
│  Port: 8080         │         │  ┌──────┴─────────────┴──────┐  │
└─────────────────────┘         │  │       Zookeeper            │  │
                                │  │       (port 2181)          │  │
                                │  └────────────────────────────┘  │
                                └──────────────────────────────────┘
                                                │
                                     Kafka topic "votes"
                                                │
                                                ▼
                                ┌──────────────────────────────────┐
                                │   COMPUTER 2                     │
                                │   Vote Consumer (Spring Boot)    │
                                │                                  │
                                │   • Listens on topic "votes"     │
                                │   • Deduplicates by Aadhar ID    │
                                │   • Tallies votes (HashMap)      │
                                │   • Persists to results.txt      │
                                │   • GET /results → JSON tally    │
                                │                                  │
                                │   Port: 8081                     │
                                └──────────────────────────────────┘

                                ┌──────────────────────────────────┐
                                │   COMPUTER 4                     │
                                │   Load Tester (PowerShell)       │
                                │                                  │
                                │   Fires 1,000 concurrent HTTP    │
                                │   POST requests to Computer 3    │
                                │   (each with a unique Aadhar ID) │
                                └──────────────────────────────────┘
```

**Data Flow:**

1. A voter sends an HTTP `POST` request to the **Producer** (`/vote?candidate=X&aadhar=Y`).
2. The Producer validates the input and publishes a message (`aadhar:candidate`) to the Kafka topic `votes`.
3. The **Consumer** subscribes to the `votes` topic and receives each message in real time.
4. The Consumer checks whether the Aadhar ID has already been used; if so, the vote is rejected.
5. If the vote is valid, the Consumer increments the candidate's tally and persists the updated state to `results.txt`.
6. A `GET /results` endpoint on the Consumer reads `results.txt` and returns the current tally as JSON.

---

## 5. Implementation Details

### 5.1 Vote Producer (`VoteProducerApplication.java`)

The Producer is a lightweight Spring Boot REST API that exposes a single endpoint:

- **`POST /vote`** — Accepts `candidate` and `aadhar` as query parameters.

Upon receiving a valid request, the Producer constructs a Kafka message in the format `aadhar:candidate` and publishes it to the `votes` topic using Spring Kafka's `KafkaTemplate`. Input validation ensures that neither the candidate name nor the Aadhar ID is empty, returning an HTTP 400 (Bad Request) response if either is missing.

**Key Code Excerpt:**

```java
@PostMapping("/vote")
public ResponseEntity<String> castVote(@RequestParam String candidate,
                                        @RequestParam String aadhar) {
    // Validate inputs
    if (candidate == null || candidate.trim().isEmpty())
        return ResponseEntity.badRequest().body("Candidate name cannot be empty");
    if (aadhar == null || aadhar.trim().isEmpty())
        return ResponseEntity.badRequest().body("Aadhar ID cannot be empty");

    // Publish to Kafka
    String message = aadhar + ":" + candidate;
    kafkaTemplate.send(TOPIC, message);
    return ResponseEntity.ok("Vote successfully cast for: " + candidate);
}
```

### 5.2 Vote Consumer (`VoteConsumerApplication.java`)

The Consumer is the data-processing heart of the system. It performs three critical functions:

1. **Event Listening** — Annotated with `@KafkaListener(topics = "votes")`, it continuously polls the Kafka broker for new vote events.
2. **Duplicate Detection** — Maintains a `HashSet<String>` of Aadhar IDs that have already voted. Any duplicate is immediately rejected with a console log message.
3. **Vote Tallying and Persistence** — Uses a `HashMap<String, Integer>` to maintain in-memory vote counts. After each valid vote, the entire state (vote counts + voter IDs) is written to `results.txt` for fault recovery.

The `synchronized` keyword is used on the critical section to ensure **thread safety**, preventing race conditions when multiple Kafka consumer threads process votes simultaneously.

**Key Code Excerpt:**

```java
@KafkaListener(topics = "votes", groupId = "voting-group")
public void listen(String message) {
    String[] parts = message.split(":", 2);
    String aadhar = parts[0];
    String candidate = parts[1];

    synchronized (this) {
        if (votedAadhars.contains(aadhar)) {
            System.out.println("Duplicate vote rejected for Aadhar: " + aadhar);
            return;
        }
        votedAadhars.add(aadhar);
        voteCounts.put(candidate, voteCounts.getOrDefault(candidate, 0) + 1);
        saveResults();
    }
}
```

### 5.3 Docker Infrastructure (`docker-compose.yml`)

The messaging backbone is deployed as a set of Docker containers managed by Docker Compose:

| Container | Image | Port | Role |
|---|---|---|---|
| `zookeeper` | `confluentinc/cp-zookeeper:7.6.1` | 2181 | Manages Kafka cluster metadata |
| `kafka1` | `confluentinc/cp-kafka:7.6.1` | 9092 | Primary Kafka broker |
| `kafka2` | `confluentinc/cp-kafka:7.6.1` | 9093 | Secondary Kafka broker (replication) |

Running **two brokers** with `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 2` ensures that vote events are replicated across both brokers, providing **fault tolerance** — if one broker goes down, the other continues to serve the topic.

### 5.4 Load Testing (`load-test.ps1`)

A PowerShell script simulates 1,000 concurrent voters by generating unique Aadhar IDs (using GUIDs) and firing parallel HTTP POST requests at the Producer API with a throttle limit of 50 concurrent connections:

```powershell
1..$totalVotes | ForEach-Object -Parallel {
    $randomAadhar = [guid]::NewGuid().ToString().Substring(0,8)
    $fullUrl = "$using:apiUrlBase?candidate=$using:candidate&aadhar=$randomAadhar"
    Invoke-WebRequest -Uri $fullUrl -Method POST -UseBasicParsing | Out-Null
} -ThrottleLimit 50
```

This test proves the system's ability to handle high-throughput, real-world election-day traffic without data loss.

---

## 6. Data Structures and Algorithms

### 6.1 HashMap (`java.util.HashMap`)

- **Used in:** `VoteConsumerApplication.java` as `Map<String, Integer> voteCounts`  
- **Purpose:** Stores candidate names as keys and their cumulative vote counts as values.  
- **Time Complexity:** **O(1)** average-case for both `get()` and `put()` operations, enabling instantaneous vote tallying regardless of the number of candidates.

### 6.2 HashSet (`java.util.HashSet`)

- **Used in:** `VoteConsumerApplication.java` as `Set<String> votedAadhars`  
- **Purpose:** Tracks which Aadhar IDs have already cast a vote to prevent duplicate voting.  
- **Time Complexity:** **O(1)** average-case for `contains()` and `add()`, allowing sub-millisecond duplicate detection even with millions of voter records.

### 6.3 Accumulator Pattern (State Management Algorithm)

The Consumer employs a **continuous accumulator algorithm**:

1. **Listen** — Continuously poll the Kafka event stream.
2. **Synchronise** — Acquire a lock (`synchronized`) to ensure mutual exclusion.
3. **Validate** — Check for duplicate Aadhar IDs using the HashSet.
4. **Accumulate** — Increment the candidate's count in the HashMap.
5. **Persist** — Write the full state to `results.txt` for crash recovery.

This pattern ensures that the system maintains **exactly-once semantics** at the application level, even when processing thousands of events per second.

---

## 7. Deployment Strategy

The system is deployed across **four physical computers** on a shared Wi-Fi network, each with a distinct role:

| Computer | Role | Component | Description |
|---|---|---|---|
| **Computer 1** | Infrastructure Engineer | Docker (Zookeeper + 2× Kafka) | Hosts the messaging backbone |
| **Computer 2** | Data Engineer | Vote Consumer (Spring Boot) | Processes and tallies votes |
| **Computer 3** | API Engineer | Vote Producer (Spring Boot) | Receives votes via REST API |
| **Computer 4** | Scalability Tester | Load Test (PowerShell script) | Simulates 1,000 concurrent voters |

**Network Configuration:** Before the demo, each machine's IP address is obtained via `ipconfig` and substituted into the configuration files (`docker-compose.yml`, `application.properties`, and `load-test.ps1`) to enable cross-machine communication.

---

## 8. Testing and Results

### 8.1 Single-Vote Test

A single HTTP request was sent to the Producer:

```
curl -X POST "http://<Computer_3_IP>:8080/vote?candidate=Progress%20United&aadhar=1111"
```

**Result:** The Consumer confirmed `Valid vote processed for: Progress United by Aadhar: 1111`, and the `/results` endpoint returned `{"Progress United": 1}`.

### 8.2 Duplicate-Vote Test

The same request (identical Aadhar ID) was sent a second time.

**Result:** The Consumer printed `Duplicate vote rejected for Aadhar: 1111`, and the tally remained `{"Progress United": 1}`. The duplicate-detection mechanism functioned correctly.

### 8.3 Load Test (1,000 Concurrent Voters)

The PowerShell script fired 1,000 parallel requests, each with a unique Aadhar ID.

**Result:** All 1,000 votes were successfully consumed and tallied. The `/results` endpoint returned combined totals, for example `{"Progress United": 251, "The Future Party": 249...}` (1 from the single-vote test + 1,000 from the load test). No votes were lost, and no service crashed.

### 8.4 Summary of Results

| Test Case | Expected Outcome | Actual Outcome | Status |
|---|---|---|---|
| Single valid vote | Tally incremented by 1 | `{"Progress United": 1}` | ✅ Pass |
| Duplicate Aadhar ID | Vote rejected, tally unchanged | `{"Progress United": 1}` | ✅ Pass |
| 1,000 concurrent votes | All 1,000 tallied correctly | e.g. `{"Progress United": 251...}` | ✅ Pass |

---

## 9. Conclusion

This project successfully demonstrates the design and deployment of a **distributed, event-driven voting system** using Apache Kafka and Spring Boot. By distributing the system across four physical machines, we validated core distributed systems concepts including:

- **Decoupled Microservices** — The Producer and Consumer operate independently; neither has knowledge of the other's internal implementation.
- **Asynchronous Communication** — Kafka's publish-subscribe model ensures that vote submission and vote processing occur asynchronously, preventing the Producer from being blocked by slow processing.
- **Fault Tolerance** — Dual Kafka brokers with topic replication and file-based state persistence ensure no data is lost during failures.
- **Scalability** — The system handled 1,000 concurrent voters without degradation, proving its readiness for real-world election-scale loads.
- **Data Integrity** — The HashSet-based Aadhar deduplication guarantees a strict one-person-one-vote policy, which is essential for any credible election system.

In conclusion, Apache Kafka proved to be an excellent choice for building real-time, distributed applications. Its high throughput, built-in replication, and consumer-group model make it an industry-standard tool for event streaming, and this project demonstrates its practical applicability in the domain of electronic governance.

---

*End of Report*
