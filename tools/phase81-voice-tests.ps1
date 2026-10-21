$ErrorActionPreference='Stop'
$root='E:\ImmersivePortalsMod\build\phase6-inspection'
function Client([string]$Action,[hashtable]$Data=@{}){& "$PSScriptRoot/phase6-control.ps1" -Target clientA -Action $Action -ExtraJson ($Data|ConvertTo-Json -Compress)|Out-Host}
function Server([string]$Action,[hashtable]$Data=@{}){& "$PSScriptRoot/phase6-control.ps1" -Target server -Action $Action -ExtraJson ($Data|ConvertTo-Json -Compress)|Out-Host}
function VoiceCase([string]$Name,[int]$Yaw,[bool]$Third,[bool]$Visible){
 Client phase81-options @{fps=60;vsync=$false;thirdPerson=$Third}
 Client look @{yaw=$Yaw;pitch=0};Start-Sleep -Milliseconds 700
 Client phase81-start @{name=$Name;audio=$true};Start-Sleep -Milliseconds 2200
 Client phase81-stop @{name=$Name}
 $r=Get-Content "$root/$Name.json" -Raw|ConvertFrom-Json
 if($r.errors.Count-or$r.cameraUpdates-lt20-or$r.portalCameraSamples-ne0-or$r.staleDraws-ne0){throw "Failed camera/render assertion: $Name"}
 if(@($r.audioSamples|Where-Object {$_.error-or!$_.speakerFound-or$_.normalReturnBeforeSpeaker-or$_.lookupWorld-ne$_.playerWorld}).Count){throw "Failed speaker lookup: $Name"}
 if($Visible-and!@($r.audioSamples|Where-Object portalRender).Count){throw "No visible portal sampled in $Name"}
 if(!$Visible-and@($r.audioSamples|Where-Object portalRender).Count){throw "Portal unexpectedly visible in $Name"}
 $distance=$r.audioSamples|Measure-Object distance -Minimum -Maximum
 $volume=$r.audioSamples|Measure-Object attenuation -Minimum -Maximum
 if($distance.Maximum-$distance.Minimum-gt0.00001-or$volume.Maximum-$volume.Minimum-gt0.00001){throw "Unstable attenuation in $Name"}
 [PSCustomObject]@{case=$Name;status='PASS';cameraUpdates=$r.cameraUpdates;cameraJumps=$r.portalCameraSamples;samples=$r.audioSamples.Count;portalSamples=@($r.audioSamples|Where-Object portalRender).Count;speakerFailures=0;distanceRange=@($distance.Minimum,$distance.Maximum);attenuationRange=@($volume.Minimum,$volume.Maximum);draws=$r.draws;staleDraws=$r.staleDraws;cameraType=$r.cameraType}
}
function Cross([string]$Name,[int]$Yaw,[string]$Dimension){
 Client phase81-options @{fps=60;vsync=$false;thirdPerson=$false};Client look @{yaw=$Yaw;pitch=0}
 Client phase81-start @{name=$Name;audio=$false}
 try{
  Client walk @{enabled=$true};$until=[DateTime]::UtcNow.AddSeconds(10)
  do{$s=Get-Content "$root/clientA-state.json" -Raw|ConvertFrom-Json;if($s.dimension-eq$Dimension){break};Start-Sleep -Milliseconds 60}while([DateTime]::UtcNow-lt$until)
  if($s.dimension-ne$Dimension){throw "Traversal failed: $Name"}
 }finally{Client walk @{enabled=$false}}
 Start-Sleep -Milliseconds 600;Client phase81-stop @{name=$Name}
 $r=Get-Content "$root/$Name.json" -Raw|ConvertFrom-Json
 if($r.errors.Count-or$r.portalCameraSamples-ne0-or$r.staleDraws-ne0){throw "Listener/camera traversal regression: $Name"}
 [PSCustomObject]@{case=$Name;status='PASS';dimension=$Dimension;cameraUpdates=$r.cameraUpdates;cameraJumps=0;listenerWorlds=@($r.listenerSamples.world|Sort-Object -Unique);draws=$r.draws;staleDraws=$r.staleDraws}
}
$state=Get-Content "$root/clientA-state.json" -Raw|ConvertFrom-Json
if(!$state.connected){throw 'Disposable client not connected'}
Server phase81-fixture @{mode='obsidian'}
Start-Sleep -Seconds 2
$summary=@()
$summary+=VoiceCase 'phase81-fixed-voice-hidden' 0 $false $false
$summary+=VoiceCase 'phase81-fixed-voice-visible' 180 $false $true
$summary+=VoiceCase 'phase81-fixed-voice-third-person' 180 $true $true
$summary+=Cross 'phase81-fixed-cross-to-nether' 180 'minecraft:the_nether'
$summary+=VoiceCase 'phase81-fixed-voice-after-cross' 0 $false $true
$summary+=Cross 'phase81-fixed-cross-return' 0 'minecraft:overworld'
$summary|ConvertTo-Json -Depth 8|Set-Content E:\ImmersivePortalsMod\build\phase81-inspection\voice-regression-summary.json
$summary|ConvertTo-Json -Depth 6
