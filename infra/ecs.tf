resource "aws_ecs_cluster" "app_cluster" {
  name = "app-cluster"
}

resource "aws_iam_role" "ecs_execution_role" {
  name = "ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = ""
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      },
    ]
  })
}

resource "aws_iam_role" "ecs_task_role" {
  name = "ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = ""
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      },
    ]
  })
}

resource "aws_iam_policy" "s3_access" {
  name = "CloudShareS3Access"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:ListBucket", "s3:DeleteObject"]
        Resource = ["${aws_s3_bucket.uploads.arn}/uploads/*", aws_s3_bucket.uploads.arn]
      }
    ]
  })
}

resource "aws_iam_policy" "dynamodb_access" {
  name        = "CloudShareDynamoDBAccess"
  description = "Erlaubt dem ECS Task Zugriff auf die Metadaten-Tabelle"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = [
          "dynamodb:PutItem",
          "dynamodb:GetItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:Scan"
        ]
        Resource = [aws_dynamodb_table.file_metadata.arn, aws_dynamodb_table.folder.arn,
          "${aws_dynamodb_table.file_metadata.arn}/index/FolderIndex", "${aws_dynamodb_table.folder.arn}/index/*",
          aws_dynamodb_table.folder_shares.arn,
          "${aws_dynamodb_table.folder_shares.arn}/index/*"]
      }
    ]
  })
}

resource "aws_iam_policy" "cognito_access" {
  name        = "CloudShareCognitoAccess"
  description = "Erlaubt dem ECS Task das Suchen von Usern im Cognito User Pool"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = [
          "cognito-idp:ListUsers",
          "cognito-idp:AdminGetUser",
          "cognito-idp:GetUser"
        ]
        Resource = [aws_cognito_user_pool.main.arn]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "attach_cognito" {
  policy_arn = aws_iam_policy.cognito_access.arn
  role       = aws_iam_role.ecs_task_role.name
}

resource "aws_iam_role_policy_attachment" "attach_s3" {
  policy_arn = aws_iam_policy.s3_access.arn
  role = aws_iam_role.ecs_task_role.name
}

resource "aws_iam_role_policy_attachment" "attach_dynamodb" {
  policy_arn = aws_iam_policy.dynamodb_access.arn
  role = aws_iam_role.ecs_task_role.name
}

resource "aws_iam_role_policy_attachment" "execution_role_attachment" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
  role       = aws_iam_role.ecs_execution_role.name
}

resource "aws_cloudwatch_log_group" "ecs_logs" {
  name              = "/ecs/cloudshare"
  retention_in_days = 3 # Spart Kosten: Logs werden nach 7 Tagen gelöscht
}

resource "aws_ecs_task_definition" "cloudshare_task" {
  family                   = "cloudshare-task"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024

  execution_role_arn = aws_iam_role.ecs_execution_role.arn
  task_role_arn      = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([
    {
      name      = "cloudshare-task"
      image     = "${aws_ecr_repository.cloudshare_repository.repository_url}:${var.image_tag}" # Dynamische ECR URL
      essential = true

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = "eu-central-1"
          "awslogs-stream-prefix" = "ecs"
        }
      }

      portMappings = [
        {
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
        }
      ]
      environment = [
        {
          name  = "JAVA_TOOL_OPTIONS"
          value = "-XX:MaxRAMPercentage=75.0"
        },
        {
          name = "S3_UPLOAD_BUCKET_NAME",
          value = aws_s3_bucket.uploads.id
        },
        {
          name = "COGNITO_ISSUER_URI",
          value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
        },
        {
          name = "COGNITO_USERPOOL_ID",
          value = aws_cognito_user_pool.main.id
        },
        {
          name  = "AWS_COGNITO_CLIENT_ID"
          value = aws_cognito_user_pool_client.client.id
        },
        {
          name  = "FRONTEND_URL"
          value = "https://${aws_cloudfront_distribution.frontend_distribution.domain_name}"
        },
        {
          name  = "ALLOWED_ORIGINS"
          value = "http://localhost:5173,https://${aws_cloudfront_distribution.frontend_distribution.domain_name}"
        }
      ]
    }

  ])
}


resource "aws_ecs_service" "cloudshare_service" {
  name            = "cloudshare-service"
  cluster         = aws_ecs_cluster.app_cluster.id
  task_definition = aws_ecs_task_definition.cloudshare_task.id
  launch_type     = "FARGATE"

  desired_count = 1
 // enable_execute_command = var.enable_debugging   Enabled exec (SSM Session Manager)


  load_balancer {
    target_group_arn = aws_lb_target_group.cloudshare_tg.arn
    container_name   = "cloudshare-task"
    container_port   = 8080
  }

  network_configuration {
    security_groups  = [aws_security_group.app_sg.id]
    subnets          = [aws_subnet.private_subnet.id, aws_subnet.private_subnet_b.id]
    assign_public_ip = false
  }



  # Der Lebensretter für Java-Apps:
  # "Wenn ein neuer Task startet, erlaube ihm 120 Sekunden Zeit zum Booten.
  # Erst danach darf der ALB ihn als 'Unhealthy' markieren."
  health_check_grace_period_seconds = 120
  force_delete                      = true // development
  depends_on                        = [aws_lb_listener.http, aws_iam_role_policy_attachment.execution_role_attachment]
}
