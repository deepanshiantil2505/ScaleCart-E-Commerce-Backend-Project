terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "ScaleCart"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# ---------------------------------------------------------------------------------------------------------------------
# Networking: Virtual Private Cloud (VPC) with Multi-AZ Public and Private Subnets
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_vpc" "scalecart_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags = { Name = "scalecart-vpc" }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.scalecart_vpc.id
  tags   = { Name = "scalecart-igw" }
}

# Public Subnets (for ALB)
resource "aws_subnet" "public_1" {
  vpc_id                  = aws_vpc.scalecart_vpc.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true
  tags                    = { Name = "scalecart-public-1" }
}

resource "aws_subnet" "public_2" {
  vpc_id                  = aws_vpc.scalecart_vpc.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = "${var.aws_region}b"
  map_public_ip_on_launch = true
  tags                    = { Name = "scalecart-public-2" }
}

# Private Subnets (for ECS Tasks, RDS, Redis, Kafka)
resource "aws_subnet" "private_app_1" {
  vpc_id            = aws_vpc.scalecart_vpc.id
  cidr_block        = "10.0.10.0/24"
  availability_zone = "${var.aws_region}a"
  tags              = { Name = "scalecart-private-app-1" }
}

resource "aws_subnet" "private_app_2" {
  vpc_id            = aws_vpc.scalecart_vpc.id
  cidr_block        = "10.0.20.0/24"
  availability_zone = "${var.aws_region}b"
  tags              = { Name = "scalecart-private-app-2" }
}

resource "aws_subnet" "private_data_1" {
  vpc_id            = aws_vpc.scalecart_vpc.id
  cidr_block        = "10.0.30.0/24"
  availability_zone = "${var.aws_region}a"
  tags              = { Name = "scalecart-private-data-1" }
}

resource "aws_subnet" "private_data_2" {
  vpc_id            = aws_vpc.scalecart_vpc.id
  cidr_block        = "10.0.40.0/24"
  availability_zone = "${var.aws_region}b"
  tags              = { Name = "scalecart-private-data-2" }
}

# NAT Gateway for outbound traffic from private subnets
resource "aws_eip" "nat" {
  domain = "vpc"
}

resource "aws_nat_gateway" "nat" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public_1.id
  tags          = { Name = "scalecart-nat-gw" }
}

# ---------------------------------------------------------------------------------------------------------------------
# Security Groups: Principle of Least Privilege
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_security_group" "alb_sg" {
  name        = "scalecart-alb-sg"
  description = "Allows incoming HTTP/HTTPS traffic to ALB"
  vpc_id      = aws_vpc.scalecart_vpc.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "ecs_sg" {
  name        = "scalecart-ecs-sg"
  description = "Allows incoming traffic to ECS tasks only from ALB"
  vpc_id      = aws_vpc.scalecart_vpc.id

  ingress {
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb_sg.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db_sg" {
  name        = "scalecart-db-sg"
  description = "Allows PostgreSQL traffic only from ECS tasks"
  vpc_id      = aws_vpc.scalecart_vpc.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_sg.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "redis_sg" {
  name        = "scalecart-redis-sg"
  description = "Allows Redis traffic only from ECS tasks"
  vpc_id      = aws_vpc.scalecart_vpc.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_sg.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ---------------------------------------------------------------------------------------------------------------------
# Relational Database: Amazon RDS PostgreSQL (Multi-AZ)
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_db_subnet_group" "rds" {
  name       = "scalecart-rds-subnet-group"
  subnet_ids = [aws_subnet.private_data_1.id, aws_subnet.private_data_2.id]
}

resource "aws_db_instance" "postgres" {
  identifier             = "scalecart-db"
  allocated_storage      = 50
  max_allocated_storage  = 200
  engine                 = "postgres"
  engine_version         = "16.1"
  instance_class         = var.db_instance_class
  multi_az               = true
  db_name                = "scalecart_db"
  username               = "scalecart_admin"
  manage_master_user_password = true
  db_subnet_group_name   = aws_db_subnet_group.rds.name
  vpc_security_group_ids = [aws_security_group.db_sg.id]
  skip_final_snapshot    = true
}

# ---------------------------------------------------------------------------------------------------------------------
# Cache Layer: Amazon ElastiCache for Redis Cluster
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_elasticache_subnet_group" "redis" {
  name       = "scalecart-redis-subnet-group"
  subnet_ids = [aws_subnet.private_data_1.id, aws_subnet.private_data_2.id]
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "scalecart-redis-cluster"
  description                = "Redis cluster for ScaleCart catalog caching"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 2
  parameter_group_name       = "default.redis7"
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.redis_sg.id]
  automatic_failover_enabled = true
}

# ---------------------------------------------------------------------------------------------------------------------
# Compute: Amazon ECS Fargate Cluster & Application Load Balancer
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_ecs_cluster" "main" {
  name = "scalecart-cluster"
}

resource "aws_lb" "alb" {
  name               = "scalecart-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb_sg.id]
  subnets            = [aws_subnet.public_1.id, aws_subnet.public_2.id]
}

resource "aws_lb_target_group" "tg" {
  name        = "scalecart-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.scalecart_vpc.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.alb.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.tg.arn
  }
}
