# Downloads the latest Forge MDK for Minecraft 1.20.1 from the Forge Maven and extracts it into the workspace root.
# Usage: run from any location. This script writes into C:\Apps\IslandCraft (workspace root configured here).

$workspaceRoot = 'C:\Apps\IslandCraft'
$metadataUrl = 'https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml'

Write-Host "Fetching Forge metadata from $metadataUrl..."
try {
    $rsp = Invoke-WebRequest -Uri $metadataUrl -UseBasicParsing -ErrorAction Stop
    [xml]$xml = $rsp.Content
} catch {
    Write-Error "Failed to fetch metadata: $_"
    exit 1
}

$allVersions = $xml.metadata.versioning.versions.version
if (-not $allVersions) {
    Write-Error "No versions found in metadata"
    exit 1
}

# Filter versions that start with '1.20.1-'
$versions1201 = $allVersions | Where-Object { $_ -match '^1\.20\.1-' }
if (-not $versions1201) {
    Write-Error "No Forge versions found for Minecraft 1.20.1 in metadata"
    exit 1
}

# Pick the last (assumed newest) version
$version = $versions1201[-1]
Write-Host "Selected Forge version: $version"

$mdkFileName = "forge-$version-mdk.zip"
$mdkUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$version/$mdkFileName"
Write-Host "MDK URL: $mdkUrl"

$tempZip = Join-Path $env:TEMP "forge_mdk_$version.zip"
$tempExtract = Join-Path $env:TEMP "forge_mdk_extract_$version"

if (Test-Path $tempZip) { Remove-Item $tempZip -Force }
if (Test-Path $tempExtract) { Remove-Item $tempExtract -Recurse -Force }

Write-Host "Downloading MDK to $tempZip..."
try {
    Invoke-WebRequest -Uri $mdkUrl -OutFile $tempZip -UseBasicParsing -ErrorAction Stop
} catch {
    Write-Error "Failed to download MDK: $_"
    exit 1
}

Write-Host "Extracting MDK to temporary folder $tempExtract..."
try {
    Expand-Archive -Path $tempZip -DestinationPath $tempExtract -Force
} catch {
    Write-Error "Failed to extract archive: $_"
    exit 1
}

# Move contents from the extracted folder into the workspace root. Many MDKs extract either files directly or into a subfolder.
Write-Host "Moving MDK files into workspace root $workspaceRoot..."
# Ensure workspace exists
if (-not (Test-Path $workspaceRoot)) { New-Item -ItemType Directory -Path $workspaceRoot | Out-Null }

# If the extract folder contains a single directory, use that as source; otherwise use the extract folder itself
$children = Get-ChildItem -Path $tempExtract
if ($children.Count -eq 1 -and $children[0].PSIsContainer) {
    $source = $children[0].FullName
} else {
    $source = $tempExtract
}

Write-Host "Source for copy: $source"

# Copy all files into workspace root, overwriting existing files
try {
    Get-ChildItem -Path $source -Force | ForEach-Object {
        $dest = Join-Path $workspaceRoot $_.Name
        if ($_.PSIsContainer) {
            # Use Copy-Item -Recurse -Force for directories
            Copy-Item -Path $_.FullName -Destination $dest -Recurse -Force
        } else {
            Copy-Item -Path $_.FullName -Destination $dest -Force
        }
    }
} catch {
    Write-Error "Failed to copy MDK files into workspace: $_"
    exit 1
}

# Clean up temp files
Remove-Item $tempZip -Force
Remove-Item $tempExtract -Recurse -Force

Write-Host "Forge MDK $version downloaded and extracted into $workspaceRoot" -ForegroundColor Green
Write-Host "Run '.\\gradlew setup' or '.\\gradlew build' from the workspace root as needed." -ForegroundColor Green
