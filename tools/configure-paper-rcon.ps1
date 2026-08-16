[CmdletBinding()]
param(
    [string]$ServerDirectory = "F:\minecraftserver\villagedefense2026",
    [int]$Port = 0
)

$ErrorActionPreference = "Stop"
$propertiesPath = Join-Path $ServerDirectory "server.properties"
$firewallGroup = "LivingNPC Paper RCON"

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Run this script from an elevated PowerShell window so it can install the localhost-only firewall rule."
}
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "Paper server properties do not exist: $propertiesPath"
}

$listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort -Unique)
if ($Port -eq 0) {
    do { $Port = Get-Random -Minimum 30000 -Maximum 60000 } while ($listeners -contains $Port)
} elseif ($Port -lt 1024 -or $Port -gt 65535) {
    throw "RCON port must be between 1024 and 65535."
} elseif ($listeners -contains $Port) {
    throw "TCP port $Port is already in use."
}

$passwordBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($passwordBytes)
$password = [Convert]::ToBase64String($passwordBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupPath = "$propertiesPath.rcon-backup-$timestamp"
Copy-Item -LiteralPath $propertiesPath -Destination $backupPath

try {
    $lines = [IO.File]::ReadAllLines($propertiesPath)
    $values = @{
        "broadcast-rcon-to-ops" = "false"
        "enable-rcon" = "true"
        "rcon.password" = $password
        "rcon.port" = [string]$Port
    }
    foreach ($key in @($values.Keys)) {
        $found = $false
        for ($index = 0; $index -lt $lines.Length; $index++) {
            if ($lines[$index].StartsWith("$key=", [StringComparison]::Ordinal)) {
                $lines[$index] = "$key=$($values[$key])"
                $found = $true
                break
            }
        }
        if (-not $found) { $lines += "$key=$($values[$key])" }
    }
    [IO.File]::WriteAllLines($propertiesPath, $lines, (New-Object Text.UTF8Encoding($false)))

    $firewallRuleName = "LivingNPC-Paper-RCON-$Port"
    New-NetFirewallRule `
        -Name $firewallRuleName `
        -DisplayName "Block remote Paper RCON ($Port)" `
        -Group $firewallGroup `
        -Direction Inbound `
        -Action Block `
        -Protocol TCP `
        -LocalPort $Port `
        -RemoteAddress Any `
        -Profile Any | Out-Null
    Get-NetFirewallRule -Group $firewallGroup -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne $firewallRuleName } |
        Remove-NetFirewallRule
} catch {
    if ($firewallRuleName) {
        Get-NetFirewallRule -Name $firewallRuleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    }
    Copy-Item -LiteralPath $backupPath -Destination $propertiesPath -Force
    throw "RCON setup failed and server.properties was restored from $backupPath. $($_.Exception.Message)"
}

Write-Host "Paper RCON configured on TCP port $Port."
Write-Host "Remote inbound access to that port is blocked by Windows Firewall; local loopback remains available."
Write-Host "Backup: $backupPath"
Write-Host "Restart Paper once to activate RCON. The generated password remains only in server.properties."
