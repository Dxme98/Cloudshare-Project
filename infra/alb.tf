resource "aws_lb" "main" {
  name               = "cloudshare-alb"
  internal           = false
  load_balancer_type = "application"


  subnets         = [aws_subnet.public_subnet.id, aws_subnet.public_subnet_b.id]
  security_groups = [aws_security_group.alb_sg.id]

  access_logs {
    bucket  = aws_s3_bucket.alb_logs.id
    enabled = true
    prefix  = "alb" # Optional: Logs landen unter s3://bucket/alb/...
  }

  depends_on = [aws_s3_bucket_policy.allow_alb_logging]

  enable_deletion_protection = false
}

resource "aws_lb_target_group" "cloudshare_tg" {
  name        = "cloudshare-lb-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path = "/hello" # Spring Boot Default
    // path                = "/actuator/health"   ACTUATOR VERWENDEN
    matcher             = "200" # Nur HTTP 200 ist gesund
    interval            = 30    # Alle 30 Sekunden prüfen
    timeout             = 10
    healthy_threshold   = 2 # Nach 2x OK gilt der Container als "Healthy"
    unhealthy_threshold = 3
  }
}


resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  default_action {
    target_group_arn = aws_lb_target_group.cloudshare_tg.arn
    type             = "forward"
  }
}


resource "aws_s3_bucket" "alb_logs" {
  bucket        = "cloudshare-alb-logs-${data.aws_caller_identity.current.account_id}"
  force_destroy = true # Damit terraform destroy den Bucket auch löscht, wenn er Logs enthält
}

resource "aws_s3_bucket_lifecycle_configuration" "alb_logs_lifecycle" {
  bucket = aws_s3_bucket.alb_logs.id

  rule {
    id     = "delete-old-logs"
    status = "Enabled"

    expiration {
      days = 3 # Logs älter als 3 Tage werden gelöscht
    }
  }
}


# Holt deine Account ID für den Bucket-Namen
data "aws_caller_identity" "current" {}

# Holt die offizielle AWS Log-Delivery Account ID für eu-central-1
# Jede Region hat hier eine feste ID (für Frankfurt: 054676820928)
resource "aws_s3_bucket_policy" "allow_alb_logging" {
  bucket = aws_s3_bucket.alb_logs.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AWSLogDeliveryWrite"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::054676820928:root"
        }
        Action   = "s3:PutObject"
        Resource = "${aws_s3_bucket.alb_logs.arn}/*"
      }
    ]
  })
}
output "alb_url" {
  value       = "http://${aws_lb.main.dns_name}"
  description = "Hier klicken um die App zu sehen"
}
