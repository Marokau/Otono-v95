param([string]$Package)
$ErrorActionPreference = 'Stop'
# Windows PowerShell may inherit a PowerShell 7 module path from the calling terminal.
$nativeModules = Join-Path $PSHOME 'Modules'
if (($env:PSModulePath -split ';') -notcontains $nativeModules) { $env:PSModulePath = $nativeModules + ';' + $env:PSModulePath }
Set-StrictMode -Version 2
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Windows.Forms
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$bin = Join-Path $PSScriptRoot 'bin'
$pending = Join-Path $bin 'update-pending.json'
$versionFile = Join-Path $PSScriptRoot 'release-version.txt'
$repo = 'Marokau/Otono-v95'

function Show-Info($message) {
    [void][Windows.Forms.MessageBox]::Show($message,'Otono updates','OK','Information')
}
function Managed-Path([string]$relative) {
    $owned = $relative -match '^(Otono\.cmd|README\.md|AGENTS\.md|\.git(attributes|ignore|modules))$' -or
        $relative -match '^(launcher/(?!bin(?:/|$))|server/(src/|data/(cash|reward|shop)/|\.mvn/)|client/(src/|external/|bin/|\.github/)|docs/).+' -or
        $relative -match '^(server|client)/[a-zA-Z0-9_.-]+$' -or $relative -eq 'server/target/server.jar'
    if (!$owned) { throw "Unmanaged update path: $relative" }
    if ($relative -match '(?i)(^|/)(\.git|\.{1,2}|con|prn|aux|nul|com[1-9]|lpt[1-9])(/|\.|$)|[\\:\x00-\x1f<>|"?*]|[ .](/|$)|//|/$|\.(sqlite3?|db)(-wal|-shm|-journal)?$' -or
        $relative -match '^(server|client)/(data|bin|target|src|external)$') { throw "Invalid update path: $relative" }
    return Safe-Path $relative
}
function Safe-Path([string]$relative) {
    $full=[IO.Path]::GetFullPath((Join-Path $root $relative))
    if (!$full.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)) { throw "Path outside installation: $relative" }
    for ($p=$full; $p -ne $root; $p=[IO.Path]::GetDirectoryName($p)) {
        if (Test-Path -LiteralPath $p) {
            if ((Get-Item -Force -LiteralPath $p).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Linked path in installation: $p" }
        }
    }
    return $full
}
function File-Hash($file) {
    $stream=[IO.File]::OpenRead($file)
    $sha=[Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose();$stream.Dispose() }
}
function Matches($file,$hash,[bool]$textFile) {
    if (![IO.File]::Exists($file)) { return $false }
    if ((File-Hash $file) -eq $hash) { return $true }
    if (!$textFile) { return $false }
    # Git for Windows may check out text files with CRLF.
    $encoding = New-Object Text.UTF8Encoding($false,$true)
    try { $s=$encoding.GetString([IO.File]::ReadAllBytes($file)) } catch { return $false }
    $sha=[Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($encoding.GetBytes($s.Replace([string][char]13+[char]10,[string][char]10)))).Replace('-','').ToLowerInvariant() -eq $hash }
    finally { $sha.Dispose() }
}
function Current-Version {
    if (Test-Path -LiteralPath $versionFile) {
        $v=[IO.File]::ReadAllText($versionFile).Trim()
        if ($v -notmatch '^v\d+\.\d+\.\d+$') { throw 'Invalid installed release version.' }
        return $v
    }
    if ((Matches (Join-Path $root 'server/src/main/java/kinoko/script/custom/JobAdvancement.java') '9689caaa76b5dfcc385bef642eb371a2dac0c9da44adc0fa752a0f56a66c1ebd' $true) -and
        (Matches (Join-Path $root 'launcher/OtonoLauncher.cs') '804c2c5b53036fddd89723c9407354d7e4d35f2a8756aafac651c424ce04e909' $true)) { return 'v0.2.1' }
    throw 'Could not identify this installation. Run the updater from the launcher folder of an Otono release.'
}
function Require-Stopped {
    foreach ($name in 'server','setup') {
        $session=Join-Path $bin "$name-session.txt"
        if (Test-Path -LiteralPath $session) {
            $s=[IO.File]::ReadAllText($session).Split('|')
            $p=Get-Process -Id ([int]$s[0]) -ErrorAction SilentlyContinue
            if ($p -and $p.StartTime.ToUniversalTime().Ticks -eq [long]$s[1]) { throw "Close this installation's $name window, then try again." }
        }
    }
    foreach ($p in Get-Process -Name MapleStory,Kinoko -ErrorAction SilentlyContinue) {
        if ($p.Path -and $p.Path.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Close the game before applying the update.' }
    }
    foreach ($relative in 'game/MapleStory.exe','game/MapleStory/MapleStory.exe') {
        $exe=Safe-Path $relative
        if ([IO.File]::Exists($exe)) {
            try { $f=[IO.File]::Open($exe,'Open','Read','None');$f.Dispose() } catch { throw 'Close this installation''s game before applying the update.' }
        }
    }
    # Also catches a Java server started directly without a launcher session file.
    $jar=Safe-Path 'server/target/server.jar'
    if ([IO.File]::Exists($jar)) {
        try { $f=[IO.File]::Open($jar,'Open','ReadWrite','None');$f.Dispose() } catch { throw 'Close the server before applying the update; its JAR is in use.' }
    }
}
function Destination($rel) {
    if ($rel -cmatch '^game/(MapleStory/)?Kinoko\.(exe|dll)$') { return Safe-Path $rel }
    return Managed-Path $rel
}
function Restore-Update {
    if (!(Test-Path -LiteralPath $pending)) { return }
    $journal=Get-Content -Raw -LiteralPath $pending | ConvertFrom-Json
    if ($journal.id -notmatch '^[a-f0-9]{32}$') { throw 'Invalid update recovery folder.' }
    $work=Safe-Path ("launcher/bin/update-"+$journal.id)
    foreach ($entry in $journal.files) {
        $dest=Destination $entry.path
        if ($entry.index -notmatch '^\d+$') { throw 'Invalid recovery entry.' }
        if ($entry.existed) {
            $backup=Safe-Path ("launcher/bin/update-"+$journal.id+"/old/"+$entry.index)
            if (![IO.File]::Exists($dest) -or (File-Hash $dest) -ne (File-Hash $backup)) { [IO.File]::Copy($backup,$dest,$true) }
        } elseif ([IO.File]::Exists($dest)) { [IO.File]::Delete($dest) }
    }
    [IO.File]::Delete($pending)
    [IO.Directory]::Delete($work,$true)
}
function Apply-Package($archive,$current,$expectedVersion) {
    Require-Stopped
    $id=[guid]::NewGuid().ToString('N')
    $work=Safe-Path ("launcher/bin/update-"+$id)
    [void][IO.Directory]::CreateDirectory((Join-Path $work 'old'))
    $plan=New-Object Collections.ArrayList
    $seen=@{}
    function Plan-File($rel,$source,$old,$new,[bool]$textFile,[bool]$generated) {
        $dest=Destination $rel
        if ($seen.ContainsKey($rel)) { throw "Duplicate update entry: $rel" }
        $seen[$rel]=$true
        if ([IO.Directory]::Exists($dest)) { throw "A folder occupies $rel" }
        $exists=[IO.File]::Exists($dest)
        if ($new -and (Matches $dest $new $textFile)) { return }
        if (!$new -and !$exists) { return }
        if ($exists -and !$generated -and (!$old -or !(Matches $dest $old $textFile))) { throw "Local changes in $rel. Copy your edits aside before updating." }
        $idx=$plan.Count
        if ($exists) { [IO.File]::Copy($dest,(Join-Path $work "old/$idx")) }
        [void]$plan.Add(@{path=$rel;source=$source;index=$idx;existed=$exists})
    }
    try {
        $zip=[IO.Compression.ZipFile]::OpenRead($archive)
        try {
            $reader=New-Object IO.StreamReader($zip.GetEntry('patch.json').Open())
            try { $m=$reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
            if ($m.format -ne 1 -or $m.base -cne $current -or $m.version -notmatch '^v\d+\.\d+\.\d+$' -or
                [version]$m.version.Substring(1) -le [version]$current.Substring(1) -or ($expectedVersion -and $m.version -cne $expectedVersion)) { throw 'This patch does not match the installed release.' }
            $n=0
            foreach ($file in $m.files) {
                $rel=[string]$file.path
                [void](Managed-Path $rel)
                $new=[string]$file.after;$old=[string]$file.before
                if (($new -and $new -notmatch '^[0-9a-f]{64}$') -or ($old -and $old -notmatch '^[0-9a-f]{64}$')) { throw "Invalid file checksum: $rel" }
                $source=$null
                if ($new) {
                    $source=Join-Path $work ("new-"+($n++))
                    $payload=$zip.GetEntry("files/$rel")
                    if (!$payload) { throw "Missing update file: $rel" }
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($payload,$source)
                    if ((File-Hash $source) -ne $new) { throw "Checksum mismatch: $rel" }
                }
                if ($rel -eq 'launcher/release-version.txt' -and (!$new -or [IO.File]::ReadAllText($source).Trim() -cne $m.version)) { throw 'Patch version marker is inconsistent.' }
                if ($rel -eq 'server/target/server.jar' -and !$new) { throw 'Missing server build.' }
                Plan-File $rel $source $old $new ([bool]$file.text) ($rel -eq 'server/target/server.jar')
                if ($rel -cmatch '^client/bin/(Kinoko\.(?:exe|dll))$' -and $new) {
                    $name=$Matches[1];$game='game'
                    if (![IO.File]::Exists((Join-Path $root 'game/MapleStory.exe')) -and [IO.File]::Exists((Join-Path $root 'game/MapleStory/MapleStory.exe'))) { $game='game/MapleStory' }
                    if ([IO.File]::Exists((Join-Path $root "$game/MapleStory.exe"))) { Plan-File "$game/$name" $source $old $new $false $false }
                }
            }
            if (!$seen.ContainsKey('launcher/release-version.txt') -or !$seen.ContainsKey('server/target/server.jar')) { throw 'Incomplete release patch.' }
        } finally { $zip.Dispose() }
        Require-Stopped
        $journal=@{id=$id;files=@($plan | Select-Object -Property @{n='path';e={$_.path}},@{n='index';e={$_.index}},@{n='existed';e={$_.existed}})}
        [IO.File]::WriteAllText((Join-Path $work 'journal.json'),($journal | ConvertTo-Json -Depth 4))
        [IO.File]::Move((Join-Path $work 'journal.json'),$pending)
        try {
            foreach ($entry in $plan) {
                $dest=Destination $entry.path
                if ($entry.source) {
                    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))
                    [IO.File]::Copy($entry.source,$dest,$true)
                    [IO.File]::SetLastWriteTimeUtc($dest,[DateTime]::UtcNow)
                } else { [IO.File]::Delete($dest) }
            }
            [IO.File]::Delete($pending)
        } catch { Restore-Update;throw }
    } finally {
        if (!(Test-Path -LiteralPath $pending) -and [IO.Directory]::Exists($work)) { [IO.Directory]::Delete($work,$true) }
    }
    return $m.version
}
function Online-Update($current) {
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    $headers=@{'User-Agent'='Otono-v95-launcher';Accept='application/vnd.github+json'}
    Write-Host "Checking GitHub for updates to Otono $current..."
    $release=Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest" -Headers $headers -TimeoutSec 20
    $latest=[string]$release.tag_name
    if ($latest -notmatch '^v\d+\.\d+\.\d+$' -or $release.draft -or $release.prerelease) { throw 'GitHub did not return a stable Otono release.' }
    if ([version]$latest.Substring(1) -le [version]$current.Substring(1)) { Show-Info "Otono $current is up to date.";return }
    $assetName="Otono-v95-$current-to-$latest.zip"
    $asset=@($release.assets | Where-Object name -ceq $assetName)
    if ($asset.Count -ne 1) { throw "Release $latest has no patch from $current yet. Check its release notes." }
    $url="https://github.com/$repo/releases/download/$latest/$assetName"
    if ($asset[0].browser_download_url -cne $url -or $asset[0].digest -notmatch '^sha256:([0-9a-f]{64})$') { throw 'The official patch or its SHA-256 checksum is unavailable.' }
    $digest=$Matches[1]
    if ([Windows.Forms.MessageBox]::Show("Update Otono $current to $latest? Your saved characters, game assets and config are kept.","Otono updates",'YesNo','Question') -ne 'Yes') { return }
    Require-Stopped
    $temp=[IO.Path]::GetTempFileName()
    try {
        Write-Host "Downloading and checking $assetName..."
        $ProgressPreference="SilentlyContinue"
        Invoke-WebRequest -UseBasicParsing -Uri $url -Headers @{'User-Agent'='Otono-v95-launcher'} -OutFile $temp -TimeoutSec 300
        if ((File-Hash $temp) -ne $digest) { throw 'Downloaded patch failed its release checksum.' }
        $installed=Apply-Package $temp $current $latest
        Show-Info "Updated to $installed. Reopen Otono.cmd to play."
        return $true
    } finally { [IO.File]::Delete($temp) }
}
try {
    [void](Safe-Path 'launcher/bin/update.lock')
    [void][IO.Directory]::CreateDirectory($bin)
    $lock=[IO.File]::Open((Join-Path $bin 'update.lock'),'OpenOrCreate','ReadWrite','None')
    try {
        if (Test-Path -LiteralPath $pending) { Require-Stopped;Restore-Update }
        $current=Current-Version
        if ($Package) { Apply-Package ([IO.Path]::GetFullPath($Package)) $current $null }
        elseif (Online-Update $current) { exit 10 }
    } finally { $lock.Dispose() }
} catch {
    if ($Package) { throw }
    [void][Windows.Forms.MessageBox]::Show($_.Exception.Message,'Otono update','OK','Warning')
    exit 1
}
