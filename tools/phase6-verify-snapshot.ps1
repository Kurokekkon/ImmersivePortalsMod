param(
 [string]$Id='rp-20261019-135027-edca04cd-1e43-4fce-871e-28b07b1173f6',
 [string]$Clone='E:\MinecraftServer-ImmersivePortals-Test',
 [string]$EvidenceRoot='build/phase6-inspection'
)
$ErrorActionPreference='Stop'
if($Id-notmatch '^rp-[A-Za-z0-9-]+$'){throw 'Invalid snapshot id'}
$clone=[IO.Path]::GetFullPath($Clone)
if(!$clone.StartsWith('E:\MinecraftServer-ImmersivePortals-Test',[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected clone path'}
$evidence=[IO.Path]::GetFullPath($EvidenceRoot)
if(!$evidence.StartsWith('E:\ImmersivePortalsMod\build\phase6',[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected evidence path'}
New-Item -ItemType Directory -Force -Path $evidence|Out-Null
if(!(Test-Path "$clone\.immersive-portals-phase6-test-clone")){throw 'Unmarked clone'}
$snapshot=[IO.Path]::GetFullPath("$clone\Restore Points\$Id")
$manifest=Get-Content "$snapshot\restorepoint-manifest.json" -Raw|ConvertFrom-Json
if($manifest.validationStatus-ne'VALIDATED'){throw 'Snapshot not validated'}
$errors=@();$bytes=0L
foreach($entry in $manifest.files){
 $path=[IO.Path]::GetFullPath((Join-Path $snapshot $entry.path))
 if(!$path.StartsWith($snapshot+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Manifest escapes snapshot'}
 if(!(Test-Path -LiteralPath $path -PathType Leaf)){$errors+="Missing $($entry.path)";continue}
 if((Get-Item -LiteralPath $path).Length-ne$entry.size-or(Get-FileHash -LiteralPath $path).Hash-ine$entry.sha256){$errors+="Mismatch $($entry.path)"}
 $bytes+=$entry.size
}
@{id=$Id;files=$manifest.files.Count;bytes=$bytes;errors=$errors;status=if($errors.Count){'FAIL'}else{'PASS'}}|ConvertTo-Json -Depth 5|Set-Content "$evidence/snapshot-hashes.json"
if($errors.Count){throw ($errors-join'; ')}
& E:/MinecraftServer/bin/java.exe tools/Phase6SnapshotCheck.java $snapshot *> "$evidence/snapshot-portal-validation.log"
if($LASTEXITCODE){throw 'Portal NBT validation failed'}
Write-Output "Validated all $($manifest.files.Count) snapshot hashes plus portal/player/dimension NBT"
