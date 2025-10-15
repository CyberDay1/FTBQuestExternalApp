param(
    [string]$SourceDirectory = (Join-Path $PSScriptRoot '..' 'dist' 'portable'),
    [string]$DestinationArchive = (Join-Path $PSScriptRoot '..' 'dist' 'FTBQuestEditor_Portable.zip')
)

if (-not (Test-Path $SourceDirectory)) {
    throw "Portable publish output not found at '$SourceDirectory'. Run dotnet publish before packaging."
}

if (-not (Test-Path (Split-Path $DestinationArchive -Parent))) {
    New-Item -ItemType Directory -Path (Split-Path $DestinationArchive -Parent) | Out-Null
}

if (Test-Path $DestinationArchive) {
    Remove-Item $DestinationArchive
}

$itemsToArchive = Get-ChildItem -Path $SourceDirectory -Recurse
if (-not $itemsToArchive) {
    throw "No files were found in '$SourceDirectory' to package."
}

Compress-Archive -Path (Join-Path $SourceDirectory '*') -DestinationPath $DestinationArchive -Force

$archiveInfo = Get-Item $DestinationArchive
$sizeLimitBytes = 250MB
if ($archiveInfo.Length -ge $sizeLimitBytes) {
    throw ("Portable package exceeds size limit. Current size: {0:N2} MB" -f ($archiveInfo.Length / 1MB))
}

Write-Host ("Portable archive created: {0}" -f $DestinationArchive)
Write-Host ("Archive size: {0:N2} MB" -f ($archiveInfo.Length / 1MB))
