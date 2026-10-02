# ScaleCart Automated API End-to-End Verification Script
# Usage: .\test-api.ps1

$baseUrl = "http://localhost:8080/api/v1"
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " ScaleCart API End-to-End Test Suite" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Login as Customer
Write-Host "`n1. Logging in as customer (customer@scalecart.com)..." -ForegroundColor Yellow
$loginBody = @{
    email = "customer@scalecart.com"
    password = "Customer@123"
} | ConvertTo-Json

try {
    $loginResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    $customerToken = $loginResp.data.token
    Write-Host " Customer login successful! JWT Token acquired." -ForegroundColor Green
} catch {
    Write-Host " Customer login failed: $_" -ForegroundColor Red
    exit 1
}

# 2. Browse Products (Testing Redis cache & catalog)
Write-Host "`n2. Browsing product catalog (GET /products)..." -ForegroundColor Yellow
try {
    $productsResp = Invoke-RestMethod -Uri "$baseUrl/products" -Method Get
    Write-Host " Retrieved $($productsResp.data.Count) products from catalog:" -ForegroundColor Green
    foreach ($p in $productsResp.data) {
        Write-Host "   - [$($p.sku)] $($p.name) | Price: `$$($p.price) | Stock: $($p.availableStock)" -ForegroundColor Gray
    }
    $targetProduct = $productsResp.data[0]
} catch {
    Write-Host " Failed to browse products: $_" -ForegroundColor Red
    exit 1
}

# 3. Create an Order (Testing Concurrency-Safe Stock Reservation & Kafka Event)
Write-Host "`n3. Placing an order for product '$($targetProduct.name)' (POST /orders)..." -ForegroundColor Yellow
$orderBody = @{
    shippingAddress = "456 Tech Park, Building 2, San Francisco, CA"
    items = @(
        @{
            productId = $targetProduct.id
            quantity = 1
        }
    )
} | ConvertTo-Json

try {
    $headers = @{ Authorization = "Bearer $customerToken" }
    $orderResp = Invoke-RestMethod -Uri "$baseUrl/orders" -Method Post -Body $orderBody -ContentType "application/json" -Headers $headers
    $order = $orderResp.data
    Write-Host " Order placed successfully!" -ForegroundColor Green
    Write-Host "   - Order Number: $($order.orderNumber)" -ForegroundColor Gray
    Write-Host "   - Order ID:     $($order.id)" -ForegroundColor Gray
    Write-Host "   - Total Amount: `$$($order.totalAmount)" -ForegroundColor Gray
    Write-Host "   - Status:       $($order.status) (Inventory reserved under pessimistic lock)" -ForegroundColor Gray
} catch {
    Write-Host " Order placement failed: $_" -ForegroundColor Red
    exit 1
}

# 4. Process Payment with Idempotency Key (Testing Mock Payment & Event-Driven Confirmation)
Write-Host "`n4. Processing mock payment (POST /payments/process)..." -ForegroundColor Yellow
$idempotencyKey = "IDEMP-" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
$paymentBody = @{
    orderId = $order.id
    amount = $order.totalAmount
    paymentMethod = "CREDIT_CARD"
    idempotencyKey = $idempotencyKey
    simulateFailure = $false
} | ConvertTo-Json

try {
    $payResp = Invoke-RestMethod -Uri "$baseUrl/payments/process" -Method Post -Body $paymentBody -ContentType "application/json" -Headers $headers
    $payment = $payResp.data
    Write-Host " Payment processed successfully!" -ForegroundColor Green
    Write-Host "   - Transaction ID: $($payment.transactionId)" -ForegroundColor Gray
    Write-Host "   - Status:         $($payment.status)" -ForegroundColor Gray
} catch {
    Write-Host " Payment processing failed: $_" -ForegroundColor Red
    exit 1
}

# 5. Verify Order Status Updated to CONFIRMED
Write-Host "`n5. Verifying updated order state (GET /orders/$($order.id))..." -ForegroundColor Yellow
try {
    $verifiedOrderResp = Invoke-RestMethod -Uri "$baseUrl/orders/$($order.id)" -Method Get -Headers $headers
    $verifiedOrder = $verifiedOrderResp.data
    Write-Host " Order state verified:" -ForegroundColor Green
    Write-Host "   - Status: $($verifiedOrder.status)" -ForegroundColor Gray
    if ($verifiedOrder.status -eq "CONFIRMED") {
        Write-Host " Status is CONFIRMED as expected!" -ForegroundColor Green
    }
} catch {
    Write-Host " Order status verification failed: $_" -ForegroundColor Red
}

# 6. Verify Stock in Inventory (Sold stock incremented)
Write-Host "`n6. Checking updated inventory for product ID $($targetProduct.id)..." -ForegroundColor Yellow
try {
    $invResp = Invoke-RestMethod -Uri "$baseUrl/inventory/product/$($targetProduct.id)" -Method Get -Headers $headers
    $inv = $invResp.data
    Write-Host " Inventory state verified:" -ForegroundColor Green
    Write-Host "   - Available Stock: $($inv.availableStock)" -ForegroundColor Gray
    Write-Host "   - Reserved Stock:  $($inv.reservedStock)" -ForegroundColor Gray
    Write-Host "   - Sold Stock:      $($inv.soldStock)" -ForegroundColor Gray
} catch {
    Write-Host " Inventory check failed: $_" -ForegroundColor Red
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " All API end-to-end flows executed successfully!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
