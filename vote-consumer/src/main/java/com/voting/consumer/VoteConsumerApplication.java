package com.voting.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@SpringBootApplication
@RestController
@CrossOrigin("*")
public class VoteConsumerApplication {

    private static final String RESULTS_FILE = "results.txt";
    private final Map<String, Integer> voteCounts = new HashMap<>();
    private final Set<String> votedAadhars = new HashSet<>();

    public static void main(String[] args) {
        SpringApplication.run(VoteConsumerApplication.class, args);
    }

    public VoteConsumerApplication() {
        // Load initial counts and aadhars from file on startup
        loadResults();
    }

    @KafkaListener(topics = "election-votes", groupId = "voting-group")
    public void listen(String message) {
        // Message is now in format "AadharID:CandidateName"
        String[] parts = message.split(":", 2);
        if (parts.length != 2) {
            System.err.println("Invalid vote message format: " + message);
            return;
        }

        String aadhar = parts[0];
        String candidate = parts[1];

        synchronized (this) {
            // Check if the user already voted
            if (votedAadhars.contains(aadhar)) {
                System.out.println("Duplicate vote rejected for Aadhar: " + aadhar);
                return; // Ignore the vote
            }

            // Accept vote
            System.out.println("Valid vote processed for: " + candidate + " by Aadhar: " + aadhar);
            votedAadhars.add(aadhar);
            voteCounts.put(candidate, voteCounts.getOrDefault(candidate, 0) + 1);
            saveResults();
        }
    }

    private void loadResults() {
        File file = new File(RESULTS_FILE);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("VOTER:")) {
                    votedAadhars.add(line.substring(6));
                } else {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        voteCounts.put(parts[0], Integer.parseInt(parts[1]));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveResults() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(RESULTS_FILE))) {
            // Save vote counts
            for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
                bw.write(entry.getKey() + "=" + entry.getValue());
                bw.newLine();
            }
            // Save voted aadhars
            for (String aadhar : votedAadhars) {
                bw.write("VOTER:" + aadhar);
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/results")
    public Map<String, Integer> getResults() {
        // Return the in-memory map for real-time accuracy and to avoid file locking
        // issues
        return new HashMap<>(voteCounts);
    }

    @PostMapping("/clear")
    public void clearResults() {
        synchronized (this) {
            System.out.println("MANUAL CLEAR REQUESTED BY UI: Resetting Election Results.");
            voteCounts.clear();
            votedAadhars.clear();
            saveResults();
        }
    }
}
