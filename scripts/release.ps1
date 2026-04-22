param(
    [ValidateSet("major", "minor", "patch")]
    [string]$Part = "patch",

    [string]$Message = "",

    [switch]$Push
)

$ErrorActionPreference = "Stop"
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$versionFile = Join-Path $repoRoot "version.properties"
$utf8NoBom = [Text.UTF8Encoding]::new($false)

Set-Location $repoRoot

if (-not (Test-Path $versionFile)) {
    [IO.File]::WriteAllText($versionFile, "versionName=0.0.0`nversionCode=0`n", $utf8NoBom)
}

$props = @{}
Get-Content $versionFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match "^\s*([^#][^=]+?)\s*=\s*(.*?)\s*$") {
        $props[$matches[1]] = $matches[2]
    }
}

$versionName = $props["versionName"]
if (-not $versionName) {
    $versionName = "0.0.0"
}

$versionCode = 0
if ($props["versionCode"]) {
    $versionCode = [int]$props["versionCode"]
}

$parts = $versionName.Split(".")
if ($parts.Length -ne 3) {
    throw "Invalid versionName '$versionName'. Expected format: X.Y.Z"
}

$major = [int]$parts[0]
$minor = [int]$parts[1]
$patch = [int]$parts[2]

switch ($Part) {
    "major" {
        $major += 1
        $minor = 0
        $patch = 0
    }
    "minor" {
        $minor += 1
        $patch = 0
    }
    default {
        $patch += 1
    }
}

$newVersionName = "$major.$minor.$patch"
$newVersionCode = $versionCode + 1
$tagName = "v$newVersionName"

if (git rev-parse -q --verify "refs/tags/$tagName") {
    throw "Tag $tagName already exists."
}

$versionContent = "versionName=$newVersionName`nversionCode=$newVersionCode`n"
[IO.File]::WriteAllText($versionFile, $versionContent, $utf8NoBom)

if ([string]::IsNullOrWhiteSpace($Message)) {
    $Message = "Release $tagName"
}

git add -A
git commit -m $Message
git tag -a $tagName -m $Message

if ($Push) {
    git push
    git push origin $tagName
}

Write-Host "Created commit and tag $tagName"
Write-Host "Version: $newVersionName"
Write-Host "VersionCode: $newVersionCode"

if (-not $Push) {
    Write-Host "Push with: git push && git push origin $tagName"
}
