param(
    [Parameter(Mandatory)][int]$Port,
    [Parameter(Mandatory)][string]$Password
)
$ErrorActionPreference = 'Stop'
$socket = [Net.Sockets.TcpClient]::new()
$socket.ReceiveTimeout = 5000
$socket.SendTimeout = 5000
try {
    $socket.Connect('127.0.0.1', $Port)
    $stream = $socket.GetStream()
    function Send-Rcon([int]$Id, [int]$Type, [string]$Body) {
        $payload = [Text.Encoding]::UTF8.GetBytes($Body)
        $length = 10 + $payload.Length
        $bytes = [byte[]]::new(4 + $length)
        [BitConverter]::GetBytes($length).CopyTo($bytes, 0)
        [BitConverter]::GetBytes($Id).CopyTo($bytes, 4)
        [BitConverter]::GetBytes($Type).CopyTo($bytes, 8)
        $payload.CopyTo($bytes, 12)
        $stream.Write($bytes, 0, $bytes.Length)
    }
    function Read-RconId {
        $header = [byte[]]::new(4)
        $read = 0
        while ($read -lt 4) { $n = $stream.Read($header, $read, 4 - $read); if (!$n) { throw 'RCON closed early' }; $read += $n }
        $length = [BitConverter]::ToInt32($header, 0)
        if ($length -lt 10 -or $length -gt 4096) { throw 'Invalid RCON response length' }
        $body = [byte[]]::new($length)
        $read = 0
        while ($read -lt $length) { $n = $stream.Read($body, $read, $length - $read); if (!$n) { throw 'RCON closed early' }; $read += $n }
        return [BitConverter]::ToInt32($body, 0)
    }
    Send-Rcon 537 3 $Password
    if ((Read-RconId) -ne 537) { throw 'RCON authentication failed' }
    Send-Rcon 538 2 'stop'
} finally {
    $socket.Dispose()
}
