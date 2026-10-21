param([ValidateSet('A','B')][string]$Role='A',[string]$Run='bootstrap')
$ErrorActionPreference='Stop'
if($Run -notmatch '^[A-Za-z0-9_-]+$'){throw 'Bad run name'}
$evidence='E:\ImmersivePortalsMod\build\phase6-inspection'
$config=Get-Content "$evidence\client$Role-launch.json" -Raw | ConvertFrom-Json
if(!(Test-Path "$($config.profile)\.phase6-client")){throw 'Unmarked client'}
$psi=[Diagnostics.ProcessStartInfo]::new('E:\MinecraftServer\bin\java.exe')
$psi.WorkingDirectory=$config.profile;$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
$psi.ArgumentList.Add('@'+$config.argfile)
$p=[Diagnostics.Process]::new();$p.StartInfo=$psi
$p.Start() | Out-Null
$out=[IO.FileStream]::new("$evidence\client$Role-$Run.stdout.log",[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::ReadWrite)
$err=[IO.FileStream]::new("$evidence\client$Role-$Run.stderr.log",[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::ReadWrite)
$a=$p.StandardOutput.BaseStream.CopyToAsync($out);$b=$p.StandardError.BaseStream.CopyToAsync($err)
@{pid=$p.Id;role=$Role;run=$Run;profile=$config.profile} | ConvertTo-Json | Set-Content "$evidence\client$Role-process.json"
Write-Output "Disposable client $Role PID $($p.Id)"
$p.WaitForExit();$a.GetAwaiter().GetResult();$b.GetAwaiter().GetResult()
@{pid=$p.Id;exitCode=$p.ExitCode;role=$Role;run=$Run} | ConvertTo-Json | Set-Content "$evidence\client$Role-$Run-exit.json"
$out.Dispose();$err.Dispose();$p.Dispose()
