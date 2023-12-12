locals {
  bucket_suffix = join("-", [var.environment, data.aws_caller_identity.current.account_id])
}

module "s3" {
  source  = "terraform-aws-modules/s3-bucket/aws//wrappers"
  version = "~> 3.15.1"

  defaults = {
    attach_deny_insecure_transport_policy = true
    server_side_encryption_configuration = {
      rule = {
        bucket_key_enabled = true
        apply_server_side_encryption_by_default = {
          sse_algorithm = "AES256"
        }
      }
    }
  }

  items = {
    "sample-django-app-${local.bucket_suffix}" = {
      bucket                   = "sample-django-app-${local.bucket_suffix}"
      acl                      = "public-read"
      block_public_acls        = false
      block_public_policy      = false
      ignore_public_acls       = false
      control_object_ownership = true
      object_ownership         = "BucketOwnerPreferred"
      restrict_public_buckets  = false
    }
  }
}
