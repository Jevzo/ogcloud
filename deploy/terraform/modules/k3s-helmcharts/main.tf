# The k3s HelmCharts that install OgCloud and its dependencies (D-0159, D-0156): every one is applied at once, and
# the chart's own ordering makes them converge. k3s's helm-controller runs them from its namespace.

locals {
  deps      = yamldecode(file(coalesce(var.platform_deps_file, "${path.module}/../../../platform-deps.yaml")))
  namespace = yamldecode(file(coalesce(var.namespace_file, "${path.module}/../../../namespace.yaml")))

  helm_namespace = "kube-system"
  values_secret  = "ogcloud-values"

  charts    = [for chart in local.deps.charts : chart if try(chart.when, null) == null || var.monitoring]
  manifests = [for manifest in local.deps.manifests : manifest if try(manifest.when, null) == null || var.monitoring]

  # Each chart's values go in valuesContent and its target's in values, which Helm reads as a second values file and
  # deep-merges on top, as Tilt does for minikube.
  platform_charts = {
    for chart in local.charts : chart.name => {
      apiVersion = "helm.cattle.io/v1"
      kind       = "HelmChart"
      metadata   = { name = chart.name, namespace = local.helm_namespace }
      spec = merge(
        {
          repo            = chart.repository
          chart           = chart.chart
          version         = chart.version
          targetNamespace = chart.namespace
          createNamespace = true
          timeout         = var.timeout
          failurePolicy   = var.failure_policy
        },
        { for key in try(chart.values, null) == null ? [] : ["valuesContent"] : key => yamlencode(chart.values) },
        { for key in try(chart.targets[var.target], null) == null ? [] : ["values"] : key => chart.targets[var.target] },
      )
    }
  }

  value_keys = [for index in range(length(var.ogcloud_values)) : "values-${index}.yaml"]

  ogcloud_values = {
    apiVersion = "v1"
    kind       = "Secret"
    type       = "Opaque"
    metadata   = { name = local.values_secret, namespace = local.helm_namespace }
    data       = zipmap(local.value_keys, [for values in var.ogcloud_values : base64encode(values)])
  }

  ogcloud = {
    apiVersion = "helm.cattle.io/v1"
    kind       = "HelmChart"
    metadata   = { name = "ogcloud", namespace = local.helm_namespace }
    spec = merge(
      {
        targetNamespace = local.namespace.metadata.name
        timeout         = var.timeout
        failurePolicy   = var.failure_policy
        valuesSecrets   = [{ name = local.values_secret, keys = local.value_keys }]
      },
      var.ogcloud_chart.content != null
      ? tomap({ chartContent = var.ogcloud_chart.content })
      : tomap({ chart = var.ogcloud_chart.chart, version = var.ogcloud_chart.version }),
    )
  }
}

data "http" "manifest" {
  for_each = { for manifest in local.manifests : manifest.name => manifest }

  url = each.value.url

  lifecycle {
    postcondition {
      condition     = self.status_code == 200 && sha256(self.response_body) == each.value.sha256
      error_message = "${each.value.url} does not match the sha256 that platform-deps.yaml pins."
    }
  }
}
