param([switch]$BuildOnly)
$ErrorActionPreference = 'Stop'
$launcherDirectory = $PSScriptRoot
$source = Join-Path $launcherDirectory 'OtonoLauncher.cs'
$outputDirectory = Join-Path $launcherDirectory 'bin'
$output = Join-Path $outputDirectory 'OtonoLauncher.exe'
if (!(Test-Path -LiteralPath $output) -or (Get-Item -LiteralPath $source).LastWriteTimeUtc -gt (Get-Item -LiteralPath $output).LastWriteTimeUtc) {
    $compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    if (!(Test-Path -LiteralPath $compiler)) { $compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe' }
    if (!(Test-Path -LiteralPath $compiler)) { throw 'The Windows .NET Framework compiler is missing. Enable .NET Framework 4.x, or use the existing setup/start CMD files.' }
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    & $compiler /nologo /target:winexe /optimize+ /reference:System.Windows.Forms.dll /reference:System.Drawing.dll "/out:$output" $source
    if ($LASTEXITCODE -ne 0) { throw 'Could not build the Otono launcher.' }
}
if (!$BuildOnly) { Start-Process -FilePath $output -WorkingDirectory (Split-Path -Parent $launcherDirectory) }
