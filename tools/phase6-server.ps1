param([string]$Run='baseline0',[string]$Clone='E:\MinecraftServer-ImmersivePortals-Test')
$ErrorActionPreference='Stop'
if($Run -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Invalid run label'}
$clone=[IO.Path]::GetFullPath($Clone)
if($clone-notlike'E:\MinecraftServer-ImmersivePortals-Test*'){throw 'Unexpected test clone path'}
$evidence='E:\ImmersivePortalsMod\build\phase6-inspection'
if(!(Test-Path "$clone\.immersive-portals-phase6-test-clone")){throw 'Unmarked clone'}
$properties=[IO.File]::ReadAllText("$clone\server.properties")
if($properties -notmatch '(?m)^server-ip=127\.0\.0\.1\s*$' -or $properties -notmatch '(?m)^server-port=25681\s*$'){throw 'Unsafe test bind'}
$psi=[Diagnostics.ProcessStartInfo]::new('E:\MinecraftServer\bin\java.exe')
$psi.WorkingDirectory=$clone
$psi.UseShellExecute=$false
$psi.CreateNoWindow=$true
$psi.RedirectStandardInput=$true
$psi.RedirectStandardOutput=$true
$psi.RedirectStandardError=$true
foreach($arg in @('-Xms512M','-Xmx4G','-jar','fabric-server-mc.26.3-loader.0.19.5-launcher.1.1.2.jar','nogui')){$psi.ArgumentList.Add($arg)}
$process=[Diagnostics.Process]::new();$process.StartInfo=$psi
$started=[DateTime]::UtcNow
if(!$process.Start()){throw 'Test JVM did not start'}
$output=[IO.FileStream]::new("$evidence\$Run.stdout.log",[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::ReadWrite)
$errorOutput=[IO.FileStream]::new("$evidence\$Run.stderr.log",[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::ReadWrite)
$stdoutTask=$process.StandardOutput.BaseStream.CopyToAsync($output)
$stderrTask=$process.StandardError.BaseStream.CopyToAsync($errorOutput)
$seq=0
$commandPath="$evidence\console-command.json"
if(Test-Path $commandPath){$seq=([IO.File]::ReadAllText($commandPath) | ConvertFrom-Json).seq}
@{pid=$process.Id;helperPid=$PID;run=$Run;startedUtc=$started.ToString('O');clone=$clone} | ConvertTo-Json | Set-Content "$evidence\server-process.json"
Write-Output ('Test server JVM PID '+$process.Id+'; console control: '+$commandPath)
try {
    while(!$process.HasExited){
        if(Test-Path $commandPath){
            try {$request=[IO.File]::ReadAllText($commandPath) | ConvertFrom-Json}catch{$request=$null}
            if($request -and $request.seq -gt $seq){
                $seq=$request.seq
                $process.StandardInput.WriteLine([string]$request.command)
                $process.StandardInput.Flush()
                @{seq=$seq;command=$request.command;sentUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content "$evidence\console-sent.json"
            }
        }
        Start-Sleep -Milliseconds 200
    }
    $stdoutTask.GetAwaiter().GetResult();$stderrTask.GetAwaiter().GetResult()
    @{run=$Run;pid=$process.Id;exitCode=$process.ExitCode;seconds=([DateTime]::UtcNow-$started).TotalSeconds} |
        ConvertTo-Json | Set-Content "$evidence\$Run-exit.json"
    Write-Output ('Test JVM exited: '+$process.ExitCode)
}finally{
    if(!$process.HasExited){$process.StandardInput.WriteLine('stop');$process.StandardInput.Flush();$process.WaitForExit(30000) | Out-Null}
    if($process.HasExited){$output.Dispose();$errorOutput.Dispose();$process.Dispose()}
}
