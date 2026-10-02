variable "aws_region" {
  description = "AWS deployment region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment name"
  type        = string
  default     = "production"
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "db_instance_class" {
  description = "RDS PostgreSQL Instance Class"
  type        = string
  default     = "db.r6g.large"
}

variable "redis_node_type" {
  description = "ElastiCache Redis node type"
  type        = string
  default     = "cache.r6g.large"
}

variable "app_image" {
  description = "Docker image URI in Amazon ECR"
  type        = string
  default     = "123456789012.dkr.ecr.us-east-1.amazonaws.com/scalecart-backend:latest"
}

variable "container_port" {
  description = "Port exposed by the Spring Boot container"
  type        = number
  default     = 8080
}
