data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "lab22" {
  cidr_block           = "10.22.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "lab22" }
}

resource "aws_internet_gateway" "lab22" {
  vpc_id = aws_vpc.lab22.id
  tags   = { Name = "lab22" }
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.lab22.id
  cidr_block              = cidrsubnet(aws_vpc.lab22.cidr_block, 8, count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
  tags                    = { Name = "lab22-public-${count.index}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.lab22.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.lab22.id
  }
  tags = { Name = "lab22-public" }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "alb" {
  name   = "lab22-alb"
  vpc_id = aws_vpc.lab22.id
  ingress {
    from_port   = 80
    to_port     = 80
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

resource "aws_security_group" "tasks" {
  name   = "lab22-tasks"
  vpc_id = aws_vpc.lab22.id
  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "rds" {
  count  = var.create_rds ? 1 : 0
  name   = "lab22-rds"
  vpc_id = aws_vpc.lab22.id
  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.tasks.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_subnet_group" "lab22" {
  count      = var.create_rds ? 1 : 0
  name       = "lab22"
  subnet_ids = aws_subnet.public[*].id
}

resource "aws_db_instance" "lab22" {
  count                  = var.create_rds ? 1 : 0
  identifier             = "lab22"
  engine                 = "mysql"
  engine_version         = "8.4"
  instance_class         = "db.t4g.micro"
  allocated_storage      = 20
  db_name                = "lab22"
  username               = "lab"
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.lab22[0].name
  vpc_security_group_ids = [aws_security_group.rds[0].id]
  publicly_accessible    = false
  skip_final_snapshot    = true
  deletion_protection    = false
}
