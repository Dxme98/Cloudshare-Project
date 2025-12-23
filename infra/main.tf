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
  name           = "file_metadata"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "fileId"

  attribute {
    name = "fileId"
    type = "S"
  }

  tags = {
    Project = "CloudShare"
  }
}