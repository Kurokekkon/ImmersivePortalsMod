$ErrorActionPreference='Stop'
$root='E:\ImmersivePortalsMod\build\phase6-inspection'
function S($command){ & "$PSScriptRoot\phase6-control.ps1" -Target server -Action command -ExtraJson (@{command=$command}|ConvertTo-Json -Compress) }
function C($role,$action,$extra=@{}){ & "$PSScriptRoot\phase6-control.ps1" -Target "client$role" -Action $action -ExtraJson ($extra|ConvertTo-Json -Compress) }
function State($role){Get-Content "$root\client$role-state.json" -Raw|ConvertFrom-Json}
function Wait-State($role,$predicate){
 $until=[DateTime]::UtcNow.AddSeconds(20)
 do { $s=State $role; if(&$predicate $s){return $s}; Start-Sleep -Milliseconds 100 }while([DateTime]::UtcNow-lt$until)
 throw "Client $role did not reach expected state: $($s|ConvertTo-Json -Compress)"
}
function Record($name){
 $a=State A;$b=State B;$server=Get-Content "$root\server-state.json" -Raw|ConvertFrom-Json
 foreach($role in 'A','B'){
  $p=$server.players."Phase6$role";if($p.copies-ne1){throw "Duplicate/missing Phase6$role"}
  if(!(State $role).connected){throw "Client disconnected"}
 }
 @{A=$a;B=$b;server=$server}|ConvertTo-Json -Depth 30|Set-Content "$root\portal-$name.json"
}
S 'gamemode creative Phase6B'
S 'tp Phase6B 0.5 201 6.5 180 0'
S 'tp Phase6A -1.5 201 1.5 180 0'
Wait-State A {param($s) $s.x -lt 0 -and $s.x -gt -3 -and $s.z -gt 1}|Out-Null
try { C A walk @{enabled=$true};Wait-State A {param($s)$s.x -gt 25}|Out-Null }finally{C A walk @{enabled=$false}}
Record 'same-traversal'
C A screenshot @{name='same-after-traversal'}
& "$PSScriptRoot\phase6-control.ps1" -Target server -Action fixture -ExtraJson '{"mode":"rotated"}'
S 'tp Phase6A -1.5 201 1.5 180 0'
Wait-State A {param($s)$s.x -lt 0}|Out-Null
C A screenshot @{name='rotated-before'}
try {C A walk @{enabled=$true};Wait-State A {param($s)$s.x -gt 25}|Out-Null}finally{C A walk @{enabled=$false}}
Record 'rotated-traversal'
foreach($mode in 'nether','chronodawn'){
 & "$PSScriptRoot\phase6-control.ps1" -Target server -Action fixture -ExtraJson (@{mode=$mode}|ConvertTo-Json -Compress)
 S 'execute in minecraft:overworld run tp Phase6A 0.5 201 1.5 180 0'
 S 'execute in minecraft:overworld run tp Phase6B 0.5 201 3.5 180 0'
 Wait-State A {param($s)$s.dimension-eq'minecraft:overworld' -and $s.z -gt 1}|Out-Null
 Start-Sleep -Seconds 2
 C A screenshot @{name="$mode-before"}
 $dim=if($mode-eq'nether'){'minecraft:the_nether'}else{'chronodawn:chronodawn'}
 try{C A walk @{enabled=$true};Wait-State A {param($s)$s.dimension-eq$dim}|Out-Null}finally{C A walk @{enabled=$false}}
 Record "$mode-A-crossed-B-stays"
 try{C B walk @{enabled=$true};Wait-State B {param($s)$s.dimension-eq$dim}|Out-Null}finally{C B walk @{enabled=$false}}
 Record "$mode-both-crossed"
 C A screenshot @{name="$mode-destination"}
 foreach($role in 'A','B'){
  try{C $role walk @{enabled=$true};Wait-State $role {param($s)$s.dimension-eq'minecraft:overworld'}|Out-Null}finally{C $role walk @{enabled=$false}}
 }
 Record "$mode-both-returned"
}
Copy-Item "$root\chunk-context-probe.json" "$root\chunk-context-fixed-success.json"
Write-Output 'Core full-pack portal traversal and two-client roundtrips PASS'
