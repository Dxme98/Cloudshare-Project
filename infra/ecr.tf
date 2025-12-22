resource "aws_ecr_repository" "cloudshare_repository" {
  name                 = "cloudshare-repository"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  force_delete = true
}

resource "aws_ecr_lifecycle_policy" "cleanup_policy" {
  repository = aws_ecr_repository.cloudshare_repository.id

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Behalte nur die 3 neuesten Images"
        selection = {
          tagStatus   = "any" # Gilt für alle Images (getaggt & ungetaggt)
          countType   = "imageCountMoreThan"
          countNumber = 3
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

output "ecr_repository_url" {
  value = aws_ecr_repository.cloudshare_repository.repository_url
}
