# Installs recommended VS Code extensions used for Minecraft Forge modding.
# Requires `code` CLI in PATH (install "Shell Command: Install 'code' command in PATH" from VS Code if missing).

$extensions = @(
    'redhat.java',
    'vscjava.vscode-java-pack',
    'vscjava.vscode-java-debug',
    'vscjava.vscode-java-test',
    'eamodio.gitlens'
)

# check for code
if (-not (Get-Command code -ErrorAction SilentlyContinue)) {
    Write-Host "VS Code CLI 'code' not found. Please enable the 'code' command or install extensions via the Extensions view manually." -ForegroundColor Yellow
    exit 1
}

foreach ($ext in $extensions) {
    Write-Host "Installing extension: $ext"
    code --install-extension $ext --force
}

Write-Host "Done installing recommended extensions." -ForegroundColor Green
