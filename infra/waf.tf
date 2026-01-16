/**
resource "aws_wafv2_web_acl" "main" {
  name        = "cloudshare-waf"
  description = "Rate Limiting für CloudShare"
  scope       = "REGIONAL" # FÜR ALB! (habe kein cloudfront)

  default_action {
    allow {} # Alles erlauben, was nicht explizit verboten ist
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "CloudShareWAF"
    sampled_requests_enabled   = true
  }

  # Regel: Rate Limiting
  rule {
    name     = "RateLimit100"
    priority = 1

    action {
      block {} # Wenn Limit erreicht -> Blockieren (403 Forbidden)
    }

    statement {
      rate_based_statement {
        limit              = 100 # Max 100 Requests pro 5 Minuten pro IP
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitMetric"
      sampled_requests_enabled   = true
    }
  }
}

resource "aws_wafv2_web_acl_association" "alb_assoc" {
  resource_arn = aws_lb.main.arn
  web_acl_arn  = aws_wafv2_web_acl.main.arn
}
 */