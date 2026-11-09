package backup

import (
	"context"
	"fmt"
	"time"

	"github.com/jevzo/ogcloud/data-plane/pkg/templateobjects"
)

// Result counts what one run did.
type Result struct {
	Copied int
	Pruned int
}

// BackUp copies new template archives from data into target and prunes old copies of deleted ones.
func BackUp(ctx context.Context, data, target *Bucket, now time.Time, retention time.Duration) (Result, error) {
	source, copies, err := listBoth(ctx, data, target)
	if err != nil {
		return Result{}, err
	}
	plan := PlanBackup(source, copies, now, retention)
	if err := copyAll(ctx, plan.Copy, data, target); err != nil {
		return Result{}, err
	}
	if err := target.delete(ctx, plan.Prune); err != nil {
		return Result{Copied: len(plan.Copy)}, err
	}
	return Result{Copied: len(plan.Copy), Pruned: len(plan.Prune)}, nil
}

// Restore copies the archives data lacks back from target.
func Restore(ctx context.Context, target, data *Bucket) (Result, error) {
	copies, present, err := listBoth(ctx, target, data)
	if err != nil {
		return Result{}, err
	}
	plan := PlanRestore(copies, present)
	if err := copyAll(ctx, plan.Copy, target, data); err != nil {
		return Result{}, err
	}
	return Result{Copied: len(plan.Copy)}, nil
}

func listBoth(ctx context.Context, from, to *Bucket) ([]Object, []Object, error) {
	source, err := from.List(ctx, templateobjects.Prefix)
	if err != nil {
		return nil, nil, err
	}
	target, err := to.List(ctx, templateobjects.Prefix)
	if err != nil {
		return nil, nil, err
	}
	return source, target, nil
}

func copyAll(ctx context.Context, keys []string, from, to *Bucket) error {
	for index, key := range keys {
		if err := from.copyTo(ctx, key, to); err != nil {
			return fmt.Errorf("after %d of %d archives: %w", index, len(keys), err)
		}
	}
	return nil
}
