# Sync to GitHub, GitLab, and VPS.
# Run from project root: .\scripts\deploy.ps1
# For VPS deploy you need SSH key auth: ssh-copy-id root@162.246.19.78 (from Git Bash/WSL)
# Or run deploy-vps.sh manually on the server after pushing.

param([switch]$SkipPush, [switch]$VpsOnly)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

if (-not $SkipPush -and -not $VpsOnly) {
    git status -sb
    $confirm = Read-Host "Commit and push to origin + gitlab? (y/n)"
    if ($confirm -ne "y") { exit 0 }
    git add -A
    $msg = Read-Host "Commit message (empty = skip commit)"
    if ($msg) {
        git commit -m $msg
    }
    git push origin main
    git push gitlab main
}

if (-not $VpsOnly) {
    Write-Host "Pushed to GitHub and GitLab."
}

# VPS deploy via SSH (requires key-based auth)
$vps = "root@162.246.19.78"
$cmd = "cd /var/www/round.ozzy1986.com && bash scripts/deploy-vps.sh"
Write-Host "Running on VPS: $cmd"
ssh $vps $cmd
