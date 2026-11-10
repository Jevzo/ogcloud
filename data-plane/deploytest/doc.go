// Package deploytest checks the OgCloud Helm chart as each install target renders it: invariants every object
// must keep, and the wiring between the chart and the services (AGENTS.md §11, D-0148); and the deploy scripts that
// run without a cluster. It needs helm on the PATH, which mise provides.
package deploytest
