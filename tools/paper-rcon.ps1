[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string]$Command = "stop",
    [string]$ServerDirectory = "F:\minecraftserver\villagedefense2026",
    [string]$HostName = "127.0.0.1",
    [int]$ConnectTimeoutMilliseconds = 5000
)

$ErrorActionPreference = "Stop"
$propertiesPath = Join-Path $ServerDirectory "server.properties"

function Read-Exact([IO.Stream]$Stream, [int]$Count) {
    $buffer = New-Object byte[] $Count
    $offset = 0
    while ($offset -lt $Count) {
        $read = $Stream.Read($buffer, $offset, $Count - $offset)
        if ($read -eq 0) { throw "RCON connection closed unexpectedly." }
        $offset += $read
    }
    return $buffer
}

function Send-Packet([IO.Stream]$Stream, [int]$RequestId, [int]$Type, [string]$Body) {
    $bodyBytes = [Text.Encoding]::UTF8.GetBytes($Body)
    $length = 10 + $bodyBytes.Length
    $packet = New-Object byte[] ($length + 4)
    [BitConverter]::GetBytes($length).CopyTo($packet, 0)
    [BitConverter]::GetBytes($RequestId).CopyTo($packet, 4)
    [BitConverter]::GetBytes($Type).CopyTo($packet, 8)
    $bodyBytes.CopyTo($packet, 12)
    $Stream.Write($packet, 0, $packet.Length)
    $Stream.Flush()
}

function Receive-Packet([IO.Stream]$Stream) {
    $length = [BitConverter]::ToInt32((Read-Exact $Stream 4), 0)
    if ($length -lt 10 -or $length -gt 1048576) { throw "Invalid RCON packet length: $length" }
    $packet = Read-Exact $Stream $length
    return [PSCustomObject]@{
        RequestId = [BitConverter]::ToInt32($packet, 0)
        Type = [BitConverter]::ToInt32($packet, 4)
        Body = [Text.Encoding]::UTF8.GetString($packet, 8, $length - 10)
    }
}

if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "Paper server properties do not exist: $propertiesPath"
}
$properties = @{}
foreach ($line in [IO.File]::ReadAllLines($propertiesPath)) {
    if ($line -match '^([^#!][^=]*)=(.*)$') { $properties[$Matches[1]] = $Matches[2] }
}
if ($properties["enable-rcon"] -ne "true") { throw "RCON is not enabled in $propertiesPath" }
$port = [int]$properties["rcon.port"]
$password = $properties["rcon.password"]
if ($port -lt 1 -or [string]::IsNullOrWhiteSpace($password)) { throw "RCON port or password is not configured." }

$client = New-Object Net.Sockets.TcpClient
try {
    $connection = $client.BeginConnect($HostName, $port, $null, $null)
    if (-not $connection.AsyncWaitHandle.WaitOne($ConnectTimeoutMilliseconds)) {
        throw "Timed out connecting to Paper RCON at ${HostName}:$port. Restart Paper once if RCON was just configured."
    }
    try {
        $client.EndConnect($connection)
    } catch {
        throw "Could not connect to Paper RCON at ${HostName}:$port. Restart Paper once if RCON was just configured. $($_.Exception.InnerException.Message)"
    }
    $client.ReceiveTimeout = $ConnectTimeoutMilliseconds
    $client.SendTimeout = $ConnectTimeoutMilliseconds
    $stream = $client.GetStream()

    Send-Packet $stream 1 3 $password
    $auth = Receive-Packet $stream
    if ($auth.Type -ne 2) { $auth = Receive-Packet $stream }
    if ($auth.RequestId -eq -1) { throw "Paper rejected the RCON password." }
    if ($auth.RequestId -ne 1 -or $auth.Type -ne 2) { throw "Paper returned an unexpected RCON authentication response." }

    Send-Packet $stream 2 2 $Command
    if ($Command -eq "stop") {
        Write-Host "Paper accepted the RCON stop command."
        return
    }
    $response = Receive-Packet $stream
    if ($response.RequestId -ne 2) { throw "Paper returned an unexpected RCON command response." }
    $response.Body
} finally {
    $client.Dispose()
}
