param([string[]]$Tasks = @('testDebugUnitTest','lintDebug','assembleDebug'))
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (!$env:JAVA_HOME) {
    $localJdk = 'F:\ComfyAutomation\android-toolchain\jdk\jdk-17.0.20+8'
    if (Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME = $localJdk }
}
if (!$env:JAVA_HOME) { throw '请配置 JAVA_HOME 指向 JDK 17' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$requiresSigning = @($Tasks | Where-Object { $_ -match 'assemble|package|install|connected' }).Count -gt 0
if ($requiresSigning -and (!(Test-Path -LiteralPath "$PSScriptRoot\signing.properties") -or !(Test-Path -LiteralPath "$PSScriptRoot\.signing\coin-test.jks"))) {
    throw '固定测试签名缺失。请恢复本项目私下备份的 .signing 和 signing.properties，不能更换签名后覆盖更新。'
}
$env:GRADLE_USER_HOME = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'work\gradle-home'
if (Test-Path -LiteralPath "$PSScriptRoot\gradlew.bat") {
    & "$PSScriptRoot\gradlew.bat" @Tasks --console=plain
} else {
    $gradle = 'F:\ComfyAutomation\android-toolchain\gradle\gradle-8.13\bin\gradle.bat'
    if (!(Test-Path -LiteralPath $gradle)) { throw 'Gradle wrapper 缺失；请用 Gradle 8.13 执行 gradle wrapper' }
    & $gradle @Tasks --console=plain
}
if ($LASTEXITCODE -ne 0) { throw '构建或测试失败，请检查输出' }
