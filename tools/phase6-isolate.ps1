$ErrorActionPreference='Stop'
$clone='E:\MinecraftServer-ImmersivePortals-Test'
$evidence='E:\ImmersivePortalsMod\build\phase6-inspection'
if(!(Test-Path "$clone\.immersive-portals-phase6-test-clone")){throw 'Unmarked clone'}
$ports=@(25681,25682)
foreach($port in $ports){
    if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "TCP port occupied: $port"}
    if(Get-NetUDPEndpoint -LocalPort $port -ErrorAction SilentlyContinue){throw "UDP port occupied: $port"}
}
function Replace-Setting($path,$key,$value,$separator='=') {
    $text=[IO.File]::ReadAllText($path)
    $pattern='(?m)^'+[regex]::Escape($key)+'\s*'+[regex]::Escape($separator)+'.*$'
    if($text -notmatch $pattern){throw "Missing setting $key in $path"}
    [IO.File]::WriteAllText($path,[regex]::Replace($text,$pattern,($key+$separator+$value)))
}
Replace-Setting "$clone\server.properties" 'server-ip' '127.0.0.1'
Replace-Setting "$clone\server.properties" 'server-port' '25681'
Replace-Setting "$clone\config\voicechat\voicechat-server.properties" 'port' '25682'
Replace-Setting "$clone\config\voicechat\voicechat-server.properties" 'bind_address' '127.0.0.1'
Replace-Setting "$clone\config\voicechat\voicechat-server.properties" 'voice_host' '127.0.0.1:25682'
Replace-Setting "$clone\automodpack\server.conf" 'bind-address' ' "127.0.0.1"' ':'
Replace-Setting "$clone\automodpack\server.conf" 'bind-port' ' 25681' ':'
Replace-Setting "$clone\automodpack\server.conf" 'advertised-endpoint-host' ' "127.0.0.1"' ':'
Replace-Setting "$clone\automodpack\server.conf" 'advertised-endpoint-port' ' 25681' ':'
$candidate='E:\ImmersivePortalsMod\build\libs\immersive-portals-6.0.6-mc26.3-fabric.jar'
$expected='10DA74A2147845912957D876C9AE85CA9D19F3651A90C611C086FF321D4EC796'
if((Get-FileHash $candidate).Hash -ne $expected){throw 'Unexpected Phase 5 candidate hash'}
$destination=Join-Path "$clone\mods" ([IO.Path]::GetFileName($candidate))
if(Test-Path $destination){throw 'Candidate already installed; refusing overwrite'}
Copy-Item -LiteralPath $candidate -Destination $destination
if((Get-FileHash $destination).Hash -ne $expected){throw 'Copied candidate hash mismatch'}
@{minecraftBind='127.0.0.1:25681';voiceBind='127.0.0.1:25682 UDP';autoModpack='HOLEPUNCH on loopback Minecraft listener; advertised 127.0.0.1:25681';
    resourcefulConfigWeb='disabled in unchanged production-equivalent config';onlineMode='true for initial baseline';
    candidate=$destination;sha256=$expected;dependency='DimLib is nested; Fabric API/Cloth Config already present; no duplicate dependency added';
    exclusions='Unchanged test copy; Immersive Portals is not server-only/excluded'} |
    ConvertTo-Json -Depth 4 | Set-Content "$evidence\isolation.json"
Write-Output 'Loopback isolation configured; Phase 5 candidate installed and hash verified in clone only.'
