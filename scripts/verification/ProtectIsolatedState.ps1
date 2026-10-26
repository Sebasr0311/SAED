param([Parameter(Mandatory=$true)][string]$State, [switch]$VerifyOnly)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$stateRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'local\isolated-oracle'))
$protectionStage = 'path-check'

try {
    if ($State -cnotmatch '^recovery-[0-9]{2,4}$') {
        throw 'Unexpected recovery state selector'
    }
    $targetDirectory = [System.IO.Path]::GetFullPath((Join-Path $stateRoot $State))
    if (-not (Split-Path $targetDirectory -Parent).Equals($stateRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Unexpected protected directory'
    }
    $ancestorPath = $targetDirectory
    while ($ancestorPath) {
        $ancestor = Get-Item -LiteralPath $ancestorPath -Force
        if (($ancestor.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Protected path traverses a link'
        }
        $parentPath = Split-Path $ancestorPath -Parent
        if ($parentPath -eq $ancestorPath) { break }
        $ancestorPath = $parentPath
    }
    $protectedFiles = @(Get-ChildItem -LiteralPath $targetDirectory -Force)
    foreach ($candidate in $protectedFiles) {
        if ($candidate.PSIsContainer -or ($candidate.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Protected directory contains an unexpected folder or link'
        }
        if (-not $candidate.FullName.StartsWith($targetDirectory + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Protected file escapes its directory'
        }
    }
    $currentPrincipal = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    if (-not $VerifyOnly) {
        $protectionStage = 'directory-acl'
        $directorySecurity = [System.IO.Directory]::GetAccessControl($targetDirectory, [System.Security.AccessControl.AccessControlSections]::Access)
        $directorySecurity.SetAccessRuleProtection($true, $false)
        foreach ($existingRule in @($directorySecurity.GetAccessRules($true, $false, [System.Security.Principal.SecurityIdentifier]))) {
            $directorySecurity.RemoveAccessRuleAll($existingRule)
        }
        $directoryRule = New-Object System.Security.AccessControl.FileSystemAccessRule(
            $currentPrincipal, [System.Security.AccessControl.FileSystemRights]::FullControl,
            ([System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [System.Security.AccessControl.InheritanceFlags]::ObjectInherit),
            [System.Security.AccessControl.PropagationFlags]::None,
            [System.Security.AccessControl.AccessControlType]::Allow)
        $directorySecurity.AddAccessRule($directoryRule)
        [System.IO.Directory]::SetAccessControl($targetDirectory, $directorySecurity)
        foreach ($candidate in $protectedFiles) {
            $protectionStage = 'file-acl'
            $fileSecurity = [System.IO.File]::GetAccessControl($candidate.FullName, [System.Security.AccessControl.AccessControlSections]::Access)
            $fileSecurity.SetAccessRuleProtection($true, $false)
            foreach ($existingRule in @($fileSecurity.GetAccessRules($true, $false, [System.Security.Principal.SecurityIdentifier]))) {
                $fileSecurity.RemoveAccessRuleAll($existingRule)
            }
            $fileRule = New-Object System.Security.AccessControl.FileSystemAccessRule(
                $currentPrincipal, [System.Security.AccessControl.FileSystemRights]::FullControl,
                [System.Security.AccessControl.AccessControlType]::Allow)
            $fileSecurity.AddAccessRule($fileRule)
            [System.IO.File]::SetAccessControl($candidate.FullName, $fileSecurity)
        }
    }
    $protectionStage = 'verify-acl'
    foreach ($protectedPath in @($targetDirectory) + @($protectedFiles | ForEach-Object { $_.FullName })) {
        $protectedAcl = if ($protectedPath -eq $targetDirectory) {
            [System.IO.Directory]::GetAccessControl($protectedPath, [System.Security.AccessControl.AccessControlSections]::Access)
        } else {
            [System.IO.File]::GetAccessControl($protectedPath, [System.Security.AccessControl.AccessControlSections]::Access)
        }
        $allowedRules = @($protectedAcl.GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier]) | Where-Object { $_.AccessControlType -eq 'Allow' })
        $unexpectedRules = @($allowedRules | Where-Object {
            $_.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value -ne $currentPrincipal.Value
        })
        if (-not $protectedAcl.AreAccessRulesProtected -or $unexpectedRules.Count -gt 0 -or $allowedRules.Count -eq 0) {
            throw 'Credential ACL verification failed'
        }
    }
    Write-Output ('PRIVATE ACL VERIFIED: directory and ' + $protectedFiles.Count + ' files; current user only')
} catch {
    # Paths, principals and exception payloads are deliberately suppressed.
    [Console]::Error.WriteLine('BLOCKED: private state protection failed at ' + $protectionStage + ' (' + $_.Exception.GetType().Name + ')')
    exit 1
}
