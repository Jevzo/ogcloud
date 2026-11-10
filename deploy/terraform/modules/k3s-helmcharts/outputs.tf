output "documents" {
  description = "Every manifest to apply at once, by file name; sensitive, since they hold the seed and OgCloud's values."
  sensitive   = true

  value = merge(
    { "namespace.yaml" = yamlencode(local.namespace) },
    { for name, manifest in data.http.manifest : "manifest-${name}.yaml" => manifest.response_body },
    { for name, chart in local.platform_charts : "helmchart-${name}.yaml" => yamlencode(chart) },
    { "ogcloud-values.yaml" = yamlencode(local.ogcloud_values), "helmchart-ogcloud.yaml" = yamlencode(local.ogcloud) },
    length(var.seed) > 0 ? { "ogcloud-seed.yaml" = yamlencode(local.seed) } : {},
  )

  precondition {
    condition = alltrue([
      for entry in concat(local.deps.charts, local.deps.manifests) : contains(["monitoring"], try(entry.when, "monitoring"))
    ])
    error_message = "platform-deps.yaml knows only `when: monitoring`."
  }

  precondition {
    condition     = contains(local.deps.targets, var.target)
    error_message = "target ${var.target} is not one of platform-deps.yaml's targets: ${join(", ", local.deps.targets)}."
  }
}
