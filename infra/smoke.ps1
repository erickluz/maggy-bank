param([string]$BaseUrl = 'http://localhost')
$ErrorActionPreference = 'Stop'

function Assert($condition, $message) {
    if (-not $condition) { throw $message }
}

function Header($token) { return @{ Authorization = "Bearer $token" } }

function PostJson($url, $body, $headers = @{}) {
    return Invoke-RestMethod -Uri "$BaseUrl$url" -Method Post -ContentType 'application/json' -Headers $headers -Body ($body | ConvertTo-Json -Compress)
}

$suffix = [guid]::NewGuid().ToString('N').Substring(0, 10)
$users = @()
foreach ($name in @('Ana', 'Beto')) {
    $username = "$($name.ToLower())_$suffix"
    $registration = PostJson '/api/identity/register' @{ displayName = $name; username = $username; password = 'password-for-local-smoke' }
    Assert ($registration.username -eq $username) 'Cadastro retornou usuário incorreto.'
    $login = PostJson '/api/identity/login' @{ username = $username; password = 'password-for-local-smoke' }
    Assert ($login.accessToken -and $login.expiresIn -gt 0) 'Login não retornou token.'
    $headers = Header $login.accessToken
    $me = Invoke-RestMethod -Uri "$BaseUrl/api/identity/me" -Headers $headers
    Assert ($me.accountStatus -eq 'READY') "Conta de $name não ficou pronta."
    $account = Invoke-RestMethod -Uri "$BaseUrl/api/finance/account" -Headers $headers
    Assert ($account.balanceCents -eq 100000) "Crédito inicial de $name incorreto."
    $statement = Invoke-RestMethod -Uri "$BaseUrl/api/finance/account/statement" -Headers $headers
    Assert ($statement.Count -eq 1 -and $statement[0].type -eq 'OPENING_CREDIT') "Extrato inicial de $name incorreto."
    $users += @{ name = $name; headers = $headers; account = $account }
}

$sender = $users[0]
$receiver = $users[1]
$recipient = Invoke-RestMethod -Uri "$BaseUrl/api/finance/recipients/$($receiver.account.accountNumber)" -Headers $sender.headers
Assert ($recipient.displayName -eq $receiver.name) 'Titular de destino incorreto.'

$request = @{ requestId = [guid]::NewGuid().ToString(); destinationAccountNumber = $receiver.account.accountNumber; amountCents = 12550 }
$first = PostJson '/api/finance/transfers' $request $sender.headers
$repeat = PostJson '/api/finance/transfers' $request $sender.headers
$lookup = Invoke-RestMethod -Uri "$BaseUrl/api/finance/transfers/$($request.requestId)" -Headers $sender.headers
Assert ($first.transferId -eq $repeat.transferId -and $first.transferId -eq $lookup.transferId) 'Idempotência ou consulta do comprovante falhou.'

$senderAfter = Invoke-RestMethod -Uri "$BaseUrl/api/finance/account" -Headers $sender.headers
$receiverAfter = Invoke-RestMethod -Uri "$BaseUrl/api/finance/account" -Headers $receiver.headers
Assert ($senderAfter.balanceCents -eq 87450 -and $receiverAfter.balanceCents -eq 112550) 'Saldos após transferência incorretos.'
$senderStatement = Invoke-RestMethod -Uri "$BaseUrl/api/finance/account/statement" -Headers $sender.headers
$receiverStatement = Invoke-RestMethod -Uri "$BaseUrl/api/finance/account/statement" -Headers $receiver.headers
Assert ($senderStatement.Count -eq 2 -and $receiverStatement.Count -eq 2) 'Movimentação ausente no extrato.'
Assert ($senderStatement[0].type -eq 'TRANSFER_OUT' -and $receiverStatement[0].type -eq 'TRANSFER_IN') 'Tipo de movimentação incorreto.'

# Two distinct requests race for a balance that can fund only one of them.
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
try {
    $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $sender.headers.Authorization.Substring(7))
    $tasks = @()
    foreach ($n in 1..2) {
        $body = @{ requestId = [guid]::NewGuid().ToString(); destinationAccountNumber = $receiver.account.accountNumber; amountCents = 70000 } | ConvertTo-Json -Compress
        $content = [System.Net.Http.StringContent]::new($body, [System.Text.Encoding]::UTF8, 'application/json')
        $tasks += $client.PostAsync("$BaseUrl/api/finance/transfers", $content)
    }
    [System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$tasks)
    $statuses = @($tasks | ForEach-Object { [int]$_.Result.StatusCode } | Sort-Object)
    Assert ($statuses[0] -eq 201 -and $statuses[1] -eq 400) "Concorrência produziu respostas inesperadas: $statuses"
} finally { $client.Dispose() }

$senderFinal = Invoke-RestMethod -Uri "$BaseUrl/api/finance/account" -Headers $sender.headers
$receiverFinal = Invoke-RestMethod -Uri "$BaseUrl/api/finance/account" -Headers $receiver.headers
Assert ($senderFinal.balanceCents -eq 17450 -and $receiverFinal.balanceCents -eq 182550) 'Concorrência alterou os saldos de forma incorreta.'

Write-Output "OK: cadastro, login, crédito único, saldo, destinatário, transferência, repetição, concorrência, extratos e comprovante ($suffix)."
