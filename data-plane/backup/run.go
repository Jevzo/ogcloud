package backup

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/jevzo/ogcloud/data-plane/pkg/templateobjects"
)

// markPrefix holds, in the target, one empty object per copy whose source is gone; its time starts the copy's
// retention (D-0186).
const markPrefix = "templates-gone/"

// Result counts what one run did.
type Result struct {
	Copied int
	Marked int
	Pruned int
}

// BackUp copies new template archives from data into target, marks copies whose source went and prunes those
// marked longer than retention ago.
func BackUp(ctx context.Context, data, target *Bucket, now time.Time, retention time.Duration) (Result, error) {
	source, copies, err := listBoth(ctx, data, target)
	if err != nil {
		return Result{}, err
	}
	marks, err := listMarks(ctx, target)
	if err != nil {
		return Result{}, err
	}
	plan, err := PlanBackup(source, copies, marks, now, retention)
	if err != nil {
		return Result{}, err
	}
	var result Result
	if err := copyAll(ctx, plan.Copy, data, target); err != nil {
		return result, err
	}
	result.Copied = len(plan.Copy)
	if err := target.putEmpty(ctx, markKeys(plan.Mark)); err != nil {
		return result, err
	}
	result.Marked = len(plan.Mark)
	// The copy goes before its mark, so a failed run leaves the mark to prune the copy next time.
	if err := target.delete(ctx, plan.Prune); err != nil {
		return result, err
	}
	result.Pruned = len(plan.Prune)
	return result, target.delete(ctx, markKeys(append(plan.Prune, plan.Unmark...)))
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

// listMarks returns the marks keyed by the copy they mark.
func listMarks(ctx context.Context, target *Bucket) ([]Object, error) {
	marks, err := target.List(ctx, markPrefix+templateobjects.Prefix)
	if err != nil {
		return nil, err
	}
	for index := range marks {
		marks[index].Key = strings.TrimPrefix(marks[index].Key, markPrefix)
	}
	return marks, nil
}

func markKeys(keys []string) []string {
	marks := make([]string, 0, len(keys))
	for _, key := range keys {
		marks = append(marks, markPrefix+key)
	}
	return marks
}

func copyAll(ctx context.Context, keys []string, from, to *Bucket) error {
	for index, key := range keys {
		if err := from.copyTo(ctx, key, to); err != nil {
			return fmt.Errorf("after %d of %d archives: %w", index, len(keys), err)
		}
	}
	return nil
}
