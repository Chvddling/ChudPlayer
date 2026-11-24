# Runs a Gradle task, shows the output live and saves it (UTF-8) to the log file.
# Called by build.bat - you don't need to run this yourself.
param(
    [string]$Task = 'run',
    [string]$Log = 'build-log.txt'
)

$ErrorActionPreference = 'Continue'
Set-Location (Join-Path $PSScriptRoot '..')

$w = New-Object System.IO.StreamWriter($Log, $false, (New-Object System.Text.UTF8Encoding($false)))
$w.AutoFlush = $true
function Out-Both([string]$line) {
    Write-Host $line
    $w.WriteLine($line)
}

$code = 1
try {
    Out-Both "ChudPlayer build log - $(Get-Date -Format s) - task: $Task"
    Out-Both "Windows: $([Environment]::OSVersion.VersionString) / PowerShell $($PSVersionTable.PSVersion)"
    Out-Both "Folder: $(Get-Location)"
    Out-Both "JAVA_HOME: $env:JAVA_HOME"
    & cmd /c "java -version 2>&1" | ForEach-Object { Out-Both ([string]$_) }
    Out-Both "libmpv-2.dll present: $(Test-Path 'native-resources\windows\libmpv-2.dll')"
    Out-Both ''

    # cmd does the 2>&1 so Gradle's warnings aren't turned into PowerShell errors
    & cmd /c "gradlew.bat $Task --console=plain --stacktrace 2>&1" | ForEach-Object { Out-Both ([string]$_) }
    $code = $LASTEXITCODE

    Out-Both ''
    Out-Both "gradlew exit code: $code"
}
catch {
    Out-Both "SCRIPT ERROR: $_"
    $code = 99
}
finally {
    $w.Close()
}
exit $code
