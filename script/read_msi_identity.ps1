param([Parameter(Mandatory = $true)][string]$Package)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$installer = $null
$database = $null
$summary = $null

function Get-MsiProperty($Database, [string]$Name) {
    $view = $null
    $record = $null
    try {
        $query = "SELECT ``Value`` FROM ``Property`` WHERE ``Property``='$Name'"
        $view = $Database.GetType().InvokeMember('OpenView', [Reflection.BindingFlags]::InvokeMethod, $null, $Database, @($query))
        [void]$view.GetType().InvokeMember('Execute', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
        $record = $view.GetType().InvokeMember('Fetch', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
        if ($null -eq $record) { throw "Missing MSI property: $Name" }
        return $record.GetType().InvokeMember('StringData', [Reflection.BindingFlags]::GetProperty, $null, $record, @(1))
    } finally {
        if ($null -ne $record) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($record) }
        if ($null -ne $view) {
            try { [void]$view.GetType().InvokeMember('Close', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null) }
            finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($view) }
        }
    }
}

try {
    $path = (Resolve-Path -LiteralPath $Package).Path
    $installer = New-Object -ComObject WindowsInstaller.Installer
    # Mode 0 and maxProperties 0 open the database and summary read-only; no installation is performed.
    $database = $installer.GetType().InvokeMember('OpenDatabase', [Reflection.BindingFlags]::InvokeMethod, $null, $installer, @($path, 0))
    $summary = $database.GetType().InvokeMember('SummaryInformation', [Reflection.BindingFlags]::GetProperty, $null, $database, @(0))
    $template = $summary.GetType().InvokeMember('Property', [Reflection.BindingFlags]::GetProperty, $null, $summary, @(7))
    [ordered]@{
        ProductName = Get-MsiProperty $database 'ProductName'
        ProductVersion = Get-MsiProperty $database 'ProductVersion'
        Template = $template
    } | ConvertTo-Json -Compress
} finally {
    foreach ($item in @($summary, $database, $installer)) {
        if ($null -ne $item) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($item) }
    }
}
