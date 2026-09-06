param(
    [ValidateSet('1.21.1', '26.2')][string]$GameVersion = '1.21.1',
    [ValidateSet('all', 'fabric', 'neoforge')][string]$Loader = 'all',
    [string[]]$Tasks = @('build')
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$cacheDirectory = Join-Path $projectRoot '.work\gradle'
$temporaryDirectory = Join-Path $projectRoot '.work\tmp'
New-Item -ItemType Directory -Path $cacheDirectory, $temporaryDirectory -Force | Out-Null
$previousGradle = $env:GRADLE_USER_HOME
$previousTemp = $env:TEMP
$previousTmp = $env:TMP
Push-Location -LiteralPath $projectRoot
try {
    $env:GRADLE_USER_HOME = $cacheDirectory
    $env:TEMP = $temporaryDirectory
    $env:TMP = $temporaryDirectory
    & (Join-Path $projectRoot 'gradlew.bat') "-PgameVersion=$GameVersion" "-Ploader=$Loader" @Tasks '--console=plain'
    $buildExitCode = $LASTEXITCODE
} finally {
    Pop-Location
    $env:GRADLE_USER_HOME = $previousGradle
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
}
exit $buildExitCode
