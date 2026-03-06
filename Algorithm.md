### Vote Counting Algorithm

1. Producer receives vote from `/vote?candidate=X`
2. Producer sends candidate name as a message to Kafka topic `votes`
3. Consumer listens to `votes` continuously
4. On each message:
   - read `results.txt`
   - increase count for that candidate
   - write updated counts back to `results.txt`
5. `/results` reads `results.txt` and returns counts
