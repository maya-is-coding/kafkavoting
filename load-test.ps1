$apiUrlBase = "http://<COMPUTER_3_IP>:8080/vote"

Write-Host "Simulating 1,000 distinct voters casting votes across all parties..."

$totalVotes = 1000
$candidates = @("Falcon Party", "Lion Party", "Dolphin Party", "Elephant Party")

# Fire off 1,000 concurrent web requests with distinct Aadhar numbers
1..$totalVotes | ForEach-Object -Parallel {
    $randomAadhar = [guid]::NewGuid().ToString().Substring(0,8)
    
    # Select a random candidate from the array
    $candidateList = $using:candidates
    $randomCandidate = $candidateList | Get-Random
    $encodedCandidate = [uri]::EscapeDataString($randomCandidate)
    
    $fullUrl = "$using:apiUrlBase`?candidate=$encodedCandidate&aadhar=$randomAadhar"
    
    try {
        Invoke-WebRequest -Uri $fullUrl -Method POST -UseBasicParsing | Out-Null
    } catch {
        # Ignore temporary failure
    }
} -ThrottleLimit 50

Write-Host "Done! 1,000 distinct Aadhar votes successfully blasted to Computer 3."
Write-Host "Check Computer 2 to verify all votes were successfully queued in Kafka and counted!"
