param(
    [string]$Clone='E:\MinecraftServer-ImmersivePortals-Test',
    [string]$ProfileRoot='E:\ImmersivePortalsMod\build\phase6'
)
$ErrorActionPreference='Stop'
$repo='E:\ImmersivePortalsMod'
$clone=[IO.Path]::GetFullPath($Clone)
$evidence="$repo\build\phase6-inspection"
$profileRoot=[IO.Path]::GetFullPath($ProfileRoot)
$gradleUserHome=if($env:GRADLE_USER_HOME){$env:GRADLE_USER_HOME}else{Join-Path $env:USERPROFILE '.gradle'}
$loomCache=Join-Path $gradleUserHome 'caches\fabric-loom'
if($profileRoot-notlike"$repo\build\phase6*"){throw 'Unexpected disposable profile root'}
if(!(Test-Path "$clone\.immersive-portals-phase6-test-clone")){throw 'Unmarked server clone'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
# Reuse only installed game/loader/library jars, never development mod output.
$oldArgs=@(Get-Content "$repo\build\phase2\mp06\A.args" | ForEach-Object {
    $line=$_;if($line.StartsWith('"') -and $line.EndsWith('"')){$line=$line.Substring(1,$line.Length-2)}
    $line.Replace('\\','\').Replace('\"','"')
})
$index=[Array]::IndexOf($oldArgs,'-cp')
if($index -lt 0){throw 'Cached client classpath unavailable'}
$classpath=@()
foreach($entry in $oldArgs[$index+1].Split(';')){
    if(!(Test-Path -LiteralPath $entry -PathType Leaf) -or !$entry.EndsWith('.jar')){continue}
    if($entry -match '\\dimlib\\|\\processIncludeJars\\|dev-launch-injector|fabric-loom-native|mixinextras'){continue}
    $zip=[IO.Compression.ZipFile]::OpenRead($entry)
    try {$isMod=$null -ne $zip.GetEntry('fabric.mod.json')}finally{$zip.Dispose()}
    if($isMod -and $entry -notmatch 'fabric-loader'){continue}
    if($entry -match 'minecraft-merged.*\.jar$'){$entry=Join-Path $loomCache '26.3\minecraft-client.jar'}
    $classpath+=$entry
}
if(!($classpath | Where-Object {$_ -match '26.3\\minecraft-client\.jar$'})){throw 'Vanilla Minecraft client jar missing'}
if(!($classpath | Where-Object {$_ -match 'fabric-loader.*\.jar$'})){throw 'Fabric loader jar missing'}
$classpath | Set-Content "$evidence\client-library-classpath.txt"
$projection=Get-Content "$clone\automodpack\server\current-projection.json" -Raw | ConvertFrom-Json
$cert=[Security.Cryptography.X509Certificates.X509Certificate2]::new("$clone\automodpack\credentials\certificate.crt")
$pin=$cert.GetCertHashString([Security.Cryptography.HashAlgorithmName]::SHA256)
$cert.Dispose()
# Only newly generated disposable credentials, never production credentials. Never log the secret.
$provisioning=[IO.File]::ReadAllText("$clone\automodpack\credentials\provisioning-secret").Trim()
foreach($role in @('A','B')){
    $profile="$profileRoot\client$role"
    if(Test-Path -LiteralPath $profile){throw "Existing client profile collision: $profile"}
    New-Item -ItemType Directory -Force "$profile\mods","$profile\automodpack" | Out-Null
    'Disposable Phase 6 profile' | Set-Content "$profile\.phase6-client"
    Copy-Item -LiteralPath "$clone\mods\automodpack-5.0.0-rc.4.jar" -Destination "$profile\mods"
    @{origin='127.0.0.1:25681';endpoint='127.0.0.1:25681';connectionMode='HOLEPUNCH';modpackId=$projection.policy.modpackId;
        fingerprint=$pin;secret=$provisioning;serverName='Disposable Las Ratingas Phase 6'} |
        ConvertTo-Json | Set-Content "$profile\automodpack\automodpack-bootstrap.json"
    $user="Phase6$role"
    # Offline UUID uses Java's MD5 name UUID algorithm; scoped to the offline clone.
    $bytes=[Security.Cryptography.MD5]::HashData([Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$user"))
    $bytes[6]=($bytes[6] -band 15) -bor 48;$bytes[8]=($bytes[8] -band 63) -bor 128
    $hex=[Convert]::ToHexString($bytes).ToLowerInvariant()
    $uuid=$hex.Substring(0,8)+'-'+$hex.Substring(8,4)+'-'+$hex.Substring(12,4)+'-'+$hex.Substring(16,4)+'-'+$hex.Substring(20)
    $args=@('-Xms256M','-Xmx2G','-XX:ActiveProcessorCount=3','--enable-native-access=ALL-UNNAMED','--sun-misc-unsafe-memory-access=allow',
        '-Dfabric.noGui=true',"-Dimm_ptl.phase6.role=$role",'-cp',($classpath -join ';'),'net.fabricmc.loader.impl.launch.knot.KnotClient',
        '--username',$user,'--uuid',$uuid,'--accessToken','0','--version','26.3','--userType','legacy',
        '--gameDir',$profile,'--assetIndex','26.3-34','--assetsDir',(Join-Path $loomCache 'assets'),'--width','854','--height','480')
    $args | ForEach-Object {'"'+$_.Replace('\','\\').Replace('"','\"')+'"'} | Set-Content "$evidence\client$role.args"
    @{profile=$profile;username=$user;uuid=$uuid;role=$role;argfile="$evidence\client$role.args"} | ConvertTo-Json | Set-Content "$evidence\client$role-launch.json"
}
Write-Output 'Two fresh AutoModpack bootstrap profiles prepared; no normal launcher profile changed.'
