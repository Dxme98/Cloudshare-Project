# 1. Der User Pool (Die Datenbank für deine Nutzer)
resource "aws_cognito_user_pool" "main" {
  name = "cloudshare-user-pool"

  # User loggen sich mit Email ein, nicht mit einem separaten Username
  username_attributes = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_symbols   = false
    require_uppercase = true
  }

  # MFA (Multi-Factor Auth) erstmal auslassen für einfachere Entwicklung
  mfa_configuration = "OFF"

  tags = {
    Project = "CloudShare"
  }
}

# 2. Der App Client (Der "Türsteher" für deine React App)
resource "aws_cognito_user_pool_client" "client" {
  name = "cloudshare-react-client"

  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false

  # Erlaubte Auth Flows
  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH", # Ermöglicht Login direkt via Formular
    "ALLOW_REFRESH_TOKEN_AUTH", # Ermöglicht "Angemeldet bleiben"
    "ALLOW_USER_SRP_AUTH"
  ]
}

# 3. Outputs (Diese IDs brauchen wir gleich für React und Spring Boot)
output "cognito_user_pool_id" {
  value = aws_cognito_user_pool.main.id
}

output "cognito_client_id" {
  value = aws_cognito_user_pool_client.client.id
}