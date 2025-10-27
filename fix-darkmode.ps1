# =====================================================================
# FTBQuestExternalApp Dark Mode Resource Fix
# Ensures CSS files are correctly placed and updates MainApp.java paths
# =====================================================================

$projectRoot = "C:\path\to\FTBQuestExternalApp-main"
$uiJavaFx = Join-Path $projectRoot "ui-javafx"
$cssDir = Join-Path $uiJavaFx "src\main\resources\css"

# 1. Ensure CSS directory exists
if (-not (Test-Path $cssDir)) {
    Write-Host "Creating CSS directory at $cssDir"
    New-Item -ItemType Directory -Force -Path $cssDir | Out-Null
}

# 2. Create missing CSS files if needed
$cssFiles = @("dark.css", "light.css", "tokens.css")

foreach ($file in $cssFiles) {
    $path = Join-Path $cssDir $file
    if (-not (Test-Path $path)) {
        Write-Host "Creating missing file: $file"
        switch ($file) {
            "tokens.css" {
                @"
:root {
    -bg: #141414;
    -bg-elev: #1c1c1c;
    -fg: #e6e6e6;
    -fg-muted: #b6b6b6;
    -accent: #6ea8fe;
    -grid: #3a3a3a;
    -node: #2f4f4f;
    -node-stroke: #b0b0b0;
    -edge-required: #f6c744;
    -edge-optional: #a78bfa;
}
"@ | Out-File $path -Encoding UTF8
            }
            "dark.css" {
                @"
@import 'tokens.css';
.root { -fx-background-color: -bg; -fx-text-fill: -fg; }
.label, .text { -fx-fill: -fg; -fx-text-fill: -fg; }
.button { -fx-background-color: -bg-elev; -fx-text-fill: -fg; }
"@ | Out-File $path -Encoding UTF8
            }
            "light.css" {
                @"
@import 'tokens.css';
:root {
    -bg: #f7f7f7;
    -bg-elev: #ffffff;
    -fg: #1a1a1a;
    -fg-muted: #5a5a5a;
    -accent: #1967d2;
    -grid: #dddddd;
    -node: #e0f2ff;
    -node-stroke: #1967d2;
    -edge-required: #cc8b00;
    -edge-optional: #6a3dad;
}
"@ | Out-File $path -Encoding UTF8
            }
        }
    }
}

# 3. Update MainApp.java to use ClassLoader resource lookup
$mainAppPath = Get-ChildItem -Path $uiJavaFx -Recurse -Filter "MainApp.java" | Select-Object -First 1
if ($mainAppPath) {
    $content = Get-Content $mainAppPath.FullName -Raw
    if ($content -match 'getClass\(\)\.getResource') {
        Write-Host "Updating MainApp.java resource loading method..."
        $fixed = $content -replace 'getClass\(\)\.getResource\(css\)\.toExternalForm\(\)', 'MainApp.class.getClassLoader().getResource(css).toExternalForm()'
        Set-Content $mainAppPath.FullName $fixed -Encoding UTF8
    } else {
        Write-Host "MainApp.java already uses ClassLoader resource loading."
    }
} else {
    Write-Host "Could not locate MainApp.java under $uiJavaFx"
}

Write-Host "✅ Dark mode resource fix complete. Rebuild the project to apply changes."
