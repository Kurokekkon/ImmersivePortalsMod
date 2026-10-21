param(
    [string]$CloneRoot='E:\MinecraftServer-ImmersivePortals-Test',
    [string]$EvidenceRoot='E:\ImmersivePortalsMod\build\phase6-inspection'
)
$ErrorActionPreference='Stop'
$sourceRoot='E:\MinecraftServer'
$cloneRoot=[IO.Path]::GetFullPath($CloneRoot)
$evidenceRoot=[IO.Path]::GetFullPath($EvidenceRoot)
if($cloneRoot-notlike'E:\MinecraftServer-ImmersivePortals-Test*'){throw 'Unexpected disposable clone path'}
if($evidenceRoot-notlike'E:\ImmersivePortalsMod\build\phase6*'){throw 'Unexpected evidence directory'}
New-Item -ItemType Directory -Force $evidenceRoot | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-ProductionStopped {
    $servers=Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'fabric-server|net\.minecraft\.server|KnotServer|server\.jar'
    }
    if($servers){throw 'A dedicated Minecraft JVM is running; do not copy live world data.'}
    if(Get-NetTCPConnection -State Listen -LocalPort 25565 -ErrorAction SilentlyContinue){throw 'Production Minecraft port is listening.'}
}
Assert-ProductionStopped
if(Test-Path -LiteralPath $cloneRoot){throw 'Clone path already exists; refusing replacement. Inspect its Phase 6 marker before any reuse.'}

function Read-Mod($file,$category) {
    $zip=[IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        $entry=$zip.GetEntry('fabric.mod.json')
        if(!$entry){return [pscustomobject]@{category=$category;file=$file.Name;id=$null;version=$null;environment='UNKNOWN';size=$file.Length;sha256=(Get-FileHash $file.FullName).Hash}}
        $reader=[IO.StreamReader]::new($entry.Open())
        try {$meta=$reader.ReadToEnd() | ConvertFrom-Json}finally{$reader.Dispose()}
        return [pscustomobject]@{category=$category;file=$file.Name;id=$meta.id;version=$meta.version;environment=$meta.environment;
            size=$file.Length;sha256=(Get-FileHash $file.FullName).Hash;depends=$meta.depends;breaks=$meta.breaks;
            nested=@($meta.jars | ForEach-Object file)}
    }finally{$zip.Dispose()}
}
$inventory=@(Get-ChildItem "$sourceRoot\mods" -File -Filter '*.jar' | ForEach-Object {Read-Mod $_ 'server'})
$inventory+=@(Get-ChildItem "$sourceRoot\automodpack\host-modpack\main\mods" -File -Filter '*.jar' | ForEach-Object {Read-Mod $_ 'host-client-only'})
$inventory | ConvertTo-Json -Depth 16 | Set-Content "$evidenceRoot\source-mod-inventory.json"

# Explicit allowlist. Never traverse historical backups, restore transactions or credentials.
$directories=@('world','mods','config','defaultconfigs','libraries','versions','kubejs','resources','resourcepacks','datapacks','emotes','include')
$rootFiles=@('server.properties','eula.txt','whitelist.json','ops.json','banned-players.json','banned-ips.json','usercache.json',
    'server.jar','fabric-server-mc.26.3-loader.0.19.5-launcher.1.1.2.jar')
$files=@()
foreach($name in $directories){if(Test-Path "$sourceRoot\$name"){$files+=@(Get-ChildItem "$sourceRoot\$name" -File -Recurse -Force)}}
foreach($name in $rootFiles){if(Test-Path "$sourceRoot\$name"){$files+=Get-Item "$sourceRoot\$name"}}
$files+=Get-Item "$sourceRoot\automodpack\server.conf"
$files+=@(Get-ChildItem "$sourceRoot\automodpack\host-modpack" -File -Recurse -Force)
$omitted=@($files | Where-Object {$_.Name -match '\.(tmp|bak|disabled)$'})
$files=@($files | Where-Object {$_.Name -notmatch '\.(tmp|bak|disabled)$'})
$before=@(foreach($f in $files){[pscustomobject]@{path=[IO.Path]::GetRelativePath($sourceRoot,$f.FullName);length=$f.Length;lastWriteUtc=$f.LastWriteTimeUtc.ToString('O');sha256=(Get-FileHash -LiteralPath $f.FullName).Hash}})
$before | ConvertTo-Json -Depth 4 | Set-Content "$evidenceRoot\source-runtime-manifest.json"
Assert-ProductionStopped
New-Item -ItemType Directory $cloneRoot | Out-Null
@{workflow='Immersive Portals Phase 6';source=$sourceRoot;createdUtc=[DateTime]::UtcNow.ToString('O');worldSource='stopped production; SHA256 verified copy'} |
    ConvertTo-Json | Set-Content "$cloneRoot\.immersive-portals-phase6-test-clone"
foreach($item in $before){
    $dest=Join-Path $cloneRoot $item.path
    New-Item -ItemType Directory -Force ([IO.Path]::GetDirectoryName($dest)) | Out-Null
    [IO.File]::Copy((Join-Path $sourceRoot $item.path),$dest,$false)
}
Assert-ProductionStopped
foreach($item in $before){
    $original=Join-Path $sourceRoot $item.path
    $copy=Join-Path $cloneRoot $item.path
    if((Get-FileHash -LiteralPath $original).Hash -ne $item.sha256 -or (Get-FileHash -LiteralPath $copy).Hash -ne $item.sha256){throw ('Source/copy consistency mismatch: '+$item.path)}
}
$afterWorld=@(Get-ChildItem "$sourceRoot\world" -File -Recurse -Force | ForEach-Object {[IO.Path]::GetRelativePath($sourceRoot,$_.FullName)})
if(@(Compare-Object @($before | Where-Object {$_.path -like 'world\*'} | ForEach-Object path) $afterWorld).Count){throw 'World file set changed during copy.'}
@{productionRunning=$false;copiedFiles=$before.Count;copiedBytes=($before | Measure-Object length -Sum).Sum;
    sha256Verified=$true;sourceUnchangedDuringCopy=$true;excludedCategories=@('World Backups','Restore Points','.world-backups-restore','logs','crash-reports','debug','.fabric caches','JDK bin/conf/lib/legal','automodpack credentials/server caches/staging','previous test artifacts','root snapshot manifests/restore markers','firewall scripts and production start.bat');
    excludedTemporaryFiles=@($omitted | ForEach-Object {[IO.Path]::GetRelativePath($sourceRoot,$_.FullName)})} |
    ConvertTo-Json -Depth 5 | Set-Content "$evidenceRoot\clone-copy-result.json"
Write-Output ('Verified stopped-world clone: '+$before.Count+' files; '+[math]::Round(($before | Measure-Object length -Sum).Sum/1GB,2)+' GiB')
