// Package backup copies template archives into the backup target and back (D-0166). Archives are immutable
// versions (D-0042), so a key that exists with the same size is already copied.
package backup

import (
	"errors"
	"sort"
	"time"
)

// ErrSourceLost stops a run whose data bucket holds none of the copied archives: an emptied or wrongly configured
// bucket must not prune the copies a restore would need (D-0186).
var ErrSourceLost = errors.New("the data bucket lists none of the copied template archives; nothing was changed")

// Object is one stored archive.
type Object struct {
	Key      string
	Size     int64
	Modified time.Time
}

// Plan is one run: the archives to copy, the copies whose source just went (to mark with the time), the copies to
// delete with their marks, and the marks to drop because their source came back or their copy is gone.
type Plan struct {
	Copy   []string
	Mark   []string
	Prune  []string
	Unmark []string
}

// PlanBackup copies what the target lacks and prunes a copy once its source has been gone for longer than
// retention, timed from the mark the first run without the source left (D-0043, D-0186). marks are keyed by the
// copy they mark.
func PlanBackup(source, target, marks []Object, now time.Time, retention time.Duration) (Plan, error) {
	kept := byKey(source)
	if len(target) > 0 && !anyIn(target, kept) {
		return Plan{}, ErrSourceLost
	}
	plan := Plan{Copy: missing(source, target)}
	copies := byKey(target)
	marked := byKey(marks)
	for _, copied := range target {
		if _, ok := kept[copied.Key]; ok {
			continue
		}
		mark, ok := marked[copied.Key]
		switch {
		case !ok:
			plan.Mark = append(plan.Mark, copied.Key)
		case now.Sub(mark.Modified) > retention:
			plan.Prune = append(plan.Prune, copied.Key)
		}
	}
	for _, mark := range marks {
		_, back := kept[mark.Key]
		_, copied := copies[mark.Key]
		if back || !copied {
			plan.Unmark = append(plan.Unmark, mark.Key)
		}
	}
	sort.Strings(plan.Mark)
	sort.Strings(plan.Prune)
	sort.Strings(plan.Unmark)
	return plan, nil
}

// PlanRestore copies back what the data bucket lacks and deletes nothing.
func PlanRestore(backup, data []Object) Plan {
	return Plan{Copy: missing(backup, data)}
}

func missing(from, to []Object) []string {
	present := byKey(to)
	var keys []string
	for _, object := range from {
		if copied, ok := present[object.Key]; !ok || copied.Size != object.Size {
			keys = append(keys, object.Key)
		}
	}
	sort.Strings(keys)
	return keys
}

func anyIn(objects []Object, index map[string]Object) bool {
	for _, object := range objects {
		if _, ok := index[object.Key]; ok {
			return true
		}
	}
	return false
}

func byKey(objects []Object) map[string]Object {
	index := make(map[string]Object, len(objects))
	for _, object := range objects {
		index[object.Key] = object
	}
	return index
}
