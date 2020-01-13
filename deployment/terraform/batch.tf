#
# Batch resources
#
# Pull the image ID for the latest Amazon ECS GPU-optimized AMI
# https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-optimized_AMI.html#w520aac22c15c31b5
data "aws_ssm_parameter" batch_gpu_container_instance_image_id {
  name = "/aws/service/ecs/optimized-ami/amazon-linux-2/gpu/recommended/image_id"
}

resource "aws_launch_template" "batch_gpu_container_instance" {
  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size = var.batch_gpu_container_instance_volume_size
      volume_type = "gp2"
    }
  }

  image_id = data.aws_ssm_parameter.batch_gpu_container_instance_image_id.value
}

resource "aws_batch_compute_environment" "gpu" {
  depends_on = [aws_iam_role_policy_attachment.batch_policy]

  compute_environment_name_prefix = "batch${var.project}GPUComputeEnvironment"
  type                            = "MANAGED"
  state                           = "ENABLED"
  service_role                    = aws_iam_role.container_instance_batch.arn

  compute_resources {
    type           = "SPOT"
    bid_percentage = var.batch_gpu_ce_spot_fleet_bid_precentage
    ec2_key_pair   = var.aws_key_name

    min_vcpus     = var.batch_gpu_ce_min_vcpus
    desired_vcpus = var.batch_gpu_ce_desired_vcpus
    max_vcpus     = var.batch_gpu_ce_max_vcpus

    spot_iam_fleet_role = aws_iam_role.container_instance_spot_fleet.arn
    instance_role       = aws_iam_instance_profile.container_instance.arn

    instance_type = var.batch_gpu_ce_instance_types

    launch_template {
      launch_template_id = aws_launch_template.batch_gpu_container_instance.id
      version            = aws_launch_template.batch_gpu_container_instance.latest_version
    }

    security_group_ids = [
      aws_security_group.api.id
    ]

    subnets = var.vpc_private_subnet_ids

    tags = {
      Name               = "BatchWorker"
      ComputeEnvironment = "GPU"
      Project            = var.project
      Environment        = var.environment
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_batch_job_queue" "gpu" {
  name                 = "queue${var.project}GPU"
  priority             = 1
  state                = "ENABLED"
  compute_environments = [aws_batch_compute_environment.gpu.arn]
}

resource "aws_batch_job_definition" "test_gpu" {
  name = "job${var.project}TestGPU"
  type = "container"

  container_properties = templatefile("${path.module}/job-definitions/test-gpu.json.tmpl", {})
}
