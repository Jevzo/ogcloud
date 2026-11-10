mock_provider "http" {}

override_data {
  target = data.http.manifest["crds"]
  values = {
    status_code   = 200
    response_body = "kind: CustomResourceDefinition\n"
  }
}

override_data {
  target = data.http.manifest["dashboard-crds"]
  values = {
    status_code   = 200
    response_body = "kind: CustomResourceDefinition\n"
  }
}

variables {
  target             = "hetzner"
  platform_deps_file = "tests/fixtures/platform-deps.yaml"
  ogcloud_chart      = { chart = "oci://charts.example.test/ogcloud", version = "2.0.0" }
  ogcloud_values     = ["first: 1\n", "second: 2\n"]
}

run "renders_every_document_but_monitoring" {
  command = plan

  assert {
    condition = keys(output.documents) == [
      "helmchart-ogcloud.yaml", "helmchart-plain.yaml", "helmchart-tuned.yaml",
      "manifest-crds.yaml", "namespace.yaml", "ogcloud-values.yaml",
    ]
    error_message = "the documents are not the namespace, the manifest, each chart and OgCloud with its values"
  }

  assert {
    condition     = output.documents["manifest-crds.yaml"] == "kind: CustomResourceDefinition\n"
    error_message = "the manifest is not passed through unchanged"
  }
}

run "installs_monitoring_only_when_asked" {
  command = plan

  variables {
    monitoring = true
  }

  assert {
    condition     = contains(keys(output.documents), "helmchart-dashboards.yaml")
    error_message = "monitoring = true leaves out the monitoring charts"
  }

  assert {
    condition     = contains(keys(output.documents), "manifest-dashboard-crds.yaml")
    error_message = "monitoring = true leaves out the monitoring charts' CRDs"
  }
}

run "installs_each_platform_chart_from_its_pin" {
  command = plan

  assert {
    condition = yamldecode(output.documents["helmchart-plain.yaml"]) == {
      apiVersion = "helm.cattle.io/v1"
      kind       = "HelmChart"
      metadata   = { name = "plain", namespace = "kube-system" }
      spec = {
        repo            = "https://charts.example.test"
        chart           = "plain"
        version         = "1.0.0"
        targetNamespace = "plain-system"
        createNamespace = true
        timeout         = "30m0s"
        failurePolicy   = "retry"
      }
    }
    error_message = "a chart without values renders differently"
  }
}

run "layers_the_targets_values_over_the_charts" {
  command = plan

  assert {
    condition     = yamldecode(yamldecode(output.documents["helmchart-tuned.yaml"]).spec.valuesContent) == { service = { type = "ClusterIP", ports = [80, 443] } }
    error_message = "the chart's values are not its valuesContent"
  }

  assert {
    condition     = yamldecode(output.documents["helmchart-tuned.yaml"]).spec.values == { service = { annotations = { "example.test/private" = "true" } } }
    error_message = "the target's values are not the chart's values"
  }
}

run "leaves_other_targets_values_out" {
  command = plan

  variables {
    target = "minikube"
  }

  assert {
    condition     = !contains(keys(yamldecode(output.documents["helmchart-tuned.yaml"]).spec), "values")
    error_message = "another target's values reached the chart"
  }
}

run "passes_ogcloud_its_values_files_in_order" {
  command = plan

  assert {
    condition     = yamldecode(output.documents["helmchart-ogcloud.yaml"]).spec.valuesSecrets == [{ name = "ogcloud-values", keys = ["values-0.yaml", "values-1.yaml"] }]
    error_message = "OgCloud does not read its values files in order"
  }

  assert {
    condition = { for key, value in yamldecode(output.documents["ogcloud-values.yaml"]).data : key => base64decode(value) } == {
      "values-0.yaml" = "first: 1\n"
      "values-1.yaml" = "second: 2\n"
    }
    error_message = "the values Secret does not hold the values files"
  }
}

run "applies_the_seed_as_a_secret_of_its_own" {
  command = plan

  variables {
    seed = { INFISICAL_ADMIN_EMAIL = "admin@example.test", CLOUDFLARE_API_TOKEN = "token" }
  }

  assert {
    condition = yamldecode(output.documents["ogcloud-seed.yaml"]) == {
      apiVersion = "v1"
      kind       = "Secret"
      type       = "Opaque"
      metadata   = { name = "ogcloud-seed", namespace = "ogcloud" }
      stringData = { INFISICAL_ADMIN_EMAIL = "admin@example.test", CLOUDFLARE_API_TOKEN = "token" }
    }
    error_message = "the seed is not its own Secret in the release namespace (D-0188)"
  }

  assert {
    condition     = yamldecode(base64decode(yamldecode(output.documents["ogcloud-values.yaml"]).data["values-2.yaml"])).bootstrap.seedChecksum == sha256(jsonencode({ INFISICAL_ADMIN_EMAIL = "admin@example.test", CLOUDFLARE_API_TOKEN = "token" }))
    error_message = "a changed seed would not change OgCloud's values, so bootstrap would never import it"
  }

  # Applied in one stream, it must come after the namespace it lives in.
  assert {
    condition     = index(sort(keys(output.documents)), "namespace.yaml") < index(sort(keys(output.documents)), "ogcloud-seed.yaml")
    error_message = "the seed would be applied before its namespace"
  }
}

run "applies_no_seed_without_one" {
  command = plan

  assert {
    condition     = !contains(keys(output.documents), "ogcloud-seed.yaml")
    error_message = "an empty seed Secret was rendered"
  }
}

run "installs_ogcloud_into_its_namespace_from_the_registry" {
  command = plan

  assert {
    condition     = yamldecode(output.documents["namespace.yaml"]).metadata.labels["pod-security.kubernetes.io/enforce"] == "restricted"
    error_message = "the release namespace does not enforce the restricted standard"
  }

  assert {
    condition = { for key, value in yamldecode(output.documents["helmchart-ogcloud.yaml"]).spec : key => value if key != "valuesSecrets" } == {
      targetNamespace = "ogcloud"
      timeout         = "30m0s"
      failurePolicy   = "retry"
      chart           = "oci://charts.example.test/ogcloud"
      version         = "2.0.0"
    }
    error_message = "OgCloud's HelmChart renders differently"
  }
}

run "installs_ogcloud_from_an_archive" {
  command = plan

  variables {
    ogcloud_chart = { content = "Y2hhcnQ=" }
  }

  assert {
    condition     = yamldecode(output.documents["helmchart-ogcloud.yaml"]).spec.chartContent == "Y2hhcnQ=" && !contains(keys(yamldecode(output.documents["helmchart-ogcloud.yaml"]).spec), "chart")
    error_message = "an archive does not replace the chart reference"
  }
}

run "refuses_an_archive_and_a_reference_together" {
  command = plan

  variables {
    ogcloud_chart = { content = "Y2hhcnQ=", chart = "oci://charts.example.test/ogcloud", version = "2.0.0" }
  }

  expect_failures = [var.ogcloud_chart]
}

run "refuses_a_timeout_helm_controller_rewrites" {
  command = plan

  variables {
    timeout = "30m"
  }

  expect_failures = [var.timeout]
}

run "refuses_an_unknown_failure_policy" {
  command = plan

  variables {
    failure_policy = "ignore"
  }

  expect_failures = [var.failure_policy]
}

run "refuses_a_manifest_that_changed" {
  command = plan

  override_data {
    target = data.http.manifest["crds"]
    values = {
      status_code   = 200
      response_body = "kind: Somethingelse\n"
    }
  }

  expect_failures = [data.http.manifest]
}

run "refuses_an_unknown_condition" {
  command = plan

  variables {
    platform_deps_file = "tests/fixtures/unknown-when.yaml"
  }

  expect_failures = [output.documents]
}

run "refuses_an_unknown_condition_on_a_manifest" {
  command = plan

  variables {
    platform_deps_file = "tests/fixtures/unknown-when-manifest.yaml"
  }

  expect_failures = [output.documents]
}

# The repository's own platform-deps.yaml parses; the mocked body never matches its pins.
run "reads_the_repositorys_platform_deps" {
  command = plan

  variables {
    platform_deps_file = null
  }

  expect_failures = [data.http.manifest]
}

run "refuses_a_target_platform_deps_does_not_name" {
  command = plan

  variables {
    target = "hetzer"
  }

  expect_failures = [output.documents]
}
