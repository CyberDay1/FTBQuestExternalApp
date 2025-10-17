# Fix-RegistryValidationLoop.ps1
# Removes the circular dependency between Registry and Validation

$registry = 'libs\FTBQuests.Registry\FTBQuests.Registry.csproj'
$validation = 'libs\FTBQuests.Validation\FTBQuests.Validation.csproj'

# 1) Backup Registry project file
Copy-Item $registry "$registry.bak" -Force

# 2) Remove Validation reference from Registry
(Get-Content $registry) |
        Where-Object { $_ -notmatch 'FTBQuests\.Validation\.csproj' } |
        Set-Content $registry -Encoding UTF8

# 3) Clear intermediate build folders
Remove-Item 'libs\FTBQuests.Registry\obj','libs\FTBQuests.Registry\bin','libs\FTBQuests.Validation\obj','libs\FTBQuests.Validation\bin' `
  -Recurse -Force -ErrorAction SilentlyContinue

# 4) Rebuild cleanly
dotnet clean
dotnet restore
dotnet build -c Release

Write-Host "`nCircular dependency removed. Registry no longer depends on Validation."
