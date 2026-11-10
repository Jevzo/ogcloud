# The k3s HelmCharts (D-0156) for k3s clusters Terraform does not provision: the k3d ordering test (T-0605) and the
# test server (D-0157). It creates nothing; `terraform output -raw manifest` prints what to apply at once.

variable "target" {
  description = "The install target in platform-deps.yaml."
  type        = string
}

variable "monitoring" {
  description = "Whether the monitoring stack is installed (D-0025)."
  type        = bool
  default     = false
}

variable "chart_archive" {
  description = "OgCloud's chart as `helm package` writes it."
  type        = string
}

variable "values_files" {
  description = "OgCloud's values files, later ones taking precedence."
  type        = list(string)
  default     = []
}

variable "seed_files" {
  description = "YAML maps of bootstrap's seed keys (D-0188), later ones taking precedence; a file from before D-0188 holds them under bootstrap.seed."
  type        = list(string)
  default     = []
}

locals {
  # An N-1 checkout's seed file still has the values file's shape (the upgrade test, D-0172).
  seed = merge([for path in var.seed_files : try(yamldecode(file(path)).bootstrap.seed, yamldecode(file(path)))]...)
}

module "helmcharts" {
  source = "../modules/k3s-helmcharts"

  target         = var.target
  monitoring     = var.monitoring
  ogcloud_chart  = { content = filebase64(var.chart_archive) }
  ogcloud_values = [for path in var.values_files : file(path)]
  seed           = local.seed
}

output "manifest" {
  description = "Every document in one stream; it holds the seed."
  sensitive   = true
  value       = join("\n---\n", values(module.helmcharts.documents))
}
