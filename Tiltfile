# OgCloud on minikube (D-0022, D-0155): the charts in deploy/platform-deps.yaml in their order, OgCloud's images
# built into minikube's Docker, then OgCloud's chart without waits, so it converges as on k3s (D-0159, D-0150).
# Start it with `mise run dev`; `mise run dev:ci` is the check.

allow_k8s_contexts('ogcloud')
update_settings(max_parallel_updates=2, k8s_upsert_timeout_secs=600)

TARGET = 'minikube'
MONITORING = os.getenv('OGCLOUD_MONITORING', '') == 'true'
DEV = 'build/dev'

def merge(base, extra):
    merged = dict(base)
    for key, value in extra.items():
        if type(value) == 'dict' and type(merged.get(key)) == 'dict':
            merged[key] = merge(merged[key], value)
        else:
            merged[key] = value
    return merged

def write(path, content):
    local('mkdir -p "$(dirname %s)" && umask 077 && cat > %s' % (path, path), stdin=content, quiet=True, echo_off=True)

# Infisical's first super-admin (D-0114), made up once and kept out of git. It reaches the cluster as the seed Secret,
# never as a chart value (D-0188), and bootstrap deletes that after the import.
SEED = DEV + '/seed.yaml'
SEED_SECRET = DEV + '/seed-secret.yaml'
if not os.path.exists(SEED):
    local('mkdir -p %s && umask 077 && printf "INFISICAL_ADMIN_EMAIL: admin@ogcloud.localhost\\nINFISICAL_ADMIN_PASSWORD: %%s\\n" "$(openssl rand -hex 24)" > %s' % (DEV, SEED), quiet=True, echo_off=True)
seed = read_yaml(SEED)
# A seed file from before D-0188 held a values file.
if 'bootstrap' in seed:
    seed = seed['bootstrap']['seed']
write(SEED_SECRET, encode_yaml({
    'apiVersion': 'v1',
    'kind': 'Secret',
    'metadata': {'name': 'ogcloud-seed', 'namespace': 'ogcloud'},
    'stringData': seed,
}))

# Strimzi's operator binds roles in the namespace it watches, so that one exists first.
local_resource('namespace', cmd='kubectl apply -f deploy/namespace.yaml', labels=['platform'])

deps = read_yaml('deploy/platform-deps.yaml')
if TARGET not in deps['targets']:
    fail('platform-deps.yaml names no install target ' + TARGET)
previous = ['namespace']
for manifest in deps['manifests']:
    if manifest.get('when') == 'monitoring' and not MONITORING:
        continue
    path = '%s/manifests/%s.yaml' % (DEV, manifest['name'])
    local_resource(
        manifest['name'],
        cmd='mkdir -p "$(dirname {p})" && curl -fsSL {url} -o {p} && echo "{sha}  {p}" | shasum -a 256 -c - && kubectl apply --server-side -f {p}'.format(
            p=path, url=manifest['url'], sha=manifest['sha256']),
        resource_deps=previous,
        labels=['platform'],
    )
    previous = [manifest['name']]

for chart in deps['charts']:
    if chart.get('when') == 'monitoring' and not MONITORING:
        continue
    values = merge(chart.get('values', {}), chart.get('targets', {}).get(TARGET, {}))
    path = '%s/values/%s.yaml' % (DEV, chart['name'])
    write(path, encode_yaml(values))
    # The operators wait for their webhooks, so the next chart's resources are admitted.
    local_resource(
        chart['name'],
        cmd='helm upgrade --install {name} {chart} --repo {repo} --version {version} --namespace {ns} --create-namespace --values {values} --wait --timeout 10m'.format(
            name=chart['name'], chart=chart['chart'], repo=chart['repository'], version=chart['version'],
            ns=chart['namespace'], values=path),
        resource_deps=previous,
        labels=['platform'],
    )
    previous = [chart['name']]

# Every image `mise run images:build` makes, straight into minikube's Docker; rerun it from the UI after changes.
local_resource(
    'images',
    cmd='eval "$(minikube -p ogcloud docker-env)" && REGISTRY=ogcloud-local VERSION=dev mise run images:build',
    labels=['ogcloud'],
)

# The seed's hash as a value, so a changed seed upgrades the release and reaches bootstrap (D-0188).
chart_values = '--values deploy/helm/ogcloud/ci/minikube.yaml --values deploy/dev/values.yaml --set bootstrap.seedChecksum=$(shasum -a 256 %s | cut -d" " -f1)' % SEED_SECRET
if MONITORING:
    chart_values += ' --set monitoring.enabled=true'
k8s_custom_deploy(
    'ogcloud',
    # Helm waits for the bootstrap hooks even without --wait; a first install pulls images and migrates Infisical.
    # Tilt tracks what apply_cmd prints, with UIDs, so the release's objects are read back from the cluster.
    apply_cmd='kubectl apply -f %s >&2 && helm upgrade --install ogcloud deploy/helm/ogcloud --namespace ogcloud --timeout 30m %s >&2 && helm get manifest ogcloud --namespace ogcloud | kubectl get -f - -o yaml' % (SEED_SECRET, chart_values),
    delete_cmd='helm uninstall ogcloud --namespace ogcloud',
    deps=['deploy/helm/ogcloud', 'deploy/dev'],
)
k8s_resource('ogcloud', resource_deps=previous + ['images'], labels=['ogcloud'])

# The public origin, the Minecraft edge and Infisical's UI (its admin is in build/dev/seed.yaml).
for name, forward, link in [
    ('https', 'svc/traefik 8443:443 --namespace traefik', 'https://ogcloud.localhost:8443/.well-known/openid-configuration'),
    ('minecraft', 'svc/ogcloud-loadbalancer 25565:25565 --namespace ogcloud', None),
    ('infisical-ui', 'svc/ogcloud-infisical 8444:8443 --namespace ogcloud', 'https://localhost:8444'),
]:
    local_resource(
        name,
        serve_cmd='while true; do kubectl port-forward %s; sleep 2; done' % forward,
        resource_deps=['ogcloud'],
        links=[link] if link else [],
        labels=['access'],
    )
