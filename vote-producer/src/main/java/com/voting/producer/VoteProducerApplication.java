package com.voting.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
@CrossOrigin("*")
public class VoteProducerApplication {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final String TOPIC = "election-votes";

    public VoteProducerApplication(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(VoteProducerApplication.class, args);
    }

    @PostMapping("/vote")
    public ResponseEntity<String> castVote(@RequestParam String candidate, @RequestParam String aadhar) {
        if (candidate == null || candidate.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Candidate name cannot be empty");
        }
        if (aadhar == null || aadhar.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Aadhar ID cannot be empty");
        }

        // Send the vote event to Kafka topic formatted as Aadhar:Candidate
        String message = aadhar + ":" + candidate;
        kafkaTemplate.send(TOPIC, message);
        return ResponseEntity.ok("Vote successfully cast for: " + candidate + " by Aadhar: " + aadhar);
    }
}
