package gateway

import (
	"context"
	"errors"
	"fmt"
	"time"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/informers"
	"k8s.io/client-go/kubernetes"
	listerscorev1 "k8s.io/client-go/listers/core/v1"
	"k8s.io/client-go/tools/cache"

	"github.com/jevzo/ogcloud/data-plane/pkg/podlabels"
)

// PodCache watches the game pods the controller manages, so binding a token costs no API call.
type PodCache struct {
	lister listerscorev1.PodNamespaceLister
}

// syncTimeout bounds the first list, e.g. when the gateway may not list pods.
const syncTimeout = time.Minute

// StartPodCache watches namespace until ctx ends and returns once the first list is in.
func StartPodCache(ctx context.Context, client kubernetes.Interface, namespace string) (*PodCache, error) {
	selector := podlabels.ManagedBy + "=" + podlabels.ControllerName
	factory := informers.NewSharedInformerFactoryWithOptions(client, 0,
		informers.WithNamespace(namespace),
		informers.WithTweakListOptions(func(options *metav1.ListOptions) { options.LabelSelector = selector }),
	)
	pods := factory.Core().V1().Pods()
	lister := pods.Lister().Pods(namespace)
	informer := pods.Informer()
	factory.Start(ctx.Done())
	syncCtx, cancel := context.WithTimeout(ctx, syncTimeout)
	defer cancel()
	if !cache.WaitForCacheSync(syncCtx.Done(), informer.HasSynced) {
		return nil, errors.Join(fmt.Errorf("pod cache for %s did not sync; can the gateway list pods there?", namespace), syncCtx.Err())
	}
	return &PodCache{lister: lister}, nil
}

// Pod returns the pod with name, if the controller manages one.
func (c *PodCache) Pod(name string) (*corev1.Pod, bool) {
	pod, err := c.lister.Get(name)
	return pod, err == nil
}
