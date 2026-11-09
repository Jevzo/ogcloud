variable "target" {
  description = "The install target whose `targets.<name>` values platform-deps.yaml adds to each chart, e.g. hetzner."
  type        = string
  nullable    = false
}

variable "monitoring" {
  description = "Whether the charts marked `when: monitoring` are installed (D-0025)."
  type        = bool
  default     = false
  nullable    = false
}

variable "ogcloud_chart" {
  description = "OgCloud's chart: an OCI reference with its version, or a packaged archive in base64 for clusters without a registry."
  type = object({
    chart   = optional(string)
    version = optional(string)
    content = optional(string)
  })
  nullable = false

  validation {
    condition     = (var.ogcloud_chart.content != null) != (var.ogcloud_chart.chart != null && var.ogcloud_chart.version != null)
    error_message = "Set either content, or chart and version."
  }
}

variable "ogcloud_values" {
  description = "Values files for OgCloud's chart, later ones taking precedence; they reach Helm through a Secret, since they hold the seed."
  type        = list(string)
  default     = []
  nullable    = false
}

variable "timeout" {
  description = "Helm's timeout per chart; OgCloud's first install waits for its bootstrap hooks (D-0159)."
  type        = string
  default     = "30m"
  nullable    = false
}

variable "failure_policy" {
  description = "What k3s's helm-controller does after a failed install or upgrade: retry upgrades the failed release at every Job retry and never uninstalls (D-0159)."
  type        = string
  default     = "retry"
  nullable    = false

  validation {
    condition     = contains(["abort", "reinstall", "retry"], var.failure_policy)
    error_message = "failure_policy is abort, reinstall or retry."
  }
}

variable "platform_deps_file" {
  description = "The pinned third-party charts and manifests (D-0021)."
  type        = string
  default     = null
}

variable "namespace_file" {
  description = "OgCloud's release namespace, created before Strimzi binds roles in it."
  type        = string
  default     = null
}
