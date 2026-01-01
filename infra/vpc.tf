resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true # <--- Hinzufügen
  enable_dns_hostnames = true # <--- Hinzufügen (Essenziell für Endpoints!)

  tags = {
    Name = "url-shortener-vpc"
  }
}

resource "aws_subnet" "public_subnet" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.0.0/24"
  availability_zone       = "eu-central-1a"
  map_public_ip_on_launch = true

  tags = {
    Name = "public-subnet"
  }
}

resource "aws_subnet" "public_subnet_b" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "eu-central-1b"
  map_public_ip_on_launch = true

  tags = {
    Name = "public-subnet-b"
  }
}

resource "aws_subnet" "private_subnet" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.2.0/24"
  availability_zone = "eu-central-1a"

  tags = {
    Name = "private-subnet"
  }
}

resource "aws_subnet" "private_subnet_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.3.0/24"
  availability_zone = "eu-central-1b"

  tags = {
    Name = "private-subnet-b"
  }
}

resource "aws_internet_gateway" "internet_gateway" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "internet-gateway"
  }
}

resource "aws_route_table" "public_route_table" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.internet_gateway.id
  }

  tags = {
    Name = "public-route-table"
  }
}

resource "aws_route_table" "private_route_table" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "private-route-table"
  }
}

resource "aws_route_table_association" "public_assoc" {
  route_table_id = aws_route_table.public_route_table.id
  subnet_id      = aws_subnet.public_subnet.id
}

resource "aws_route_table_association" "public_assoc_b" {
  route_table_id = aws_route_table.public_route_table.id
  subnet_id      = aws_subnet.public_subnet_b.id
}


resource "aws_route_table_association" "private_assoc" {
  route_table_id = aws_route_table.private_route_table.id
  subnet_id      = aws_subnet.private_subnet.id
}

resource "aws_route_table_association" "private_b_assoc" {
  subnet_id      = aws_subnet.private_subnet_b.id
  route_table_id = aws_route_table.private_route_table.id
}


/**  VPC ENDPOINTS */

# --- 1. Security Group für die Endpoints ---
# Sie erlaubt deiner App, die Endpoints auf Port 443 (HTTPS) anzusprechen
resource "aws_security_group" "endpoints_sg" {
  name        = "vpc-endpoints-sg"
  description = "Erlaubt Zugriff von der App auf die VPC Endpoints"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.app_sg.id] # Nur deine App darf anklopfen
  }
}

# --- 2. ECR API Endpoint (Interface) ---
# Verantwortlich für Auth & Metadaten (z.B. "Darf ich pullen?")
resource "aws_vpc_endpoint" "ecr_api" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.eu-central-1.ecr.api"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true

  subnet_ids         = [aws_subnet.private_subnet.id, aws_subnet.private_subnet_b.id]
  security_group_ids = [aws_security_group.endpoints_sg.id]
}

# --- 3. ECR DKR Endpoint (Interface) ---
# Verantwortlich für den eigentlichen Download der Image-Registry
resource "aws_vpc_endpoint" "ecr_dkr" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.eu-central-1.ecr.dkr"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = [aws_subnet.private_subnet.id, aws_subnet.private_subnet_b.id]
  security_group_ids  = [aws_security_group.endpoints_sg.id]
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.eu-central-1.s3"
  vpc_endpoint_type = "Gateway"

  # WICHTIG: Muss mit der Route Table der privaten Subnetze verknüpft sein!
  route_table_ids = [aws_route_table.private_route_table.id]
}

resource "aws_vpc_endpoint" "dynamoDb" {
  vpc_id = aws_vpc.main.id
  service_name = "com.amazonaws.eu-central-1.dynamodb"
  vpc_endpoint_type = "Gateway"

  route_table_ids = [aws_route_table.private_route_table.id]
}

resource "aws_vpc_endpoint" "logs" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.eu-central-1.logs"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true

  subnet_ids         = [aws_subnet.private_subnet.id, aws_subnet.private_subnet_b.id]
  security_group_ids = [aws_security_group.endpoints_sg.id]
}

resource "aws_vpc_endpoint" "cognito_idp" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.eu-central-1.cognito-idp"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true

  subnet_ids          = [aws_subnet.private_subnet.id, aws_subnet.private_subnet_b.id]
  security_group_ids  = [aws_security_group.endpoints_sg.id]
}