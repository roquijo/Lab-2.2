data "aws_caller_identity" "current" {}

resource "aws_iam_role" "execution" {
  name = "lab22-ecs-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_ssm" {
  name = "lab22-read-db-password"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ssm:GetParameters"]
      Resource = aws_ssm_parameter.db_password.arn
    }]
  })
}

resource "aws_iam_role" "task" {
  name = "lab22-ecs-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "task_telemetry" {
  name = "lab22-telemetry"
  role = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "xray:PutTraceSegments",
          "xray:PutTelemetryRecords",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams",
          "cloudwatch:PutMetricData"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/lab22/db-password"
  type  = "SecureString"
  value = var.db_password
}

resource "aws_cloudwatch_log_group" "service" {
  for_each          = toset(["service-a", "service-b", "collector", "mysql"])
  name              = "/lab22/${each.key}"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "otel_services" {
  name              = "/lab22/services"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "otel_metrics" {
  name              = "/lab22/metrics"
  retention_in_days = 7
}
