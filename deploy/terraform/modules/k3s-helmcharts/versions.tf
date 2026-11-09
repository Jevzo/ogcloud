terraform {
  required_providers {
    # Fetches the manifests platform-deps.yaml pins by sha256 (D-0158).
    http = {
      source  = "hashicorp/http"
      version = "3.6.3"
    }
  }
}
