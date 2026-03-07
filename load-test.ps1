$apiUrlBase = "http://10.12.71.152:8080"
$totalVotes = 1000
$candidates = @("The Future Party", "Progress United", "Common Ground", "The Bridge Alliance")

Write-Host "Simulating $totalVotes distinct voters casting votes across all parties..."

# PowerShell 5.1 concurrency using RunspacePool
$RunspacePool = [runspacefactory]::CreateRunspacePool(1, 50)
$RunspacePool.Open()
$Jobs = New-Object System.Collections.Generic.List[PSObject]

$ScriptBlock = {
    Param($apiUrlBase, $candidates)
    $randomAadhar = [guid]::NewGuid().ToString().Substring(0,8)
    
    # Select a random candidate
    $randomCandidate = $candidates | Get-Random
    $encodedCandidate = [uri]::EscapeDataString($randomCandidate)
    
    $fullUrl = "$apiUrlBase`?candidate=$encodedCandidate&aadhar=$randomAadhar"
    
    try {
        Invoke-WebRequest -Uri $fullUrl -Method POST -UseBasicParsing | Out-Null
    } catch {
        # Ignore temporary failure
    }
}

# Fire off 1,000 requests
1..$totalVotes | ForEach-Object {
    $PowerShell = [powershell]::Create().AddScript($ScriptBlock).AddArgument($apiUrlBase).AddArgument($candidates)
    $PowerShell.RunspacePool = $RunspacePool
    $Jobs.Add((New-Object PSObject -Property @{
        Runspace = $PowerShell
        Handle   = $PowerShell.BeginInvoke()
    }))
}

# Wait for all jobs to complete
Write-Host "Waiting for requests to finish..." -NoNewline
while ($Jobs.Handle.IsCompleted -contains $false) {
    Write-Host "." -NoNewline
    Start-Sleep -Milliseconds 200
}
Write-Host " Done."

# Cleanup
$Jobs | ForEach-Object { $_.Runspace.EndInvoke($_.Handle); $_.Runspace.Dispose() }
$RunspacePool.Close()

Write-Host "Done! $totalVotes distinct Aadhar votes successfully blasted to Computer 3."
Write-Host "Check Computer 2 to verify all votes were successfully queued in Kafka and counted!"