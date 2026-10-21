$ErrorActionPreference='Stop'
$clone='E:\MinecraftServer-ImmersivePortals-Test-Current'
if(!(Test-Path "$clone\.immersive-portals-phase6-test-clone")){throw 'Unmarked current clone'}
foreach($port in 25681,25682){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw 'Test port occupied'};if(Get-NetUDPEndpoint -LocalPort $port -ErrorAction SilentlyContinue){throw 'Test UDP port occupied'}}
function Setting($file,$name,$value,$separator='='){
 $text=[IO.File]::ReadAllText($file);$pattern='(?m)^'+[regex]::Escape($name)+'\s*'+[regex]::Escape($separator)+'.*$'
 if($text-notmatch$pattern){throw "Missing setting $name"};[IO.File]::WriteAllText($file,[regex]::Replace($text,$pattern,($name+$separator+$value)))
}
Setting "$clone\server.properties" 'server-ip' '127.0.0.1'
Setting "$clone\server.properties" 'server-port' '25681'
Setting "$clone\config\voicechat\voicechat-server.properties" 'port' '25682'
Setting "$clone\config\voicechat\voicechat-server.properties" 'bind_address' '127.0.0.1'
Setting "$clone\config\voicechat\voicechat-server.properties" 'voice_host' '127.0.0.1:25682'
foreach($key in 'bind-address','advertised-endpoint-host'){Setting "$clone\automodpack\server.conf" $key ' "127.0.0.1"' ':'}
foreach($key in 'bind-port','advertised-endpoint-port'){Setting "$clone\automodpack\server.conf" $key ' 25681' ':'}
$config=[IO.File]::ReadAllText("$clone\automodpack\server.conf")
if($config-notmatch'(?m)^\s*exclude:\s*\['){throw 'Cannot find test exclusions'}
$config=[regex]::Replace($config,'(?m)^(\s*exclude:)\s*\[','$1 ["mods/immersive-portals-phase6-driver.jar",')
[IO.File]::WriteAllText("$clone\automodpack\server.conf",$config)
$candidate='E:\ImmersivePortalsMod\build\libs\immersive-portals-6.0.6-mc26.3-fabric.jar'
$authorized=Get-Content build/phase6-inspection/authorized-candidate.json -Raw|ConvertFrom-Json
if((Get-FileHash $candidate).Hash-ne$authorized.sha256){throw 'Unexpected candidate'}
Copy-Item $candidate "$clone\mods"
Copy-Item build/libs/immersive-portals-phase6-driver.jar "$clone\mods"
@{clone=$clone;candidateSha256=$authorized.sha256;loopbackTcp=25681;loopbackVoiceUdp=25682;initialOnlineMode=$true;testDriverExcluded=$true}|ConvertTo-Json|Set-Content build/phase6-current-inspection/isolation.json
