# Per-task runner: executes one Claude Code headless call.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File runner.ps1 -TaskId <id>
param([string]$TaskId)
$ErrorActionPreference = 'Continue'

$root   = 'E:\Work Document\.agent-hub'
$claude = 'C:\Users\y1954\AppData\Roaming\npm\claude.cmd'

$promptFile = Join-Path $root ("prompts\" + $TaskId + ".txt")
$outFile    = Join-Path $root ("reports\" + $TaskId + ".out.txt")
$errFile    = Join-Path $root ("reports\" + $TaskId + ".err.txt")
$exitFile   = Join-Path $root ("reports\" + $TaskId + ".exit.txt")

# Ensure runtime directories exist (they are cleaned up at end of session).
New-Item -ItemType Directory -Force -Path (Join-Path $root 'prompts'), (Join-Path $root 'reports') | Out-Null

# Self-sufficiency: if the prompt file is missing, extract it from tasks.json.
if (-not (Test-Path $promptFile)) {
    $raw = Get-Content (Join-Path $root 'tasks.json') -Raw -Encoding UTF8
    $all = ConvertFrom-Json $raw
    if ($all -isnot [array]) { $all = @($all) }
    $task = $all | Where-Object { $_.id -eq $TaskId }
    if ($task) {
        $task.prompt | Set-Content $promptFile -Encoding UTF8
    } else {
        Write-Output "ERROR task id $TaskId not found in tasks.json"
        exit 2
    }
}

# Prompt is read from a UTF-8 file via cmd stdin redirection (avoids PS5.1 ANSI mangling).
cmd /c "`"$claude`" -p --output-format text < `"$promptFile`" > `"$outFile`" 2> `"$errFile`""
$code = $LASTEXITCODE
$code | Set-Content $exitFile -Encoding UTF8

# Race-free status marker: one file per task, aggregator merges later.
$statusText = if ($code -eq 0) { 'done' } else { 'failed' }
$statusText + '|' + (Get-Date -Format o) | Set-Content (Join-Path $root ("reports\" + $TaskId + ".status.txt")) -Encoding UTF8
exit $code
