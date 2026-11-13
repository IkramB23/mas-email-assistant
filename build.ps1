# Compiles the project and builds mail-assistant.jar (JDK 21+ only, no other dependency).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (Test-Path build/classes) { Remove-Item -Recurse -Force build/classes }
New-Item -ItemType Directory -Force build/classes | Out-Null
$sources = Get-ChildItem -Recurse -Filter *.java src/main/java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d build/classes $sources
if ($LASTEXITCODE -ne 0) { exit 1 }
jar --create --file mail-assistant.jar --main-class mas.mail.Main -C build/classes .
Write-Host "Built mail-assistant.jar - run: java -jar mail-assistant.jar   (or --cli, --eval, --mock)"
