output "alb_dns_name" {
  description = "Public URL / DNS of the Application Load Balancer"
  value       = aws_lb.alb.dns_name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL database endpoint"
  value       = aws_db_instance.postgres.endpoint
}

output "redis_primary_endpoint" {
  description = "ElastiCache Redis primary endpoint"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}
