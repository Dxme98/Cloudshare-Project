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