terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    bucket         = "tf-state-cloudshare-project-5598" # Dein Bucket aus Schritt 0
    key            = "dev/terraform.tfstate"            # Der Pfad INNERHALB des Buckets (wie ein Ordner)
    region         = "eu-central-1"
    dynamodb_table = "state-lock" # Deine Tabelle für das Locking
    encrypt        = true         # Der State wird verschlüsselt abgelegt
  }
}

# 3. Der Provider selbst
provider "aws" {
  region = "eu-central-1"
}
