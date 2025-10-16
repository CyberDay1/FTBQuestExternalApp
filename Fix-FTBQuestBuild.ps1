# Fix-FTBQuestBuild.ps1
# Purpose: Resolve namespace, reference, and csproj issues for FTBQuestExternalApp2

$ErrorActionPreference = "Stop"
$repoRoot = "C:\Users\conno\IdeaProjects\FTBQuestExternalApp2"

Write-Host "=== Fixing FTBQuestExternalApp2 build configuration ==="

# --- 1. Ensure Tests project references all required libs ---
$testsProj = Join-Path $repoRoot "tests\FTBQuests.Tests\FTBQuests.Tests.csproj"
if (Test-Path $testsProj) {
    Write-Host "Patching FTBQuests.Tests.csproj..."
    $content = Get-Content $testsProj -Raw

    if ($content -notmatch "FTBQuests.Validation") {
        $patch = @"
  <ItemGroup>
    <ProjectReference Include="..\..\libs\FTBQuests.Validation\FTBQuests.Validation.csproj" />
    <ProjectReference Include="..\..\libs\FTBQuests.Assets\FTBQuests.Assets.csproj" />
    <ProjectReference Include="..\..\libs\FTBQuests.IO\FTBQuests.IO.csproj" />
    <ProjectReference Include="..\..\libs\FTBQuests.Codecs\FTBQuests.Codecs.csproj" />
  </ItemGroup>
"@
        $content = $content -replace "(?<=</Project>)", $patch
        Set-Content $testsProj $content
    }
}

# --- 2. Qualify ambiguous QuestPack references ---
Write-Host "Fixing ambiguous QuestPack references..."
$files = Get-ChildItem -Path $repoRoot -Recurse -Include *.cs | Where-Object {
    Select-String -Path $_.FullName -Pattern "\bQuestPack\b"
}

foreach ($file in $files) {
    (Get-Content $file.FullName) |
            ForEach-Object {
                $_ -replace "(?<!FTBQuests\.IO\.)\bQuestPack\b", "FTBQuests.IO.QuestPack"
            } | Set-Content $file.FullName
}

# --- 3. Fix missing Validation and Assets using statements ---
Write-Host "Adding using directives for FTBQuests.Validation and FTBQuests.Assets..."
$validationFiles = Get-ChildItem -Path $repoRoot -Recurse -Include *.cs | Where-Object {
    Select-String -Path $_.FullName -Pattern "class|namespace"
}
foreach ($file in $validationFiles) {
    $lines = Get-Content $file.FullName
    if ($lines -notmatch "using FTBQuests.Validation;") {
        $lines = @("using FTBQuests.Validation;", "using FTBQuests.Assets;") + $lines
        Set-Content $file.FullName $lines
    }
}

# --- 4. Rebuild the solution ---
Write-Host "Cleaning and rebuilding solution..."
Set-Location $repoRoot
dotnet clean
dotnet build -c Release

Write-Host "=== Completed ==="
