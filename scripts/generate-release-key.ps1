param([string]$Alias = 'finplus-release')
$ErrorActionPreference='Stop'
$mobileRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$keyPath=Join-Path $mobileRoot 'finplus-release.jks'
$propsPath=Join-Path $mobileRoot 'keystore.properties'
if((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $propsPath)){throw 'Release key already exists; refusing to overwrite it.'}
$bytes=New-Object byte[] 24
$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$password=[Convert]::ToBase64String($bytes).Replace('+','A').Replace('/','B').TrimEnd('=')
& keytool -genkeypair -v -keystore $keyPath -alias $Alias -keyalg RSA -keysize 4096 -validity 10000 -storepass $password -keypass $password -dname 'CN=FinPlus, OU=Mobile, O=FinPlus, L=Moscow, C=RU'
if($LASTEXITCODE -ne 0){throw 'keytool failed'}
@("storeFile=finplus-release.jks","storePassword=$password","keyAlias=$Alias","keyPassword=$password") | Set-Content -LiteralPath $propsPath -Encoding ascii
Write-Output "Release key created at $keyPath. Back it up securely; losing it prevents app updates."
