$apiUrlBase = "http://<COMPUTER_3_IP>:8080/vote"
$candidate = "A"

Write-Host "Simulating 1,000 distinct voters casting votes for Candidate A..."

$totalVotes = 1000

# Fire off 1,000 concurrent web requests with distinct Aadhar numbers
1..$totalVotes | ForEach-Object -Parallel {
    $randomAadhar = [guid]::NewGuid().ToString().Substring(0,8)
    $fullUrl = "$using:apiUrlBase`?candidate=$using:candidate&aadhar=$randomAadhar"
    Invoke-WebRequest -Uri $fullUrl -Method POST -UseBasicParsing | Out-Null
} -ThrottleLimit 50

Write-Host "Done! 1,000 distinct Aadhar votes successfully blasted to Computer 3."
Write-Host "Check Computer 2 to verify all votes were successfully queued in Kafka and counted!"
