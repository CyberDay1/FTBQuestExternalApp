# Fix-FinalCirculars.ps1
$root = "C:\Users\conno\IdeaProjects\FTBQuestExternalApp2\libs"

# Gather all project references
$refs = @{}
$projects = Get-ChildItem -Path $root -Recurse -Filter *.csproj

foreach ($proj in $projects) {
    [xml]$xml = Get-Content $proj.FullName
    $name = [System.IO.Path]::GetFileNameWithoutExtension($proj.Name)
    $refs[$name] = @()

    foreach ($ref in @($xml.Project.ItemGroup.ProjectReference)) {
        if (-not $ref.Include) { continue }
        $target = [System.IO.Path]::GetFileNameWithoutExtension((Split-Path $ref.Include -Leaf))
        $refs[$name] += $target
    }
}

Write-Host "Detected project dependency map:" -ForegroundColor Cyan
$refs.GetEnumerator() | ForEach-Object { Write-Host "$($_.Key) -> $($_.Value -join ', ')" }

# Find mutual (circular) references
foreach ($proj in $projects) {
    [xml]$xml = Get-Content $proj.FullName
    $name = [System.IO.Path]::GetFileNameWithoutExtension($proj.Name)
    $changed = $false

    foreach ($ref in @($xml.Project.ItemGroup.ProjectReference)) {
        if (-not $ref.Include) { continue }
        $target = [System.IO.Path]::GetFileNameWithoutExtension((Split-Path $ref.Include -Leaf))

        # If both projects reference each other, remove one direction
        if ($refs[$target] -and $refs[$target] -contains $name) {
            Write-Host "Removing mutual ref: $name ↔ $target" -ForegroundColor Yellow
            $ref.ParentNode.RemoveChild($ref) | Out-Null
            $changed = $true
        }
    }

    if ($changed) {
        $utf8 = New-Object System.Text.UTF8Encoding($false)
        $writer = New-Object System.IO.StreamWriter($proj.FullName, $false, $utf8)
        $xml.Save($writer)
        $writer.Close()
    }
}

Write-Host "`nAll mutual circular project references have been removed." -ForegroundColor Green
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  dotnet clean"
Write-Host "  dotnet restore"
Write-Host "  dotnet build -c Release"
