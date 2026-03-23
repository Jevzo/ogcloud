[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Position = 0)]
    [string]$ModeOrTarget,

    [Parameter(Position = 1)]
    [string]$Target,

    [Alias("p")]
    [switch]$Push
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$registry = "ogwarsdev"

$targets = @{
    "common" = @{
        Kind = "gradle"
        ProjectDir = "ogcloud-common"
        BuildTask = ":ogcloud-common:jar"
        CleanTask = ":ogcloud-common:clean"
        PublishTask = ":ogcloud-common:publishGprPublicationToGitHubPackagesRepository"
    }
    "api" = @{
        Kind = "spring"
        ProjectDir = "ogcloud-api"
        BuildTask = ":ogcloud-api:bootJar"
        CleanTask = ":ogcloud-api:clean"
        DockerFile = "ogcloud-api/Dockerfile"
        DockerContext = "."
        Image = "$registry/api"
    }
    "controller" = @{
        Kind = "spring"
        ProjectDir = "ogcloud-controller"
        BuildTask = ":ogcloud-controller:bootJar"
        CleanTask = ":ogcloud-controller:clean"
        DockerFile = "ogcloud-controller/Dockerfile"
        DockerContext = "."
        Image = "$registry/controller"
    }
    "paper-plugin" = @{
        Kind = "gradle"
        ProjectDir = "ogcloud-paper-plugin"
        BuildTask = ":ogcloud-paper-plugin:shadowJar"
        CleanTask = ":ogcloud-paper-plugin:clean"
        PublishTask = ":ogcloud-paper-plugin:publishGprPublicationToGitHubPackagesRepository"
    }
    "legacy-paper-plugin" = @{
        Kind = "gradle"
        ProjectDir = "ogcloud-legacy-paper-plugin"
        BuildTask = ":ogcloud-legacy-paper-plugin:shadowJar"
        CleanTask = ":ogcloud-legacy-paper-plugin:clean"
        PublishTask = ":ogcloud-legacy-paper-plugin:publishGprPublicationToGitHubPackagesRepository"
    }
    "velocity-plugin" = @{
        Kind = "gradle"
        ProjectDir = "ogcloud-velocity-plugin"
        BuildTask = ":ogcloud-velocity-plugin:shadowJar"
        CleanTask = ":ogcloud-velocity-plugin:clean"
        PublishTask = ":ogcloud-velocity-plugin:publishGprPublicationToGitHubPackagesRepository"
    }
    "loadbalancer" = @{
        Kind = "go"
        ProjectDir = "ogcloud-loadbalancer"
        WorkDir = "ogcloud-loadbalancer"
        BuildArgs = @("build", "-o", "loadbalancer", "./cmd/loadbalancer")
        Artifacts = @("loadbalancer", "loadbalancer.exe")
        Environment = @{
            "GOOS" = "linux"
            "GOARCH" = "amd64"
            "CGO_ENABLED" = "0"
        }
        DockerFile = "ogcloud-loadbalancer/Dockerfile"
        DockerContext = "ogcloud-loadbalancer"
        Image = "$registry/loadbalancer"
    }
    "template-loader" = @{
        Kind = "go"
        ProjectDir = "ogcloud-template-loader"
        WorkDir = "ogcloud-template-loader"
        BuildArgs = @("build", "-o", "template-loader", "./cmd/loader")
        Artifacts = @("template-loader", "template-loader.exe")
        Environment = @{
            "GOOS" = "linux"
            "GOARCH" = "amd64"
            "CGO_ENABLED" = "0"
        }
        DockerFile = "ogcloud-template-loader/Dockerfile"
        DockerContext = "ogcloud-template-loader"
        Image = "$registry/template-loader"
    }
    "dashboard" = @{
        Kind = "node"
        ProjectDir = "frontend/dashboard"
        WorkDir = "frontend/dashboard"
        DockerFile = "frontend/dashboard/Dockerfile"
        DockerContext = "frontend/dashboard"
        Image = "$registry/dashboard"
    }
    "landing-page" = @{
        Kind = "node"
        ProjectDir = "frontend/landing-page"
        WorkDir = "frontend/landing-page"
        DockerFile = "frontend/landing-page/Dockerfile"
        DockerContext = "frontend/landing-page"
        Image = "$registry/landing-page"
    }
}

$localAllTargets = @(
    "common",
    "api",
    "controller",
    "paper-plugin",
    "velocity-plugin",
    "loadbalancer",
    "template-loader",
    "dashboard",
    "landing-page"
)
$publishAllTargets = @("common", "paper-plugin", "legacy-paper-plugin", "velocity-plugin")
$dockerAllTargets = @("api", "controller", "loadbalancer", "template-loader", "dashboard", "landing-page")

function Show-Usage {
    Write-Host "Usage:"
    Write-Host "  .\build.ps1 <target>"
    Write-Host "  .\build.ps1 all"
    Write-Host "  .\build.ps1 clean <target>"
    Write-Host "  .\build.ps1 clean all"
    Write-Host "  .\build.ps1 publish <target>"
    Write-Host "  .\build.ps1 publish all"
    Write-Host "  .\build.ps1 docker <target> [-p]"
    Write-Host "  .\build.ps1 docker all [-p]"
    Write-Host ""
    Write-Host "Targets:"
    Write-Host "  common, api, controller, paper-plugin, legacy-paper-plugin, velocity-plugin, loadbalancer, template-loader, dashboard, landing-page, all"
    Write-Host ""
    Write-Host "Notes:"
    Write-Host "  - Use -p to push after a docker build."
    Write-Host "  - Use publish mode for GitHub Packages publishing."
    Write-Host "  - Docker tags are always read from the target project's VERSION file."
    Write-Host "  - Use -WhatIf to dry-run any command without executing it."
}

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [string[]]$Arguments = @(),

        [string]$WorkingDirectory = $repoRoot,

        [hashtable]$Environment = @{},

        [Parameter(Mandatory = $true)]
        [string]$Action,

        [Parameter(Mandatory = $true)]
        [string]$TargetLabel
    )

    if (-not $PSCmdlet.ShouldProcess($TargetLabel, $Action)) {
        return
    }

    Push-Location $WorkingDirectory
    $previousEnvironment = @{}
    try {
        foreach ($name in $Environment.Keys) {
            $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
            [Environment]::SetEnvironmentVariable($name, $Environment[$name], "Process")
        }

        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Command failed ($LASTEXITCODE): $FilePath $($Arguments -join ' ')"
        }
    } finally {
        foreach ($name in $Environment.Keys) {
            [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
        }
        Pop-Location
    }
}

function Get-TargetConfig {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetName
    )

    $config = $targets[$TargetName]
    if (-not $config) {
        throw "Unknown target: $TargetName"
    }

    return $config
}

function Get-ProjectVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetName
    )

    $config = Get-TargetConfig -TargetName $TargetName
    $projectDir = Join-Path $repoRoot $config.ProjectDir
    $versionFile = Join-Path $projectDir "VERSION"

    if (-not (Test-Path $versionFile)) {
        throw "Missing VERSION file for target '$TargetName': $versionFile"
    }

    $version = (Get-Content -Path $versionFile -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($version)) {
        throw "VERSION file for target '$TargetName' is empty: $versionFile"
    }

    return $version
}

function Invoke-LocalBuild {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetName
    )

    $config = Get-TargetConfig -TargetName $TargetName

    switch ($config.Kind) {
        "spring" {
            Write-Host "Building $TargetName via Gradle ($($config.BuildTask))..."
            Invoke-CheckedCommand `
                -FilePath $gradleWrapper `
                -Arguments @($config.BuildTask, "--no-daemon") `
                -Action "Build" `
                -TargetLabel $TargetName
        }
        "gradle" {
            Write-Host "Building $TargetName via Gradle ($($config.BuildTask))..."
            Invoke-CheckedCommand `
                -FilePath $gradleWrapper `
                -Arguments @($config.BuildTask, "--no-daemon") `
                -Action "Build" `
                -TargetLabel $TargetName
        }
        "go" {
            $workingDirectory = Join-Path $repoRoot $config.WorkDir
            Write-Host "Building $TargetName via Go..."
            Invoke-CheckedCommand `
                -FilePath "go" `
                -Arguments $config.BuildArgs `
                -WorkingDirectory $workingDirectory `
                -Environment $config.Environment `
                -Action "Build" `
                -TargetLabel $TargetName
        }
        "node" {
            $workingDirectory = Join-Path $repoRoot $config.WorkDir
            Write-Host "Building $TargetName via pnpm..."
            Invoke-CheckedCommand `
                -FilePath "pnpm" `
                -Arguments @("run", "build") `
                -WorkingDirectory $workingDirectory `
                -Action "Build" `
                -TargetLabel $TargetName
        }
        default {
            throw "Unsupported build kind: $($config.Kind)"
        }
    }
}

function Invoke-DockerBuild {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetName,

        [switch]$DoPush
    )

    $config = Get-TargetConfig -TargetName $TargetName
    if (-not $config.Image) {
        throw "Target '$TargetName' does not support docker builds"
    }

    if ($config.Kind -eq "spring") {
        Invoke-LocalBuild -TargetName $TargetName
    }

    $dockerFile = Join-Path $repoRoot $config.DockerFile
    $dockerContext = Join-Path $repoRoot $config.DockerContext
    $resolvedTag = Get-ProjectVersion -TargetName $TargetName
    $imageTag = "$($config.Image):$resolvedTag"

    Write-Host "Building Docker image $imageTag..."
    Invoke-CheckedCommand `
        -FilePath "docker" `
        -Arguments @("build", "-t", $imageTag, "-f", $dockerFile, $dockerContext) `
        -Action "Docker build" `
        -TargetLabel $imageTag

    if ($DoPush) {
        Write-Host "Pushing Docker image $imageTag..."
        Invoke-CheckedCommand `
            -FilePath "docker" `
            -Arguments @("push", $imageTag) `
            -Action "Docker push" `
            -TargetLabel $imageTag
    }
}

function Invoke-TargetClean {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetName
    )

    $config = Get-TargetConfig -TargetName $TargetName

    switch ($config.Kind) {
        "spring" {
            Write-Host "Cleaning $TargetName via Gradle ($($config.CleanTask))..."
            Invoke-CheckedCommand `
                -FilePath $gradleWrapper `
                -Arguments @($config.CleanTask, "--no-daemon") `
                -Action "Clean" `
                -TargetLabel $TargetName
        }
        "gradle" {
            Write-Host "Cleaning $TargetName via Gradle ($($config.CleanTask))..."
            Invoke-CheckedCommand `
                -FilePath $gradleWrapper `
                -Arguments @($config.CleanTask, "--no-daemon") `
                -Action "Clean" `
                -TargetLabel $TargetName
        }
        "go" {
            $workingDirectory = Join-Path $repoRoot $config.WorkDir
            foreach ($artifact in $config.Artifacts) {
                $artifactPath = Join-Path $workingDirectory $artifact
                if (Test-Path $artifactPath) {
                    Write-Host "Removing $artifactPath..."
                    if ($PSCmdlet.ShouldProcess($artifactPath, "Remove generated binary")) {
                        Remove-Item -Force $artifactPath
                    }
                }
            }
        }
        "node" {
            $workingDirectory = Join-Path $repoRoot $config.WorkDir
            $distPath = Join-Path $workingDirectory "dist"
            if (Test-Path $distPath) {
                Write-Host "Removing $distPath..."
                if ($PSCmdlet.ShouldProcess($distPath, "Remove frontend build output")) {
                    Remove-Item -Recurse -Force $distPath
                }
            }
        }
        default {
            throw "Unsupported clean kind: $($config.Kind)"
        }
    }
}

function Invoke-GitHubPackagesPublish {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetName
    )

    $config = Get-TargetConfig -TargetName $TargetName
    $publishTask = $config.PublishTask
    if (-not $publishTask) {
        throw "Target '$TargetName' does not support GitHub Packages publishing"
    }

    Write-Host "Publishing $TargetName to GitHub Packages via Gradle ($publishTask)..."
    Invoke-CheckedCommand `
        -FilePath $gradleWrapper `
        -Arguments @($publishTask, "--no-daemon") `
        -Action "Publish to GitHub Packages" `
        -TargetLabel $TargetName
}

if (-not $ModeOrTarget -or $ModeOrTarget -in @("help", "-h", "--help")) {
    Show-Usage
    exit 0
}

$isDockerMode = $ModeOrTarget -eq "docker"
$isCleanMode = $ModeOrTarget -eq "clean"
$isPublishMode = $ModeOrTarget -eq "publish"

if ($isDockerMode) {
    if (-not $Target) {
        Show-Usage
        throw "Docker mode requires a target"
    }

    $targetsToBuild = if ($Target -eq "all") { $dockerAllTargets } else { @($Target) }
    foreach ($item in $targetsToBuild) {
        Invoke-DockerBuild -TargetName $item -DoPush:$Push
    }
    exit 0
}

if ($isCleanMode) {
    if (-not $Target) {
        Show-Usage
        throw "Clean mode requires a target"
    }

    $targetsToClean = if ($Target -eq "all") { $localAllTargets } else { @($Target) }
    foreach ($item in $targetsToClean) {
        Invoke-TargetClean -TargetName $item
    }
    exit 0
}

if ($isPublishMode) {
    if (-not $Target) {
        Show-Usage
        throw "Publish mode requires a target"
    }

    $targetsToPublish = if ($Target -eq "all") { $publishAllTargets } else { @($Target) }
    foreach ($item in $targetsToPublish) {
        Invoke-GitHubPackagesPublish -TargetName $item
    }
    exit 0
}

if ($Target) {
    Show-Usage
    throw "Local build mode only accepts a single target"
}

$targetsToBuild = if ($ModeOrTarget -eq "all") { $localAllTargets } else { @($ModeOrTarget) }
foreach ($item in $targetsToBuild) {
    Invoke-LocalBuild -TargetName $item
}
