param([ValidateSet('server','clientA','clientB')][string]$Target,[string]$Action,[string]$ExtraJson='{}',[int]$TimeoutSeconds=20)
$ErrorActionPreference='Stop'
$root='E:\ImmersivePortalsMod\build\phase6-inspection'
$path=Join-Path $root "$Target-command.json"
$seq=1
if(Test-Path -LiteralPath $path){$seq=([IO.File]::ReadAllText($path) | ConvertFrom-Json).seq+1}
$command=[ordered]@{seq=$seq;action=$Action}
$extra=$ExtraJson | ConvertFrom-Json -AsHashtable
foreach($entry in $extra.GetEnumerator()){$command[$entry.Key]=$entry.Value}
$temp=$path+'.control.tmp'
$command | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $temp
[IO.File]::Move($temp,$path,$true)
$until=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
while([DateTime]::UtcNow -lt $until){
    $response=Join-Path $root "$Target-response.json"
    if(Test-Path -LiteralPath $response){
        try {$reply=[IO.File]::ReadAllText($response) | ConvertFrom-Json}catch{$reply=$null}
        if($reply -and $reply.seq -eq $seq){
            if($reply.status -ne 'PASS'){throw ($reply | ConvertTo-Json -Compress)}
            Write-Output "$Target $Action acknowledged ($seq)";return
        }
    }
    Start-Sleep -Milliseconds 100
}
throw "Timed out waiting for $Target request $seq"
