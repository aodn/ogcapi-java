output "buckets" {
  value = module.s3.wrapper
}

output "ecs" {
  value = module.ecs
}
