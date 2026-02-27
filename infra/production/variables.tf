variable "aws_region" {
  description = "Die AWS Region für das Deployment"
  type        = string
  default     = "eu-central-1"
}


variable "image_tag" {
  description = "Welches Image-Tag soll deployed werden?"
  type        = string
  default     = "latest"
}