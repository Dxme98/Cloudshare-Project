terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    bucket         = "tf-state-cloudshare-project-5598"
    key            = "dev/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "state-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = "eu-central-1"
}

// Für das CloudFront-Zertifikat (USA)
provider "aws" {
  alias  = "virginia"
  region = "us-east-1"
}
