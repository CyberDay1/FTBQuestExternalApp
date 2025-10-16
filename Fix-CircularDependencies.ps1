# Fix-CircularDependencies.ps1
# Detect and break circular ProjectReference dependencies in FTBQuestExternalApp2

$root = "C:\Users\conno\IdeaProjects\FTBQuestExternalApp2"

Write-Host "Scanning for circular dependencies in $root..." -ForegroundColor Cyan

# Get all .csproj files
$projects = Get-ChildItem -Path $root -Recurse -Filter *.csproj
$projectMap = @{}

# Build dependency map
foreach ($proj in $projects) {
    $projName = [System.IO.Path]::GetFileNameWithoutExtension($proj.Name)
    $refs = Select-String -Path $proj.FullName -Pattern '<ProjectReference Include="(.+?)"' |
            ForEach-Object { [System.IO.Path]::GetFileNameWithoutExtension($_.Matches.Groups[1].Value) }
    $projectMap[$projName] = $refs
}

# Detect cycles
function Detect-Cycles($graph) {
    $visited = @{}
    $stack = @{}
    $cycles = @()

    function Visit($node, $path) {
        if ($stack[$node]) {
            $idx = [array]::IndexOf($path, $node)
            $cycle = $path[$idx..($path.Count - 1)]
            $script:cycles += ,$cycle
            return
        }
        if ($visited[$node]) { return }

        $visited[$node] = $true
        $stack[$node] = $true

        if ($graph[$node]) {
            foreach ($child in $graph[$node]) {
                Visit $child ($path + $node)
            }
        }
        $stack[$node] = $false
    }

    foreach ($node in $graph.Keys) {
        Visit $node @()
    }

    return $cycles
}

$cycles = Detect-Cycles $projectMap

if ($cycles.Count -eq 0) {
    Write-Host "No circular dependencies detected." -ForegroundColor Green
    exit
}

Write-Host "`nCircular dependencies detected:" -ForegroundColor Yellow
$cycles | ForEach-Object { Write-Host (" - " + ($_.Trim() -join ' -> ')) -ForegroundColor Red }

# Break cycles automatically by removing one back-reference per cycle
foreach ($cycle in $cycles) {
    $last = $cycle[-1]
    $first = $cycle[0]
    $lastProj = $projects | Where-Object { $_.Name -eq "$last.csproj" }
    if ($lastProj) {
        $content = Get-Content $lastProj.FullName
        $pattern = "<ProjectReference Include=""..\\$first\\$first.csproj"""
        if ($content -match $pattern) {
            Write-Host "Removing reference $first from $last" -ForegroundColor Cyan
            $newContent = $content | Where-Object { $_ -notmatch $pattern }
            $newContent | Set-Content $lastProj.FullName -Encoding UTF8
        }
    }
}

Write-Host "`nCleanup complete. Run these commands next:" -ForegroundColor Green
Write-Host "  dotnet clean"
Write-Host "  dotnet restore"
Write-Host "  dotnet build -c Release"
