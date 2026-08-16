[CmdletBinding()]
param(
    [string]$ServerDirectory = "F:\minecraftserver\villagedefense2026",
    [int]$StartupTimeoutSeconds = 180,
    [int]$ObserveSeconds = 180,
    [string]$JavaCommand = "java",
    [string[]]$JavaArguments = @("-Xms2G", "-Xmx4G", "-jar", "paper.jar", "nogui"),
    [string[]]$RequiredRoles = @("fisher", "rancher"),
    [int]$ShutdownTimeoutSeconds = 120,
    [switch]$NoAutoStop,
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"
$projectDirectory = Split-Path -Parent $PSScriptRoot
$pluginsDirectory = Join-Path $ServerDirectory "plugins"
$pluginDataDirectory = Join-Path $pluginsDirectory "LivingNPC"
$logPath = Join-Path $ServerDirectory "logs\latest.log"
$paperJar = Join-Path $ServerDirectory "paper.jar"
$serverPropertiesPath = Join-Path $ServerDirectory "server.properties"
$script:launchedProcessId = $null

function Get-ServerProcess {
    if ($script:launchedProcessId) {
        return Get-CimInstance Win32_Process -Filter "ProcessId = $script:launchedProcessId" -ErrorAction SilentlyContinue
    }
    if (-not (Test-Path -LiteralPath $serverPropertiesPath -PathType Leaf)) { return }
    $portLines = @([IO.File]::ReadAllLines($serverPropertiesPath) | Where-Object { $_ -match '^server-port=(\d+)$' })
    if ($portLines.Count -ne 1) { return }
    $serverPort = [int]($portLines[0] -replace '^server-port=', '')
    $processIds = @(Get-NetTCPConnection -State Listen -LocalPort $serverPort -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
    foreach ($processId in $processIds) {
        Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue | Where-Object {
            $_.Name -eq 'java.exe' -and $_.CommandLine -match "(?i)(?:^|[\\/\s])paper\.jar(?:\s|$)"
        }
    }
}

function Read-LatestLog {
    if (-not (Test-Path -LiteralPath $logPath)) { return "" }
    $stream = New-Object System.IO.FileStream(
        $logPath,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::ReadWrite
    )
    try {
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8, $true)
        try { return $reader.ReadToEnd() }
        finally { $reader.Dispose() }
    }
    finally { $stream.Dispose() }
}

function Get-LatestLogLength {
    if (-not (Test-Path -LiteralPath $logPath)) { return 0L }
    return (Get-Item -LiteralPath $logPath).Length
}

function Read-LatestLogFrom([long]$offset) {
    if (-not (Test-Path -LiteralPath $logPath)) { return "" }
    $stream = New-Object System.IO.FileStream(
        $logPath,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::ReadWrite
    )
    try {
        if ($offset -lt 0 -or $offset -gt $stream.Length) { $offset = 0L }
        $null = $stream.Seek($offset, [System.IO.SeekOrigin]::Begin)
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8, $true)
        try { return $reader.ReadToEnd() }
        finally { $reader.Dispose() }
    }
    finally { $stream.Dispose() }
}

function Stop-PaperCleanly {
    $processes = @(Get-ServerProcess)
    if ($processes.Count -eq 0) { return }
    if ($NoAutoStop) {
        throw "Paper is already running from $ServerDirectory and automatic shutdown was disabled."
    }

    Write-Host "[0/5] Stopping Paper cleanly through local RCON..."
    $shutdownLogOffset = Get-LatestLogLength
    & (Join-Path $PSScriptRoot "paper-rcon.ps1") stop -ServerDirectory $ServerDirectory

    $deadline = (Get-Date).AddSeconds($ShutdownTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        $remaining = @($processes | Where-Object { Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue })
        if ($remaining.Count -eq 0) {
            $shutdownLog = Read-LatestLogFrom $shutdownLogOffset
            if ($shutdownLog -notmatch "Stopping server") {
                throw "Paper exited, but the clean shutdown marker was not found in $logPath"
            }
            Write-Host "      Paper stopped after saving plugin and world data."
            return
        }
    }
    throw "Paper did not stop within $ShutdownTimeoutSeconds seconds. It was not forcibly terminated."
}

if (-not (Test-Path -LiteralPath $ServerDirectory -PathType Container)) {
    throw "Server directory does not exist: $ServerDirectory"
}
if (-not (Test-Path -LiteralPath $pluginsDirectory -PathType Container)) {
    throw "Plugins directory does not exist: $pluginsDirectory"
}
if (-not (Test-Path -LiteralPath $paperJar -PathType Leaf)) {
    throw "Paper jar does not exist: $paperJar"
}
if ($CheckOnly) {
    if (-not (Get-ServerProcess)) { throw "Paper is not running from $ServerDirectory." }
    Write-Host "Checking the currently running Paper server. No build, deploy or restart will occur."
    $log = Read-LatestLog
    if ($log -notmatch "Done \([0-9.]+s\)!") { throw "The current Paper log has no Done marker: $logPath" }
    $observationLogOffset = Get-LatestLogLength
} else {
    Stop-PaperCleanly

    Write-Host "[1/5] Building and testing LivingNPC..."
    & (Join-Path $projectDirectory "gradlew.bat") -p $projectDirectory clean test build --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }
    $builtJars = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory "build\libs") -Filter "living-npc-*.jar" -File)
    if ($builtJars.Count -ne 1) {
        throw "Expected exactly one built LivingNPC jar, found $($builtJars.Count)."
    }
    $builtJar = $builtJars[0]
    $jarName = $builtJar.Name
    $deployedJar = Join-Path $pluginsDirectory $jarName

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $backupDirectory = Join-Path $pluginsDirectory "LivingNPC-backup-$timestamp"
    Write-Host "[2/5] Backing up current jar and plugin data to $backupDirectory"
    New-Item -ItemType Directory -Path $backupDirectory | Out-Null
    $deployedJars = @(Get-ChildItem -LiteralPath $pluginsDirectory -Filter "living-npc-*.jar" -File)
    foreach ($existingJar in $deployedJars) {
        Copy-Item -LiteralPath $existingJar.FullName -Destination $backupDirectory
    }
    if (Test-Path -LiteralPath $pluginDataDirectory -PathType Container) {
        Copy-Item -LiteralPath $pluginDataDirectory -Destination $backupDirectory -Recurse
    }

    Write-Host "[3/5] Deploying $jarName"
    foreach ($existingJar in $deployedJars) {
        Remove-Item -LiteralPath $existingJar.FullName
    }
    Copy-Item -LiteralPath $builtJar.FullName -Destination $deployedJar
    $deployedHash = (Get-FileHash -LiteralPath $deployedJar -Algorithm SHA256).Hash
    Write-Host "      SHA-256: $deployedHash"

    $observationLogOffset = Get-LatestLogLength
    $launchTime = Get-Date
    Write-Host "[4/5] Starting Paper. The server will remain running after this check."
    $launchedProcess = Start-Process -FilePath $JavaCommand -ArgumentList $JavaArguments -WorkingDirectory $ServerDirectory -PassThru
    $script:launchedProcessId = $launchedProcess.Id

    $startupDeadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    $started = $false
    while ((Get-Date) -lt $startupDeadline) {
        Start-Sleep -Seconds 2
        if (-not (Get-ServerProcess)) { throw "Paper exited during startup. Check $logPath" }
        if (Test-Path -LiteralPath $logPath) {
            $logFile = Get-Item -LiteralPath $logPath
            $log = Read-LatestLog
            if ($logFile.LastWriteTime -ge $launchTime.AddSeconds(-2) -and $log -match "Done \([0-9.]+s\)!") {
                $started = $true
                break
            }
        }
    }
    if (-not $started) { throw "Paper did not reach Done within $StartupTimeoutSeconds seconds. Check $logPath" }
}

$log = if ($CheckOnly) { Read-LatestLog } else { Read-LatestLogFrom $observationLogOffset }
if ($log -notmatch "\[LivingNPC\] LivingNPC Season [0-9]+ enabled with") {
    throw "Paper started, but the expected LivingNPC enable marker was not found in $logPath"
}

Write-Host "[5/5] Observing NPC diagnostics for $ObserveSeconds seconds..."
$observeDeadline = (Get-Date).AddSeconds($ObserveSeconds)
while ((Get-Date) -lt $observeDeadline) {
    Start-Sleep -Seconds 5
    if (-not (Get-ServerProcess)) { throw "Paper exited during NPC observation." }
}

$log = Read-LatestLogFrom $observationLogOffset
$fatalPatterns = @(
    "(?im)^.*\[(?:ERROR|SEVERE)\]:.*LivingNPC.*$",
    "(?im)^.*Error occurred while enabling LivingNPC.*$",
    "(?im)^.*Could not pass event .* to LivingNPC.*$",
    "(?im)^.*(?:Exception|NoClassDefFoundError).*vn\.heomc\.livingnpc.*$",
    "(?im)^.*\s+at vn\.heomc\.livingnpc\..*$"
)
$fatalLines = foreach ($pattern in $fatalPatterns) {
    [regex]::Matches($log, $pattern) | ForEach-Object { $_.Value.Trim() }
}
$fatalLines = @($fatalLines | Sort-Object -Unique)

$unresolved = @{}
foreach ($line in ($log -split "`r?`n")) {
    if ($line -match "NPC_DIAGNOSTIC uuid=([0-9a-fA-F-]{36}) state=(ERROR|RECOVERED)") {
        $npcId = $Matches[1]
        if ($Matches[2] -eq "ERROR") { $unresolved[$npcId] = $line.Trim() }
        else { $unresolved.Remove($npcId) }
    }
}

$healthMatches = [regex]::Matches($log, "NPC_HEALTH total=(\d+) ok=(\d+) waiting=(\d+) errors=(\d+)")
$latestHealth = if ($healthMatches.Count -gt 0) { $healthMatches[$healthMatches.Count - 1] } else { $null }
$completedRoles = @{}
foreach ($line in ($log -split "`r?`n")) {
    if ($line -match "NPC_ROLE_ACTIVITY uuid=[0-9a-fA-F-]{36} role=([a-z-]+) result=completed") {
        $completedRoles[$Matches[1]] = $true
    }
}
$missingRoles = @($RequiredRoles | Where-Object { -not $completedRoles.ContainsKey($_) })
if ($fatalLines.Count -gt 0 -or $unresolved.Count -gt 0 -or ($latestHealth -and [int]$latestHealth.Groups[4].Value -gt 0)) {
    Write-Host "NPC smoke check FAILED. Fatal log lines: $($fatalLines.Count); unresolved NPC diagnostics: $($unresolved.Count)."
    $fatalLines | ForEach-Object { Write-Host "  $_" }
    $unresolved.Values | ForEach-Object { Write-Host "  $_" }
    exit 1
}

if ($latestHealth -and [int]$latestHealth.Groups[2].Value -gt 0 -and $missingRoles.Count -eq 0) {
    Write-Host "PASS: Paper and LivingNPC started; required roles completed work: $($RequiredRoles -join ', '). $($latestHealth.Value)"
    exit 0
}

$healthText = if ($latestHealth) { $latestHealth.Value } else { "no NPC_HEALTH marker" }
$missingText = if ($missingRoles.Count -gt 0) { $missingRoles -join ", " } else { "none" }
Write-Warning "INCONCLUSIVE: Paper and LivingNPC started without fatal errors, but required role evidence is missing: $missingText ($healthText). Ensure those NPCs are spawned, on shift, configured, and near a player, then run the check again."
exit 2
