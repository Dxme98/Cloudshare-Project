resource "aws_s3_bucket" "uploads" {
  bucket = "cloudshare-file-uploads"
  force_destroy = true
}

resource "aws_s3_object" "uploads_folder" {
  bucket = aws_s3_bucket.uploads.id
  key    = "uploads/"
  content_type = "application/x-directory"
}

resource "aws_s3_bucket_public_access_block" "allow_public" {
  bucket = aws_s3_bucket.uploads.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "file_metadata" {
  name           = "FileMetadata"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "fileId"

  attribute {
    name = "fileId"
    type = "S"
  }

  attribute {
    name = "folderId"
    type = "S"
  }

  global_secondary_index {
    name               = "FolderIndex"
    hash_key           = "folderId"
    projection_type    = "ALL" # Kopiert alle Attribute in den Index für schnellen Zugriff
  }

  tags = {
    Project = "CloudShare"
  }
}

resource "aws_dynamodb_table" "folder_shares" {
  name           = "FolderShare"
  billing_mode   = "PAY_PER_REQUEST"

  hash_key       = "userId"
  range_key      = "folderId"


  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "folderId"
    type = "S"
  }

  global_secondary_index {
    name               = "gsi_folder_lookup"
    hash_key           = "folderId"
    range_key          = "userId"
    projection_type    = "ALL"
  }

  tags = {
    Environment = "dev"
    Project     = "CloudShare"
  }
}


resource "aws_dynamodb_table" "folder" {
  name           = "Folder"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "folderId"

  attribute {
    name = "folderId"
    type = "S"
  }

  attribute {
    name = "userId"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  stream_enabled   = true
  stream_view_type = "OLD_IMAGE"


  global_secondary_index {
    name               = "UserIndex"
    hash_key           = "userId"
    projection_type    = "ALL"
  }

  tags = {
    Project = "CloudShare"
  }
}


resource "aws_iam_role" "lambda_exec_role" {
  name = "cloudshare_lambda_cleanup_role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_policy" "lambda_cleanup_policy" {
  name = "CloudShare_Lambda_Cleanup_Permissions"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:DeleteObject"]
        Resource = ["${aws_s3_bucket.uploads.arn}/uploads/*" ]
      },
      {
        Effect   = "Allow"
        Action   = [
          "dynamodb:Query",
          "dynamodb:DeleteItem",
          "dynamodb:BatchWriteItem"
        ]
        Resource = [
          aws_dynamodb_table.file_metadata.arn,
          "${aws_dynamodb_table.file_metadata.arn}/index/FolderIndex",
          aws_dynamodb_table.folder_shares.arn,
          "${aws_dynamodb_table.folder_shares.arn}/index/gsi_folder_lookup"
        ]
      },
      {
        Effect   = "Allow"
        Action   = [
          "dynamodb:GetRecords",
          "dynamodb:GetShardIterator",
          "dynamodb:DescribeStream",
          "dynamodb:ListStreams",
        ]
        Resource = [aws_dynamodb_table.folder.stream_arn]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_cleanup_attach" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = aws_iam_policy.lambda_cleanup_policy.arn
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "archive_file" "lambda_zip" {
  type        = "zip"
  source_file = "${path.module}/../../lambdas/cleanup/cleanup_lambda.py"
  output_path = "${path.module}/cleanup_lambda.zip"
}

# Die Lambda Funktion selbst
resource "aws_lambda_function" "cleanup_lambda" {
  filename         = data.archive_file.lambda_zip.output_path

  source_code_hash = data.archive_file.lambda_zip.output_base64sha256

  function_name    = "CloudShare_Cleanup_Service"
  role             = aws_iam_role.lambda_exec_role.arn
  handler          = "cleanup_lambda.lambda_handler"
  runtime          = "python3.11"

  environment {
    variables = {
      METADATA_TABLE_NAME = aws_dynamodb_table.file_metadata.name
      S3_BUCKET_NAME      = aws_s3_bucket.uploads.id
      SHARE_TABLE_NAME    = aws_dynamodb_table.folder_shares.name
    }
  }
}

resource "aws_lambda_event_source_mapping" "trigger" {
  event_source_arn  = aws_dynamodb_table.folder.stream_arn
  function_name     = aws_lambda_function.cleanup_lambda.arn
  starting_position = "LATEST"

  filter_criteria {
    filter {
      pattern = jsonencode({
        eventName = ["REMOVE"]
      })
    }
  }
}