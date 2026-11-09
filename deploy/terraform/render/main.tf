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

module "helmcharts" {
  source = "../modules/k3s-helmcharts"

  target         = var.target
  monitoring     = var.monitoring
  ogcloud_chart  = { content = filebase64(var.chart_archive) }
  ogcloud_values = [for path in var.values_files : file(path)]
}

output "manifest" {
  description = "Every document in one stream; it holds the seed."
  sensitive   = true
  value       = join("\n---\n", values(module.helmcharts.documents))
}
