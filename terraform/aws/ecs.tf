locals {
  jdbc_url = var.create_rds ? "jdbc:mysql://${aws_db_instance.lab22[0].address}:3306/lab22?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC" : "jdbc:mysql://127.0.0.1:3306/lab22?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC"
}

resource "aws_ecs_cluster" "lab22" {
  name = "lab22"
}

resource "aws_ecs_task_definition" "lab22" {
  family                   = "lab22"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "1024"
  memory                   = "3072"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn
  container_definitions = templatefile("${path.module}/../../deploy/aws/ecs-task-definition.json.tpl", {
    region             = var.region
    service_a_image    = var.service_a_image
    service_b_image    = var.service_b_image
    collector_image    = var.collector_image
    db_password_arn    = aws_ssm_parameter.db_password.arn
    jdbc_url           = local.jdbc_url
    include_mysql      = var.create_rds ? "false" : "true"
  })
}

resource "aws_lb" "lab22" {
  name               = "lab22"
  load_balancer_type = "application"
  subnets            = aws_subnet.public[*].id
  security_groups    = [aws_security_group.alb.id]
}

resource "aws_lb_target_group" "service_a" {
  name        = "lab22-service-a"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.lab22.id
  target_type = "ip"
  health_check {
    path                = "/actuator/health/liveness"
    matcher             = "200"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.lab22.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service_a.arn
  }
}

resource "aws_ecs_service" "lab22" {
  name            = "lab22"
  cluster         = aws_ecs_cluster.lab22.id
  task_definition = aws_ecs_task_definition.lab22.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.tasks.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.service_a.arn
    container_name   = "service-a"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.http]
}
